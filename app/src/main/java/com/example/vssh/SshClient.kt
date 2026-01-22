package com.example.vssh

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
import java.io.IOException

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

    private fun CoroutineScope.readerJob(input: BufferedInputStream): Job {
        return kotlinx.coroutines.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                val text = String(buffer, 0, count)
                onOutput(text)
            }
        }
    }
}
