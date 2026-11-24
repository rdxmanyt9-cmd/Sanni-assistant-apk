package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.MarcoLanguageMode
import com.example.core.MarcoSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.marcoDataStore: DataStore<Preferences> by preferencesDataStore(name = "marco_settings")

class MarcoSettingsStore(private val context: Context) {

    private object Keys {
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val WAKE_PHRASE = stringPreferencesKey("wake_phrase")
        val PREFERRED_VOICE = stringPreferencesKey("preferred_voice")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val BG_LISTENING = booleanPreferencesKey("bg_listening")
        val FLOATING_ORB = booleanPreferencesKey("floating_orb")
        val WEB_SEARCH = booleanPreferencesKey("web_search")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
    }

    val settingsFlow: Flow<MarcoSettings> = context.marcoDataStore.data.map { prefs ->
        val mode = prefs[Keys.LANGUAGE_MODE]?.let { raw ->
            runCatching { MarcoLanguageMode.valueOf(raw) }.getOrNull()
        } ?: MarcoLanguageMode.AUTO_HINGLISH

        MarcoSettings(
            languageMode = mode,
            wakePhrase = prefs[Keys.WAKE_PHRASE] ?: "Hey Marco",
            preferredVoiceName = prefs[Keys.PREFERRED_VOICE] ?: "",
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.02f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 0.92f,
            backgroundListeningEnabled = prefs[Keys.BG_LISTENING] ?: false,
            floatingOrbEnabled = prefs[Keys.FLOATING_ORB] ?: false,
            webSearchEnabled = prefs[Keys.WEB_SEARCH] ?: true,
            saveHistoryEnabled = prefs[Keys.SAVE_HISTORY] ?: true,
            confirmSensitiveActions = prefs[Keys.CONFIRM_SENSITIVE] ?: true,
            amoledPureBlack = prefs[Keys.AMOLED_BLACK] ?: true
        )
    }

    suspend fun getCurrentSettings(): MarcoSettings = settingsFlow.first()

    suspend fun updateSettings(transform: (MarcoSettings) -> MarcoSettings) {
        val current = getCurrentSettings()
        val updated = transform(current)
        context.marcoDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_MODE] = updated.languageMode.name
            prefs[Keys.WAKE_PHRASE] = updated.wakePhrase
            prefs[Keys.PREFERRED_VOICE] = updated.preferredVoiceName
            prefs[Keys.SPEECH_RATE] = updated.speechRate.coerceIn(0.6f, 1.8f)
            prefs[Keys.SPEECH_PITCH] = updated.speechPitch.coerceIn(0.6f, 1.5f)
            prefs[Keys.BG_LISTENING] = updated.backgroundListeningEnabled
            prefs[Keys.FLOATING_ORB] = updated.floatingOrbEnabled
            prefs[Keys.WEB_SEARCH] = updated.webSearchEnabled
            prefs[Keys.SAVE_HISTORY] = updated.saveHistoryEnabled
            prefs[Keys.CONFIRM_SENSITIVE] = updated.confirmSensitiveActions
            prefs[Keys.AMOLED_BLACK] = updated.amoledPureBlack
        }
    }
}
