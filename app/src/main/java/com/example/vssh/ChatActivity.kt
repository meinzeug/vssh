package com.example.vssh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

class ChatActivity : AppCompatActivity() {
    private val recordAudioRequestCode = 502
    private lateinit var chatMessages: LinearLayout
    private lateinit var chatInput: EditText
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button
    private lateinit var scrollContainer: NestedScrollView
    private lateinit var includeOutputCheck: CheckBox
    private lateinit var ttsCheck: CheckBox
    private lateinit var toolsCheck: CheckBox
    private lateinit var speechController: SpeechInputController
    private var pendingVoiceStart = false
    private var tts: TextToSpeech? = null

    private val messages = mutableListOf<ChatMessage>()
    private val client = OpenRouterClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatMessages = findViewById(R.id.chatMessages)
        chatInput = findViewById(R.id.chatInput)
        sendButton = findViewById(R.id.chatSendButton)
        voiceButton = findViewById(R.id.chatVoiceButton)
        scrollContainer = findViewById(R.id.chatScrollContainer)
        includeOutputCheck = findViewById(R.id.includeOutputCheck)
        ttsCheck = findViewById(R.id.ttsCheck)
        toolsCheck = findViewById(R.id.toolsCheck)

        chatInput.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        val systemPrompt = """
            Du bist vssh, ein Voice-First Linux-Admin-Assistent für die Server-Konsole.
            Antworte klar, kurz und umsetzbar. Wenn der Nutzer etwas ausführen will, liefere
            die passenden Befehle in einem Code-Block (bash). Erkläre in 1-2 Sätzen, was der
            Befehl macht. Bei riskanten Aktionen: Warnung + Rückfrage.
        """.trimIndent()
        messages.add(ChatMessage("system", systemPrompt))

        speechController = AndroidSpeechInputController(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }

