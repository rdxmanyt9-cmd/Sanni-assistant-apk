package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.core.TtsVoiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class MarcoTtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private var currentSpeechRate = 1.02f
    private var currentSpeechPitch = 0.92f
    private var preferredVoiceName = ""
    private var pendingUtterance: Pair<String, String>? = null
    private var onSpeakCompleted: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeakCompleted?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeakCompleted?.invoke()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    onSpeakCompleted?.invoke()
                }
            })

            refreshAvailableVoices()
            applyVoiceConfig(preferredVoiceName, currentSpeechRate, currentSpeechPitch)

            pendingUtterance?.let { (text, localeTag) ->
                pendingUtterance = null
                speak(text, localeTag)
            }
        } else {
            Log.e(TAG, "TextToSpeech init failed with status=$status")
        }
    }

    fun refreshAvailableVoices() {
        if (!isInitialized) return
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        val filtered = voices
            .filter { v ->
                v.locale.language in setOf("hi", "en") && !v.isNetworkConnectionRequired
            }
            .sortedWith(
                compareByDescending<Voice> { isLikelyMaleVoice(it) }
                    .thenByDescending { it.locale.country == "IN" }
                    .thenBy { it.name }
            )
            .map { v ->
                TtsVoiceOption(
                    name = v.name,
                    locale = v.locale.toLanguageTag(),
                    isMaleLikely = isLikelyMaleVoice(v),
                    qualityLabel = when {
                        v.quality >= Voice.QUALITY_VERY_HIGH -> "HD"
                        v.quality >= Voice.QUALITY_HIGH -> "High"
                        else -> "Standard"
                    }
                )
            }
        _availableVoices.value = filtered
    }

    fun applyVoiceConfig(voiceName: String, rate: Float, pitch: Float) {
        preferredVoiceName = voiceName
        currentSpeechRate = rate.coerceIn(0.6f, 1.8f)
        currentSpeechPitch = pitch.coerceIn(0.6f, 1.5f)
        if (!isInitialized) return

        tts.setSpeechRate(currentSpeechRate)
        tts.setPitch(currentSpeechPitch)

        if (voiceName.isNotBlank()) {
            val match = runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == voiceName }
            if (match != null) {
                tts.voice = match
                return
            }
        }

        // Auto-select the best male Hindi/Indian English voice if available
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        val bestMale = voices.firstOrNull {
            it.locale.language == "hi" && isLikelyMaleVoice(it)
        } ?: voices.firstOrNull {
            it.locale.language == "en" && it.locale.country == "IN" && isLikelyMaleVoice(it)
        } ?: voices.firstOrNull {
            it.locale.language == "hi"
        }

        if (bestMale != null) {
            tts.voice = bestMale
        } else {
            tts.language = Locale("hi", "IN")
        }
    }

    fun speak(text: String, localeTag: String = "hi-IN", onDone: (() -> Unit)? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            onDone?.invoke()
            return
        }
        onSpeakCompleted = onDone
        if (!isInitialized) {
            pendingUtterance = clean to localeTag
            return
        }

        if (preferredVoiceName.isBlank()) {
            val desiredLocale = Locale.forLanguageTag(localeTag)
            val voices = runCatching { tts.voices }.getOrNull().orEmpty()
            val localeMale = voices.firstOrNull {
                it.locale.language == desiredLocale.language && isLikelyMaleVoice(it)
            } ?: voices.firstOrNull { it.locale.language == desiredLocale.language }
            if (localeMale != null) {
                tts.voice = localeMale
            } else {
                tts.language = desiredLocale
            }
        }

        tts.setSpeechRate(currentSpeechRate)
        tts.setPitch(currentSpeechPitch)
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts.stop()
        }
        _isSpeaking.value = false
    }

    fun shutdown() {
        if (isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        _isSpeaking.value = false
    }

    private fun isLikelyMaleVoice(voice: Voice): Boolean {
        val n = voice.name.lowercase()
        val maleMarkers = listOf("male", "-m-", "hid-m", "in-m", "iol", "iog", "sfg", "tpc", "x-hia", "x-hid", "x-ene", "b", "d")
        val femaleMarkers = listOf("female", "-f-", "x-hie", "x-end", "sfb", "iob", "ioa")
        if (femaleMarkers.any { n.contains(it) }) return false
        return maleMarkers.any { n.contains(it) }
    }

    companion object {
        private const val TAG = "MarcoTtsManager"
    }
}
