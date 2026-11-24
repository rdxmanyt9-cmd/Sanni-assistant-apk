package com.example.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MarcoServiceEvent {
    data object StartListeningRequested : MarcoServiceEvent
    data object StopListeningRequested : MarcoServiceEvent
    data class WakePhraseDetected(val commandText: String) : MarcoServiceEvent
    data object StopBackgroundServiceRequested : MarcoServiceEvent
    data object FloatingOrbHidden : MarcoServiceEvent
    data class VoiceServiceStatusChanged(val running: Boolean) : MarcoServiceEvent
    data class FloatingOrbStatusChanged(val running: Boolean) : MarcoServiceEvent
}

object MarcoEventBus {
    private val _events = MutableSharedFlow<MarcoServiceEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<MarcoServiceEvent> = _events.asSharedFlow()

    private val _assistantStatus = MutableStateFlow<AssistantStatus>(AssistantStatus.Idle)
    val assistantStatus: StateFlow<AssistantStatus> = _assistantStatus.asStateFlow()

    private val _voiceServiceRunning = MutableStateFlow(false)
    val voiceServiceRunning: StateFlow<Boolean> = _voiceServiceRunning.asStateFlow()

    private val _floatingOrbRunning = MutableStateFlow(false)
    val floatingOrbRunning: StateFlow<Boolean> = _floatingOrbRunning.asStateFlow()

    private val _lastHeardTranscript = MutableStateFlow("")
    val lastHeardTranscript: StateFlow<String> = _lastHeardTranscript.asStateFlow()

    fun emit(event: MarcoServiceEvent) {
        when (event) {
            is MarcoServiceEvent.VoiceServiceStatusChanged -> _voiceServiceRunning.value = event.running
            is MarcoServiceEvent.FloatingOrbStatusChanged -> _floatingOrbRunning.value = event.running
            else -> Unit
        }
        _events.tryEmit(event)
    }

    fun updateStatus(status: AssistantStatus) {
        _assistantStatus.value = status
    }

    fun updateTranscript(text: String) {
        _lastHeardTranscript.value = text
    }
}