        sendButton.setOnClickListener { sendMessage() }
        voiceButton.setOnClickListener { startVoiceInput() }
    }

    private fun sendMessage() {
        val text = chatInput.text.toString().trim()
        if (text.isEmpty()) return

        chatInput.setText("")
        appendMessageBubble(isUser = true, text = text, command = null)
        messages.add(ChatMessage("user", text))

        val store = SettingsStore(this)
        val apiKey = store.getApiKey()
        if (apiKey.isBlank()) {
            appendMessageBubble(isUser = false, text = "Bitte API Key in OpenRouter Settings setzen.", command = null)
            return
        }

        val model = store.getModel()
        val baseUrl = store.getBaseUrl()

        toggleUi(loading = true)
        lifecycleScope.launch {
            try {
                val toolsEnabled = toolsCheck.isChecked && SshBridge.canExecute()
                val useTools = toolsEnabled && !shouldSkipTools(text)
                if (useTools) {
                    runToolsFlow(baseUrl, apiKey, model, text)
                } else {
                    val requestMessages = messages.toMutableList()
                    if (shouldSkipTools(text)) {
                        requestMessages.add(
                            ChatMessage(
                                "system",
                                "Der Nutzer will einen konkreten Befehl. Antworte mit einem bash Codeblock " +
                                    "und 1-2 kurzen Sätzen Erklärung. Keine Tools."
                            )
                        )
                    }
                    if (includeOutputCheck.isChecked) {
                        val output = SshBridge.getRecentOutput().trim()
                        if (output.isNotEmpty()) {
                            requestMessages.add(ChatMessage("user", "SSH Output (latest):\n$output"))
                        }
                    }
                    val reply = withTimeout(35_000) {
                        client.chat(baseUrl, apiKey, model, requestMessages)
                    }
                    if (reply.isNotBlank()) {
                        messages.add(ChatMessage("assistant", reply))
                        val cmd = extractCommand(reply)
                        appendMessageBubble(isUser = false, text = reply, command = cmd)
                        if (ttsCheck.isChecked) {
                            speak(reply)
                        }
                    } else {
                        appendMessageBubble(isUser = false, text = "Leere Antwort erhalten.", command = null)
                    }
                }
            } catch (ex: Exception) {
                appendMessageBubble(isUser = false, text = "Fehler: ${ex.message}", command = null)
            } finally {
                toggleUi(loading = false)
            }
        }
    }

    private fun shouldSkipTools(text: String): Boolean {
        val lower = text.lowercase()
        return listOf(
            "befehl", "kommando", "command", "syntax", "beispiel", "wie mache", "how to", "how do"
        ).any { lower.contains(it) }
    }

    private fun appendMessageBubble(isUser: Boolean, text: String, command: String?) {
        runOnUiThread {
            val bubble = MessageBubbleView(this)
            bubble.bind(
                isUser = isUser,
                text = text,
                command = command,
                canSend = SshBridge.canSend()
            ) { commandText ->
                sendCommandToSsh(commandText)
            }
            chatMessages.addView(bubble)
            scrollContainer.post { scrollContainer.fullScroll(NestedScrollView.FOCUS_DOWN) }
        }
    }

    private fun toggleUi(loading: Boolean) {
        sendButton.isEnabled = !loading
        chatInput.isEnabled = !loading
        includeOutputCheck.isEnabled = !loading
        ttsCheck.isEnabled = !loading
        voiceButton.isEnabled = !loading
        toolsCheck.isEnabled = !loading
    }

    private fun sendCommandToSsh(command: String) {
        val lines = command.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return
        lines.forEach { SshBridge.sendCommand(it) }
        appendMessageBubble(isUser = true, text = "→ SSH: ${lines.joinToString("; ")}", command = null)
    }

    private fun extractCommand(text: String): String? {
        val codeBlock = Regex("```(?:\\w+)?\\s*([\\s\\S]*?)```")
            .find(text)
            ?.groupValues
            ?.get(1)
        if (!codeBlock.isNullOrBlank()) {
            val lines = codeBlock.trim().lines()
            if (lines.isEmpty()) return null
            val first = lines.first().trim().lowercase()
            val cleaned = if (first in listOf("bash", "sh", "shell")) {
                lines.drop(1)
            } else {
                lines
            }
            return cleaned.joinToString("\n").trim().ifEmpty { null }
        }
        val inline = Regex("`([^`]+)`").find(text)?.groupValues?.get(1)
        if (!inline.isNullOrBlank()) {
            return inline.trim().ifEmpty { null }
        }
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        return when {
            firstLine.startsWith("$ ") -> firstLine.removePrefix("$ ").trim().ifEmpty { null }
            firstLine.startsWith("# ") -> firstLine.removePrefix("# ").trim().ifEmpty { null }
            else -> null
        }
    }

    private suspend fun runToolsFlow(baseUrl: String, apiKey: String, model: String, userText: String) {
        val toolSystem = """
            Du bist ein Linux-Admin-Agent. Antworte strikt als JSON ohne Codeblock:
            {
              "commands": ["..."],
              "final": "kurze Erklärung, was du tust oder was du brauchst"
            }
            Regeln:
            - commands darf leer sein (dann frage nach Details).
            - Nur sichere, lesende Befehle (keine Writes, keine Pipes/&&/;).
            - Erlaubte Befehle: journalctl, last/lastb, who, w, uptime,
              systemctl status/--failed, df -h, free -m, ss -tulpn, netstat -tulpn,
              ps aux, top -b -n 1, sowie cat/tail/head/grep/sed/awk auf /var/log/*.
        """.trimIndent()

        val toolRequest = buildList {
            add(ChatMessage("system", toolSystem))
            // Optional: kurze Konversation für Kontext
            val context = messages
                .filter { it.role != "system" }
                .takeLast(6)
                .joinToString("\n") { "${it.role}: ${it.content.take(400)}" }
            if (context.isNotBlank()) {
                add(ChatMessage("user", "Kontext:\n$context"))
            }
            if (includeOutputCheck.isChecked) {
                val output = SshBridge.getRecentOutput().takeLast(2000).trim()
                if (output.isNotBlank()) {
                    add(ChatMessage("user", "SSH Output (latest):\n$output"))
                }
            }
            add(ChatMessage("user", userText))
        }
        val toolReply = withTimeout(35_000) {
            client.chat(baseUrl, apiKey, model, toolRequest)
        }
        val plan = parseToolPlan(toolReply)

        var commands = plan?.commands ?: emptyList()
        var planNote = plan?.final ?: ""
        if (commands.isEmpty()) {
            val fallback = defaultCommandsFor(userText)
            if (fallback.isNotEmpty()) {
                commands = fallback
                if (planNote.isBlank()) {
                    planNote = "Ich sammle Logs/Status für eine Zusammenfassung."
                }
            } else {
                messages.add(ChatMessage("assistant", toolReply))
                appendMessageBubble(isUser = false, text = toolReply, command = extractCommand(toolReply))
                if (ttsCheck.isChecked) speak(toolReply)
                return
            }
        }

        val safeCommands = SshCommandSafety.filter(commands)
        if (safeCommands.isEmpty()) {
            val note = if (planNote.isNotBlank()) planNote else "Keine sicheren Kommandos gefunden."
            messages.add(ChatMessage("assistant", note))
            appendMessageBubble(isUser = false, text = note, command = null)
            if (ttsCheck.isChecked) speak(note)
            return
        }

        if (planNote.isNotBlank()) {
            appendMessageBubble(isUser = false, text = planNote, command = null)
        }
        appendMessageBubble(isUser = false, text = "Führe ${safeCommands.size} SSH‑Kommandos aus …", command = null)
        val outputs = mutableListOf<Pair<String, String>>()
        for (cmd in safeCommands) {
            appendMessageBubble(isUser = false, text = "→ $cmd", command = null)
            val out = try {
                val result = SshBridge.runCommand(cmd)
                if (result.isBlank()) "[Keine Ausgabe]" else result
            } catch (ex: Exception) {
                "[Fehler] ${ex.message}"
            }
            val clipped = out.take(4000)
            outputs.add(cmd to clipped)
        }

        val outputText = buildString {
            outputs.forEach { (cmd, out) ->
                append("COMMAND: ").append(cmd).append('\n')
                append("OUTPUT:\n").append(out).append("\n\n")
            }
        }

        val finalSystem = """
            Du bist ein Linux-Admin-Assistent. Nutze die Kommando-Ausgaben, um die Nutzerfrage zu beantworten.
            Antworte kurz, klar, mit Stichpunkten. Wenn relevant, nenne konkrete Dateien oder Services.
        """.trimIndent()

        val finalMessages = listOf(
            ChatMessage("system", finalSystem),
            ChatMessage("user", "User question: $userText\n\n$outputText")
        )
        val finalReply = withTimeout(35_000) {
            client.chat(baseUrl, apiKey, model, finalMessages)
        }
        val replyText = if (finalReply.isNotBlank()) finalReply else "Keine Zusammenfassung erhalten."
        messages.add(ChatMessage("assistant", replyText))
        appendMessageBubble(isUser = false, text = replyText, command = extractCommand(replyText))
        if (ttsCheck.isChecked) speak(replyText)
    }

    private fun parseToolPlan(text: String): ToolPlan? {
        val trimmed = text.trim()
        val jsonText = extractJson(trimmed) ?: return null
        return try {
            val obj = org.json.JSONObject(jsonText)
            val commands = mutableListOf<String>()
            val arr = obj.optJSONArray("commands")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val cmd = arr.optString(i).trim()
                    if (cmd.isNotBlank()) commands.add(cmd)
                }
            }
            val final = obj.optString("final").trim()
            ToolPlan(commands, final)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJson(text: String): String? {
        if (text.startsWith("{") && text.endsWith("}")) return text
        val match = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)?.groupValues?.get(1)
        if (!match.isNullOrBlank()) return match.trim()
        val braceStart = text.indexOf('{')
        val braceEnd = text.lastIndexOf('}')
        return if (braceStart >= 0 && braceEnd > braceStart) text.substring(braceStart, braceEnd + 1) else null
    }

    private data class ToolPlan(val commands: List<String>, val final: String)

    private fun defaultCommandsFor(text: String): List<String> {
        val lower = text.lowercase()
        val commands = mutableListOf<String>()
        val isLastNight = lower.contains("letzte nacht") || lower.contains("last night")
        val wantsLog = lower.contains("log") || lower.contains("logs") || lower.contains("fehler") || lower.contains("error")
        if (isLastNight || wantsLog) {
            commands.add("journalctl --since \"yesterday\" --until \"today\" --no-pager -n 200")
            commands.add("last -n 50")
            commands.add("sudo lastb -n 50")
        }
        if (lower.contains("login") || lower.contains("anmeldung")) {
            commands.add("last -n 50")
        }
        if (lower.contains("disk") || lower.contains("speicher") || lower.contains("space")) {
            commands.add("df -h")
            commands.add("free -m")
        }
        if (lower.contains("prozess") || lower.contains("process")) {
            commands.add("ps aux --sort=-%mem")
        }
        return commands.distinct()
    }

    private fun startVoiceInput() {
        if (!speechController.isAvailable()) {
            appendMessageBubble(isUser = false, text = "STT ist nicht verfügbar.", command = null)
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

        speechController.startListening(
            onResult = { text ->
                chatInput.setText(text)
                sendMessage()
            },
            onError = { error ->
                appendMessageBubble(isUser = false, text = "STT Fehler: $error", command = null)
            }
        )
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
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
                appendMessageBubble(isUser = false, text = "Mikrofon-Berechtigung verweigert", command = null)
            }
        }
    }

    private fun speak(text: String) {
        val engine = tts ?: return
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-reply")
    }

    override fun onDestroy() {
        super.onDestroy()
        speechController.stopListening()
        speechController.release()
        tts?.stop()
        tts?.shutdown()
    }
}
