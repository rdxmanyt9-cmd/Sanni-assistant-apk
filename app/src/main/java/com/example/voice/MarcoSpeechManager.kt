package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SpeechListenEvent {
    data class FinalResult(val text: String) : SpeechListenEvent
    data class PartialResult(val text: String) : SpeechListenEvent
    data class Error(val message: String, val errorCode: Int) : SpeechListenEvent
}

class MarcoSpeechManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var eventCallback: ((SpeechListenEvent) -> Unit)? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        languageTag: String = "hi-IN",
        onEvent: (SpeechListenEvent) -> Unit
    ) {
        mainHandler.post {
            eventCallback = onEvent
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _isListening.value = false
                onEvent(
                    SpeechListenEvent.Error(
                        "Is device par Google/Android Speech Recognition service available nahi hai.",
                        SpeechRecognizer.ERROR_CLIENT
                    )
                )
                return@post
            }

            runCatching {
                recognizer?.destroy()
                recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                _isListening.value = true
                recognizer?.startListening(intent)
            }.onFailure { e ->
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _isListening.value = false
                _amplitude.value = 0f
                onEvent(
                    SpeechListenEvent.Error(
                        "Microphone listening shuru nahi ho paayi: ${e.localizedMessage}",
                        SpeechRecognizer.ERROR_CLIENT
                    )
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { recognizer?.stopListening() }
            _isListening.value = false
            _amplitude.value = 0f
        }
    }

    fun cancel() {
        mainHandler.post {
            runCatching { recognizer?.cancel() }
            _isListening.value = false
            _amplitude.value = 0f
        }
    }

    fun destroy() {
        mainHandler.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
            _isListening.value = false
            _amplitude.value = 0f
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize typical speech RMS (-2f..10f) into 0f..1f
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            _amplitude.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening.value = false
            _amplitude.value = 0f
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _amplitude.value = 0f
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error aaya."
                SpeechRecognizer.ERROR_CLIENT -> "Speech client error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing hai."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition ke liye network connection slow ya offline hai."
                SpeechRecognizer.ERROR_NO_MATCH -> "Koi awaaz clear sunai nahi di. Phir se boliye, Sanni."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer abhi busy hai, ek second baad try kijiye."
                SpeechRecognizer.ERROR_SERVER -> "Speech server temporary error."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Awaaz detect nahi hui. Mic tap karke phir boliye."
                else -> "Voice recognition error ($error)."
            }
            eventCallback?.invoke(SpeechListenEvent.Error(msg, error))
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            _amplitude.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()?.trim().orEmpty()
            if (best.isNotEmpty()) {
                eventCallback?.invoke(SpeechListenEvent.FinalResult(best))
            } else {
                eventCallback?.invoke(
                    SpeechListenEvent.Error("Kuch samajh nahi aaya, phir se boliye.", SpeechRecognizer.ERROR_NO_MATCH)
                )
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isNotEmpty()) {
                eventCallback?.invoke(SpeechListenEvent.PartialResult(partial))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "MarcoSpeechManager"
    }
}
