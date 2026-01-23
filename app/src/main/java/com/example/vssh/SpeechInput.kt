package com.example.vssh

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

interface SpeechInputController {
    fun isAvailable(): Boolean
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
    fun release()
}

class NoopSpeechInputController : SpeechInputController {
    override fun isAvailable(): Boolean = false

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        onError("STT nicht aktiviert")
    }

    override fun stopListening() {
        // no-op
    }

    override fun release() {
        // no-op
    }
}

class AndroidSpeechInputController(context: Context) : SpeechInputController {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (isListening) return
        if (!isAvailable()) {
            onError("SpeechRecognizer nicht verfügbar")
            return
        }

        val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            recognizer = it
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio-Fehler. Mikrofon pruefen."
                    SpeechRecognizer.ERROR_CLIENT -> "Client-Fehler. Bitte erneut versuchen."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Keine Mikrofon-Berechtigung. Bitte erlauben."
                    SpeechRecognizer.ERROR_NETWORK -> "Netzwerk-Fehler. Verbindung pruefen."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerk-Timeout. Bitte erneut versuchen."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Keine Sprache erkannt. Bitte nach dem Startton sprechen."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "STT ist beschaeftigt. Bitte kurz warten."
                    SpeechRecognizer.ERROR_SERVER -> "Server-Fehler. Bitte erneut versuchen."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keine Sprache erkannt. Bitte nach dem Startton sprechen."
                    else -> "STT Fehlercode: $error"
                }
                handler.post { onError(message) }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val first = texts?.firstOrNull()?.trim()
                if (!first.isNullOrBlank()) {
                    handler.post { onResult(first) }
                } else {
                    handler.post { onError("Keine Sprache erkannt") }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        isListening = true
        speechRecognizer.startListening(intent)
    }

    override fun stopListening() {
        if (!isListening) return
        recognizer?.stopListening()
        isListening = false
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }
}
