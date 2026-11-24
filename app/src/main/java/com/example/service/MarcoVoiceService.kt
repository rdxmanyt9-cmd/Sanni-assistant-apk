package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.AssistantStatus
import com.example.core.MarcoEventBus
import com.example.core.MarcoServiceEvent
import com.example.data.local.MarcoSettingsStore
import com.example.voice.MarcoSpeechManager
import com.example.voice.SpeechListenEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MarcoVoiceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var speechManager: MarcoSpeechManager
    private lateinit var settingsStore: MarcoSettingsStore
    private var wakePhrase: String = "Hey Marco"
    private var languageTag: String = "hi-IN"
    private var isPaused: Boolean = false
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        speechManager = MarcoSpeechManager(this)
        settingsStore = MarcoSettingsStore(this)
        createNotificationChannel()

        serviceScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                wakePhrase = settings.wakePhrase.ifBlank { "Hey Marco" }
                languageTag = settings.languageMode.localeTag
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                isPaused = !isPaused
                if (isPaused) {
                    speechManager.cancel()
                } else {
                    scheduleNextListenCycle(400L)
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_TRIGGER_NOW -> {
                MarcoEventBus.emit(MarcoServiceEvent.StartListeningRequested)
                return START_STICKY
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Cannot start MarcoVoiceService without RECORD_AUDIO permission.")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        MarcoEventBus.emit(MarcoServiceEvent.VoiceServiceStatusChanged(true))
        scheduleNextListenCycle(500L)
        return START_STICKY
    }

    private fun scheduleNextListenCycle(delayMs: Long) {
        loopJob?.cancel()
        if (isPaused) return
        loopJob = serviceScope.launch {
            delay(delayMs)
            // Do not fight with foreground app active listening/speaking
            val status = MarcoEventBus.assistantStatus.value
            if (status == AssistantStatus.Listening || status == AssistantStatus.Speaking) {
                scheduleNextListenCycle(1500L)
                return@launch
            }
            listenForWakePhrase()
        }
    }

    private fun listenForWakePhrase() {
        if (isPaused) return
        speechManager.startListening(languageTag) { event ->
            when (event) {
                is SpeechListenEvent.PartialResult -> Unit
                is SpeechListenEvent.FinalResult -> {
                    val heard = event.text
                    val wakeLower = wakePhrase.lowercase()
                    val heardLower = heard.lowercase()
                    if (heardLower.contains(wakeLower) || heardLower.contains("marco")) {
                        val stripped = heard
                            .replace(Regex(Regex.escape(wakePhrase), RegexOption.IGNORE_CASE), "")
                            .replace(Regex("hey\\s+marco|ok\\s+marco|marco", RegexOption.IGNORE_CASE), "")
                            .trim()
                        MarcoEventBus.emit(
                            MarcoServiceEvent.WakePhraseDetected(
                                stripped.ifBlank { "Hello Marco" }
                            )
                        )
                        scheduleNextListenCycle(3500L)
                    } else {
                        scheduleNextListenCycle(900L)
                    }
                }
                is SpeechListenEvent.Error -> {
                    val backoff = if (event.errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2500L else 1400L
                    scheduleNextListenCycle(backoff)
                }
            }
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MarcoVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MarcoVoiceService::class.java).apply { action = ACTION_TOGGLE_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = if (isPaused) {
            "Wake phrase paused • Tap Resume or open Marco"
        } else {
            "Listening for \"$wakePhrase\" for Sanni Kumar"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Marco AI Active")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, if (isPaused) "Resume" else "Pause", pauseIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_voice_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_voice_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        speechManager.destroy()
        MarcoEventBus.emit(MarcoServiceEvent.VoiceServiceStatusChanged(false))
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MarcoVoiceService"
        private const val CHANNEL_ID = "marco_voice_channel"
        private const val NOTIFICATION_ID = 4001

        const val ACTION_STOP = "com.example.action.STOP_VOICE_SERVICE"
        const val ACTION_TOGGLE_PAUSE = "com.example.action.PAUSE_VOICE_SERVICE"
        const val ACTION_TRIGGER_NOW = "com.example.action.TRIGGER_NOW"

        fun start(context: Context) {
            val intent = Intent(context, MarcoVoiceService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MarcoVoiceService::class.java))
        }
    }
}
