package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.MarcoGeminiBrain
import com.example.core.AssistantStatus
import com.example.core.ChatMessage
import com.example.core.DeviceTelemetry
import com.example.core.MarcoAction
import com.example.core.MarcoActionType
import com.example.core.MarcoEventBus
import com.example.core.MarcoServiceEvent
import com.example.core.MarcoSettings
import com.example.core.MemoryPreference
import com.example.core.MessageSender
import com.example.core.PendingConfirmation
import com.example.core.TtsVoiceOption
import com.example.data.local.MarcoDatabase
import com.example.data.local.MarcoRepository
import com.example.data.local.MarcoSettingsStore
import com.example.data.network.WebSearchService
import com.example.device.DeviceActionExecutor
import com.example.device.DeviceTelemetryMonitor
import com.example.service.MarcoFloatingService
import com.example.service.MarcoVoiceService
import com.example.voice.MarcoSpeechManager
import com.example.voice.MarcoTtsManager
import com.example.voice.SpeechListenEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MarcoViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = MarcoDatabase.getInstance(appContext)
    private val settingsStore = MarcoSettingsStore(appContext)
    private val repository = MarcoRepository(database, settingsStore)
    private val webSearchService = WebSearchService()
    private val geminiBrain = MarcoGeminiBrain(webSearchService)
    private val telemetryMonitor = DeviceTelemetryMonitor(appContext)
    private val actionExecutor = DeviceActionExecutor(appContext, telemetryMonitor)
    private val ttsManager = MarcoTtsManager(appContext)
    private val speechManager = MarcoSpeechManager(appContext)

    val settings: StateFlow<MarcoSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarcoSettings())

    val messages: StateFlow<List<ChatMessage>> = repository.messagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryPreference>> = repository.memoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableVoices: StateFlow<List<TtsVoiceOption>> = ttsManager.availableVoices
    val speechAmplitude: StateFlow<Float> = speechManager.amplitude

    private val _status = MutableStateFlow<AssistantStatus>(AssistantStatus.Idle)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _telemetry = MutableStateFlow(telemetryMonitor.readTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _permissionPromptNeeded = MutableStateFlow<String?>(null)
    val permissionPromptNeeded: StateFlow<String?> = _permissionPromptNeeded.asStateFlow()

    private val _geminiDiagnostics = MutableStateFlow<String?>(null)
    val geminiDiagnostics: StateFlow<String?> = _geminiDiagnostics.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { s ->
                ttsManager.applyVoiceConfig(s.preferredVoiceName, s.speechRate, s.speechPitch)
            }
        }

        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                if (speaking) {
                    setStatus(AssistantStatus.Speaking)
                } else if (_status.value is AssistantStatus.Speaking) {
                    setStatus(
                        if (_pendingConfirmation.value != null) AssistantStatus.AwaitingConfirmation
                        else AssistantStatus.Idle
                    )
                }
            }
        }

        viewModelScope.launch {
            MarcoEventBus.events.collect { event ->
                when (event) {
                    MarcoServiceEvent.StartListeningRequested -> toggleVoiceListening()
                    MarcoServiceEvent.StopListeningRequested -> stopVoiceListening()
                    is MarcoServiceEvent.WakePhraseDetected -> {
                        handleIncomingCommand(event.commandText, speakResponse = true)
                    }
                    MarcoServiceEvent.FloatingOrbHidden -> {
                        repository.updateSettings { it.copy(floatingOrbEnabled = false) }
                        refreshTelemetry()
                    }
                    is MarcoServiceEvent.VoiceServiceStatusChanged,
                    is MarcoServiceEvent.FloatingOrbStatusChanged -> refreshTelemetry()
                    MarcoServiceEvent.StopBackgroundServiceRequested -> {
                        MarcoVoiceService.stop(appContext)
                        repository.updateSettings { it.copy(backgroundListeningEnabled = false) }
                        refreshTelemetry()
                    }
                }
            }
        }

        viewModelScope.launch {
            delay(500)
            val existing = repository.getRecentHistoryForPrompt(1)
            if (existing.isEmpty()) {
                repository.addMessage(
                    sender = MessageSender.MARCO,
                    text = "Hello Sanni! Main Marco hoon. Bataiye, aaj main aapki kya help kar sakta hoon?"
                )
            }
            refreshTelemetry()
        }
    }

    fun refreshTelemetry() {
        _telemetry.value = telemetryMonitor.readTelemetry()
    }

    fun toggleVoiceListening() {
        if (speechManager.isListening.value) {
            stopVoiceListening()
            return
        }
        refreshTelemetry()
        if (!_telemetry.value.micGranted) {
            _permissionPromptNeeded.value = android.Manifest.permission.RECORD_AUDIO
            setStatus(AssistantStatus.Error("Microphone permission chahiye."))
            return
        }

        ttsManager.stop()
        _partialTranscript.value = ""
        setStatus(AssistantStatus.Listening)

        val localeTag = settings.value.languageMode.localeTag
        speechManager.startListening(localeTag) { event ->
            when (event) {
                is SpeechListenEvent.PartialResult -> {
                    _partialTranscript.value = event.text
                }
                is SpeechListenEvent.FinalResult -> {
                    _partialTranscript.value = ""
                    handleIncomingCommand(event.text, speakResponse = true)
                }
                is SpeechListenEvent.Error -> {
                    _partialTranscript.value = ""
                    setStatus(AssistantStatus.Error(event.message))
                }
            }
        }
    }

    fun stopVoiceListening() {
        speechManager.stopListening()
        _partialTranscript.value = ""
        if (_status.value is AssistantStatus.Listening) {
            setStatus(AssistantStatus.Idle)
        }
    }

    fun sendTextCommand(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        handleIncomingCommand(clean, speakResponse = true)
    }

    fun clearPermissionPrompt() {
        _permissionPromptNeeded.value = null
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            val results = executeActionList(pending.actions)
            val summary = results.ifBlank { "Done Sanni! Action execute kar diya hai." }
            repository.addMessage(MessageSender.MARCO, summary, actionTag = pending.actionSummary)
            speakOut(summary, settings.value.languageMode.localeTag)
            refreshTelemetry()
        }
    }

    fun cancelPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        setStatus(AssistantStatus.Idle)
        viewModelScope.launch {
            val msg = "Theek hai Sanni, '${pending.actionSummary}' cancel kar diya."
            repository.addMessage(MessageSender.MARCO, msg)
            speakOut(msg, settings.value.languageMode.localeTag)
        }
    }

    fun toggleFlashlightDirect() {
        val next = if (_telemetry.value.flashlightOn) "off" else "on"
        val res = actionExecutor.toggleFlashlight(next)
        refreshTelemetry()
        viewModelScope.launch {
            repository.addMessage(MessageSender.MARCO, res.feedbackMessage, actionTag = "FLASHLIGHT")
        }
    }

    fun adjustVolumeDirect(mode: String) {
        val res = actionExecutor.adjustVolume(mode)
        refreshTelemetry()
        viewModelScope.launch {
            repository.addMessage(MessageSender.MARCO, res.feedbackMessage, actionTag = "VOLUME")
        }
    }

    fun setBrightnessDirect(percent: Int) {
        val res = actionExecutor.setBrightness(percent.toString())
        if (res.missingPermission != null) {
            _permissionPromptNeeded.value = res.missingPermission
        }
        refreshTelemetry()
    }

    fun runDirectAction(action: MarcoAction) {
        viewModelScope.launch {
            val feedback = executeActionList(listOf(action))
            if (feedback.isNotBlank()) {
                repository.addMessage(MessageSender.MARCO, feedback, actionTag = action.type.name)
            }
            refreshTelemetry()
        }
    }

    fun toggleBackgroundService(enable: Boolean) {
        viewModelScope.launch {
            refreshTelemetry()
            if (enable) {
                if (!_telemetry.value.micGranted) {
                    _permissionPromptNeeded.value = android.Manifest.permission.RECORD_AUDIO
                    return@launch
                }
                MarcoVoiceService.start(appContext)
            } else {
                MarcoVoiceService.stop(appContext)
            }
            repository.updateSettings { it.copy(backgroundListeningEnabled = enable) }
            delay(250)
            refreshTelemetry()
        }
    }

    fun toggleFloatingOrb(enable: Boolean) {
        viewModelScope.launch {
            refreshTelemetry()
            if (enable) {
                if (!_telemetry.value.overlayGranted) {
                    _permissionPromptNeeded.value = android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    return@launch
                }
                MarcoFloatingService.start(appContext)
            } else {
                MarcoFloatingService.stop(appContext)
            }
            repository.updateSettings { it.copy(floatingOrbEnabled = enable) }
            delay(250)
            refreshTelemetry()
        }
    }

    fun updateSettings(transform: (MarcoSettings) -> MarcoSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun previewVoice() {
        speakOut(
            "Hello Sanni! Main Marco hoon. Meri awaaz ab bilkul set hai.",
            settings.value.languageMode.localeTag
        )
    }

    fun stopSpeaking() {
        ttsManager.stop()
        setStatus(AssistantStatus.Idle)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearConversationHistory()
            repository.addMessage(
                MessageSender.MARCO,
                "Conversation history clear ho gayi hai, Sanni. Fresh start ke liye ready!"
            )
        }
    }

    fun addOrUpdateMemory(key: String, value: String) {
        viewModelScope.launch {
            repository.saveMemory(key, value)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun testGeminiConnection() {
        viewModelScope.launch {
            _geminiDiagnostics.value = "Testing Firebase AI Logic (gemini-3.8-flash)..."
            val res = geminiBrain.testConnection()
            _geminiDiagnostics.value = res.fold(
                onSuccess = { reply -> "Connected: $reply" },
                onFailure = { err -> "Error: ${err.localizedMessage ?: "Check internet & App Check"}" }
            )
        }
    }

    private fun handleIncomingCommand(userText: String, speakResponse: Boolean) {
        viewModelScope.launch {
            // Check if user is responding verbally to a pending confirmation!
            val currentPending = _pendingConfirmation.value
            if (currentPending != null) {
                val lower = userText.trim().lowercase()
                val approved = listOf("yes", "haan", "ha", "kar do", "send", "bhej", "ok", "sure", "confirm")
                    .any { lower.contains(it) }
                val rejected = listOf("no", "nahi", "mat", "cancel", "ruko", "stop")
                    .any { lower.contains(it) }
                if (approved && !rejected) {
                    repository.addMessage(MessageSender.USER, userText)
                    confirmPendingAction()
                    return@launch
                } else if (rejected) {
                    repository.addMessage(MessageSender.USER, userText)
                    cancelPendingAction()
                    return@launch
                }
            }

            repository.addMessage(MessageSender.USER, userText)
            setStatus(AssistantStatus.Thinking)
            refreshTelemetry()

            val currentSettings = repository.getCurrentSettings()
            val history = repository.getRecentHistoryForPrompt(10)
            val mems = repository.getAllMemoriesOnce()

            val brainResp = geminiBrain.processCommand(
                userText = userText,
                history = history,
                memories = mems,
                telemetry = _telemetry.value,
                settings = currentSettings
            )

            // Handle immediate SAVE_MEMORY or TOGGLE_FLOATING_ORB
            brainResp.actions.filter { it.type == MarcoActionType.SAVE_MEMORY }.forEach { act ->
                repository.saveMemory(act.target, act.payload)
            }

            val executableActions = brainResp.actions.filterNot { it.type == MarcoActionType.SAVE_MEMORY }

            if (brainResp.requiresConfirmation && executableActions.isNotEmpty()) {
                val actionSummary = executableActions.joinToString(", ") {
                    "${it.type.name}: ${it.target}".trimEnd(':', ' ')
                }
                val prompt = brainResp.confirmationPrompt ?: brainResp.spokenReply
                _pendingConfirmation.value = PendingConfirmation(
                    prompt = prompt,
                    actionSummary = actionSummary,
                    actions = executableActions
                )
                repository.addMessage(
                    sender = MessageSender.MARCO,
                    text = prompt,
                    actionTag = "CONFIRMATION REQUIRED",
                    webSources = brainResp.webSources
                )
                setStatus(AssistantStatus.AwaitingConfirmation)
                if (speakResponse) {
                    speakOut(prompt, brainResp.replyLanguage)
                }
                return@launch
            }

            var finalReply = brainResp.spokenReply
            var actionBadge = ""
            if (executableActions.isNotEmpty()) {
                val executionFeedback = executeActionList(executableActions)
                actionBadge = executableActions.joinToString(" • ") { it.type.name }
                if (executableActions.any {
                        it.type == MarcoActionType.SCREEN_READ ||
                            it.type == MarcoActionType.READ_NOTIFICATIONS
                    } && executionFeedback.isNotBlank()
                ) {
                    finalReply = "$finalReply\n\n$executionFeedback"
                } else if (executionFeedback.isNotBlank() &&
                    (executionFeedback.contains("permission", true) ||
                        executionFeedback.contains("install nahi", true) ||
                        executionFeedback.contains("failed", true) ||
                        executionFeedback.contains("enable kijiye", true))
                ) {
                    finalReply = executionFeedback
                }
            }

            repository.addMessage(
                sender = MessageSender.MARCO,
                text = finalReply,
                actionTag = actionBadge,
                webSources = brainResp.webSources
            )

            refreshTelemetry()
            if (speakResponse) {
                speakOut(finalReply, brainResp.replyLanguage)
            } else {
                setStatus(AssistantStatus.Idle)
            }
        }
    }

    private suspend fun executeActionList(actions: List<MarcoAction>): String {
        val feedbacks = mutableListOf<String>()
        for ((index, action) in actions.withIndex()) {
            if (action.type == MarcoActionType.TOGGLE_FLOATING_ORB) {
                val enable = !action.target.equals("off", ignoreCase = true)
                toggleFloatingOrb(enable)
                feedbacks += if (enable) "Floating orb on kar diya." else "Floating orb hide kar diya."
                continue
            }

            val res = actionExecutor.execute(action)
            if (res.missingPermission != null) {
                _permissionPromptNeeded.value = res.missingPermission
            }
            if (res.feedbackMessage.isNotBlank()) {
                feedbacks += res.feedbackMessage
            }
            if (index < actions.lastIndex) {
                delay(450L)
            }
        }
        return feedbacks.distinct().joinToString("\n")
    }

    private fun speakOut(text: String, localeTag: String) {
        // Keep TTS crisp for very long screen dumps
        val spokenText = if (text.length > 420) text.take(400) + "..." else text
        setStatus(AssistantStatus.Speaking)
        ttsManager.speak(spokenText, localeTag) {
            setStatus(
                if (_pendingConfirmation.value != null) AssistantStatus.AwaitingConfirmation
                else AssistantStatus.Idle
            )
        }
    }

    private fun setStatus(newStatus: AssistantStatus) {
        _status.value = newStatus
        MarcoEventBus.updateStatus(newStatus)
    }

    override fun onCleared() {
        speechManager.destroy()
        ttsManager.shutdown()
        super.onCleared()
    }

    companion object {
        private const val TAG = "MarcoViewModel"
    }
}
