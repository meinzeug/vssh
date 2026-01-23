package com.example.vssh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val maxTerminalChars = 20000
    private val recordAudioRequestCode = 501
    private lateinit var sshClient: SshClient
    private lateinit var scrollContainer: NestedScrollView
    private lateinit var terminalOutput: TextView
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button
    private lateinit var agentButton: Button
    private lateinit var settingsButton: Button
    private lateinit var speechController: SpeechInputController
    private var pendingVoiceStart = false
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollContainer = findViewById(R.id.scrollContainer)
        terminalOutput = findViewById(R.id.terminalOutput)
        hostInput = findViewById(R.id.hostInput)
        portInput = findViewById(R.id.portInput)
        userInput = findViewById(R.id.userInput)
        passwordInput = findViewById(R.id.passwordInput)
        commandInput = findViewById(R.id.commandInput)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        sendButton = findViewById(R.id.sendButton)
        voiceButton = findViewById(R.id.voiceButton)
        agentButton = findViewById(R.id.agentButton)
        settingsButton = findViewById(R.id.settingsButton)

        terminalOutput.movementMethod = ScrollingMovementMethod()
        commandInput.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        speechController = AndroidSpeechInputController(this)
        sshClient = SshClient(
            scope = lifecycleScope,
            onOutput = { text -> appendOutput(text) },
            onStatus = { status -> appendOutput("\n[$status]\n") }
        )

        toggleUi(connected = false, inProgress = false)
        SshBridge.setSender(isConnected = false, sendFn = null, execFn = null)
        connectButton.setOnClickListener { connect() }
        disconnectButton.setOnClickListener { disconnect() }
        sendButton.setOnClickListener { sendCommand() }

        voiceButton.setOnClickListener { startVoiceInput() }
        agentButton.setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechController.release()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordAudioRequestCode) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted && pendingVoiceStart) {
                pendingVoiceStart = false
                startVoiceInput()
            } else if (!granted) {
                pendingVoiceStart = false
                appendOutput("\n[Mikrofon-Berechtigung verweigert]\n")
            }
        }
    }

    private fun startVoiceInput() {
        if (!speechController.isAvailable()) {
            appendOutput("\n[STT ist nicht verfügbar]\n")
            return
        }

        if (!hasRecordAudioPermission()) {
            pendingVoiceStart = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                recordAudioRequestCode
            )
            return
        }

        setListening(true)
        speechController.startListening(
            onResult = { text ->
                setListening(false)
                commandInput.setText(text)
                sendCommand()
            },
            onError = { error ->
                setListening(false)
                appendOutput("\n[STT Fehler: $error]\n")
            }
        )
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun connect() {
        val host = hostInput.text.toString().trim()
        val port = portInput.text.toString().trim().ifEmpty { "22" }.toIntOrNull() ?: 22
        val user = userInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (host.isBlank() || user.isBlank()) {
            appendOutput("\n[Bitte Host und User angeben]\n")
            return
        }

        lifecycleScope.launch {
            try {
                toggleUi(connected = false, inProgress = true)
                sshClient.connect(SshConfig(host, port, user, password))
                SshBridge.setSender(
                    isConnected = true,
                    sendFn = { command ->
                        lifecycleScope.launch {
                            try {
                                sshClient.sendCommand(command)
                                appendOutput("\n$command\n")
                            } catch (ex: Exception) {
                                appendOutput("\n[Fehler: ${ex.message}]\n")
                            }
                        }
                    },
                    execFn = { command ->
                        sshClient.execute(command)
                    }
                )
                toggleUi(connected = true, inProgress = false)
            } catch (ex: Exception) {
                appendOutput("\n[Fehler: ${ex.message}]\n")
                SshBridge.setSender(isConnected = false, sendFn = null, execFn = null)
                toggleUi(connected = false, inProgress = false)
            }
        }
    }

    private fun disconnect() {
        lifecycleScope.launch {
            try {
                sshClient.disconnect()
            } catch (ex: Exception) {
                appendOutput("\n[Fehler: ${ex.message}]\n")
            } finally {
                SshBridge.setSender(isConnected = false, sendFn = null, execFn = null)
                toggleUi(connected = false, inProgress = false)
            }
        }
    }

    private fun sendCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isEmpty()) return
        commandInput.setText("")

        lifecycleScope.launch {
            try {
                sshClient.sendCommand(command)
                appendOutput("\n$command\n")
            } catch (ex: Exception) {
                appendOutput("\n[Fehler: ${ex.message}]\n")
            }
        }
    }

    private fun appendOutput(text: String) {
        runOnUiThread {
            terminalOutput.append(text)
            if (terminalOutput.length() > maxTerminalChars) {
                val trimmed = terminalOutput.text.takeLast(maxTerminalChars)
                terminalOutput.text = trimmed
            }
            SshBridge.appendOutput(text)
            scrollContainer.post { scrollContainer.fullScroll(NestedScrollView.FOCUS_DOWN) }
        }
    }

    private fun setListening(active: Boolean) {
        isListening = active
        voiceButton.isEnabled = !active && speechController.isAvailable()
        voiceButton.text = if (active) "..." else "VOICE (STT)"
    }

    private fun toggleUi(connected: Boolean, inProgress: Boolean) {
        connectButton.isEnabled = !connected && !inProgress
        disconnectButton.isEnabled = connected && !inProgress
        sendButton.isEnabled = connected && !inProgress
        voiceButton.isEnabled = connected && !inProgress && speechController.isAvailable() && !isListening
    }
}
