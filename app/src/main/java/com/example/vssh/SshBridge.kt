package com.example.vssh

import java.util.concurrent.atomic.AtomicBoolean

object SshBridge {
    private val connected = AtomicBoolean(false)
    @Volatile private var sender: ((String) -> Unit)? = null
    @Volatile private var execFn: (suspend (String) -> String)? = null
    private val outputBuffer = StringBuilder()
    private const val maxBufferChars = 8000

    fun setSender(isConnected: Boolean, sendFn: ((String) -> Unit)?, execFn: (suspend (String) -> String)?) {
        connected.set(isConnected)
        sender = sendFn
        this.execFn = execFn
    }

    fun canSend(): Boolean = connected.get() && sender != null

    fun canExecute(): Boolean = connected.get() && execFn != null

    fun sendCommand(command: String): Boolean {
        val send = sender ?: return false
        send(command)
        return true
    }

    suspend fun runCommand(command: String): String {
        val exec = execFn ?: return ""
        return exec(command)
    }

    fun appendOutput(text: String) {
        outputBuffer.append(text)
        if (outputBuffer.length > maxBufferChars) {
            outputBuffer.delete(0, outputBuffer.length - maxBufferChars)
        }
    }

    fun getRecentOutput(): String = outputBuffer.toString()
}
