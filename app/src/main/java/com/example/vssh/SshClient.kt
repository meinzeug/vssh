package com.example.vssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class SshClient(
    private val scope: CoroutineScope,
    private val onOutput: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val jsch = JSch()
    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var outputStream: BufferedOutputStream? = null
    private var readerJob: Job? = null

    suspend fun connect(config: SshConfig) {
        disconnect()
        withContext(Dispatchers.IO) {
            onStatus("Verbinde zu ${config.host}:${config.port} ...")
            val newSession = jsch.getSession(config.username, config.host, config.port)
            newSession.setPassword(config.password)
            newSession.setConfig("StrictHostKeyChecking", "no")
            newSession.serverAliveInterval = 10_000
            newSession.connect(10_000)

            val newChannel = newSession.openChannel("shell") as ChannelShell
            newChannel.setPty(true)
            newChannel.connect(5_000)

            val input = BufferedInputStream(newChannel.inputStream)
            outputStream = BufferedOutputStream(newChannel.outputStream)
            session = newSession
            channel = newChannel

            readerJob = scope.readerJob(input)
        }
        onStatus("Verbunden.")
    }

    suspend fun sendCommand(command: String) {
        withContext(Dispatchers.IO) {
            val stream = outputStream ?: throw IOException("Keine aktive Verbindung")
            stream.write((command + "\n").toByteArray())
            stream.flush()
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            readerJob?.cancel()
            readerJob = null
            outputStream?.close()
            outputStream = null
            channel?.disconnect()
            channel = null
            session?.disconnect()
            session = null
        }
        onStatus("Getrennt.")
    }

    suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        val activeSession = session ?: throw IOException("Keine aktive Verbindung")
        val execChannel = activeSession.openChannel("exec") as ChannelExec
        execChannel.setCommand(command)
        execChannel.setInputStream(null)
        val stdout = BufferedInputStream(execChannel.inputStream)
        val stderr = BufferedInputStream(execChannel.errStream)
        execChannel.connect(5_000)

        val deadline = System.currentTimeMillis() + 15_000
        val output = readUntilClosed(execChannel, stdout, deadline)
        val error = readUntilClosed(execChannel, stderr, deadline)
        val timedOut = output.timedOut || error.timedOut
        execChannel.disconnect()

        val combined = (output.text + error.text).trim()
        if (timedOut) {
            return@withContext if (combined.isBlank()) {
                "[Timeout] Befehl hat zu lange gedauert."
            } else {
                "[Timeout] Teilweise Ausgabe:\n$combined"
            }
        }
        combined
    }

    private fun CoroutineScope.readerJob(input: BufferedInputStream): Job {
        return launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                val text = String(buffer, 0, count)
                onOutput(text)
            }
        }
    }

    private fun readUntilClosed(channel: ChannelExec, input: InputStream, deadline: Long): ReadResult {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            var readAny = false
            while (input.available() > 0) {
                val count = input.read(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
                readAny = true
            }
            if (System.currentTimeMillis() > deadline) {
                return ReadResult(output.toString(), true)
            }
            if (channel.isClosed && !readAny && input.available() == 0) {
                break
            }
            Thread.sleep(50)
        }
        val bytes = output.toByteArray()
        return ReadResult(String(bytes), false)
    }

    private data class ReadResult(val text: String, val timedOut: Boolean)
}
