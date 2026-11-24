package com.example.core

sealed interface AssistantStatus {
    data object Idle : AssistantStatus
    data object Listening : AssistantStatus
    data object Thinking : AssistantStatus
    data object Speaking : AssistantStatus
    data object AwaitingConfirmation : AssistantStatus
    data class Error(val message: String) : AssistantStatus
}

enum class MarcoLanguageMode(val displayName: String, val localeTag: String) {
    AUTO_HINGLISH("Auto (Hindi + Hinglish + English)", "hi-IN"),
    HINDI("Hindi (Primary)", "hi-IN"),
    ENGLISH("English (India)", "en-IN")
}

enum class MarcoActionType {
    NONE,
    OPEN_APP,
    CALL_CONTACT,
    DIAL_NUMBER,
    PREPARE_WHATSAPP,
    PREPARE_SMS,
    TOGGLE_FLASHLIGHT,
    ADJUST_VOLUME,
    SET_BRIGHTNESS,
    CHECK_DEVICE_STATUS,
    SET_ALARM,
    SET_TIMER,
    NAVIGATE_MAPS,
    OPEN_SYSTEM_SETTINGS,
    SCREEN_READ,
    SCREEN_TAP,
    SCREEN_SCROLL,
    SCREEN_INPUT_TEXT,
    SCREEN_GLOBAL_ACTION,
    READ_NOTIFICATIONS,
    WEB_SEARCH,
    SAVE_MEMORY,
    TOGGLE_FLOATING_ORB
}

data class MarcoAction(
    val type: MarcoActionType,
    val target: String = "",
    val payload: String = "",
    val extra: String = ""
)

data class MarcoBrainResponse(
    val spokenReply: String,
    val replyLanguage: String = "hi-IN",
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val actions: List<MarcoAction> = emptyList(),
    val webSources: List<String> = emptyList(),
    val isOfflineFallback: Boolean = false
)

data class PendingConfirmation(
    val id: Long = System.currentTimeMillis(),
    val prompt: String,
    val actionSummary: String,
    val actions: List<MarcoAction>
)

data class DeviceTelemetry(
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val wifiConnected: Boolean = false,
    val mobileDataConnected: Boolean = false,
    val mediaVolumePercent: Int = 50,
    val isMuted: Boolean = false,
    val screenBrightnessPercent: Int = 50,
    val flashlightOn: Boolean = false,
    val hasFlashlight: Boolean = false,
    val isOnline: Boolean = true,
    val micGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val contactsGranted: Boolean = false,
    val callPhoneGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val backgroundServiceRunning: Boolean = false,
    val floatingOrbRunning: Boolean = false
)

enum class MessageSender {
    USER,
    MARCO,
    SYSTEM
}

data class ChatMessage(
    val id: Long = 0,
    val sender: MessageSender,
    val text: String,
    val actionTag: String = "",
    val webSources: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class MemoryPreference(
    val id: Long = 0,
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class TtsVoiceOption(
    val name: String,
    val locale: String,
    val isMaleLikely: Boolean,
    val qualityLabel: String
)

data class MarcoSettings(
    val languageMode: MarcoLanguageMode = MarcoLanguageMode.AUTO_HINGLISH,
    val wakePhrase: String = "Hey Marco",
    val preferredVoiceName: String = "",
    val speechRate: Float = 1.02f,
    val speechPitch: Float = 0.92f,
    val backgroundListeningEnabled: Boolean = false,
    val floatingOrbEnabled: Boolean = false,
    val webSearchEnabled: Boolean = true,
    val saveHistoryEnabled: Boolean = true,
    val confirmSensitiveActions: Boolean = true,
    val amoledPureBlack: Boolean = true
)
