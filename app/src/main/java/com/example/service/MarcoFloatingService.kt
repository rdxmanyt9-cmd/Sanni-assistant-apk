package com.example.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.AssistantStatus
import com.example.core.MarcoEventBus
import com.example.core.MarcoServiceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class MarcoFloatingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var rootContainer: LinearLayout? = null
    private var orbView: FrameLayout? = null
    private var menuPanel: LinearLayout? = null
    private var orbGlowDrawable: GradientDrawable? = null
    private var pulseAnimator: ValueAnimator? = null
    private var isMenuExpanded = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            MarcoEventBus.emit(MarcoServiceEvent.FloatingOrbHidden)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted.")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (rootContainer == null) {
            attachFloatingOrb()
        }

        MarcoEventBus.emit(MarcoServiceEvent.FloatingOrbStatusChanged(true))
        observeAssistantState()
        return START_STICKY
    }

    private fun observeAssistantState() {
        serviceScope.launch {
            MarcoEventBus.assistantStatus.collect { status ->
                val ringColor = when (status) {
                    is AssistantStatus.Listening -> Color.parseColor("#00FF88")
                    is AssistantStatus.Speaking -> Color.parseColor("#69F0AE")
                    is AssistantStatus.Thinking -> Color.parseColor("#00BFA5")
                    is AssistantStatus.AwaitingConfirmation -> Color.parseColor("#FFB300")
                    is AssistantStatus.Error -> Color.parseColor("#FF5252")
                    AssistantStatus.Idle -> Color.parseColor("#00E676")
                }
                orbGlowDrawable?.setStroke(dp(2), ringColor)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachFloatingOrb() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val orbSize = dp(58)
        val glowBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#EB061611"))
            setStroke(dp(2), Color.parseColor("#00E676"))
        }
        orbGlowDrawable = glowBg

        val orbFrame = FrameLayout(this).apply {
            background = glowBg
            elevation = dp(10).toFloat()
            val icon = ImageView(this@MarcoFloatingService).apply {
                setImageResource(R.drawable.ic_marco_orb)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(dp(6), dp(6), dp(6), dp(6))
                contentDescription = "Marco Floating Orb"
            }
            addView(icon, FrameLayout.LayoutParams(orbSize, orbSize))
        }

        // Expandable quick action pill
        val panelBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(Color.parseColor("#EE081511"))
            setStroke(dp(1), Color.parseColor("#5500E676"))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = panelBg
            setPadding(dp(10), dp(6), dp(10), dp(6))
            visibility = View.GONE

            addView(createQuickActionChip("Mic") {
                MarcoEventBus.emit(MarcoServiceEvent.StartListeningRequested)
                toggleMenu(false)
            })
            addView(createQuickActionChip("Open") {
                val launch = Intent(this@MarcoFloatingService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(launch)
                toggleMenu(false)
            })
            addView(createQuickActionChip("Hide") {
                MarcoEventBus.emit(MarcoServiceEvent.FloatingOrbHidden)
                stopSelf()
            })
        }

        container.addView(orbFrame, LinearLayout.LayoutParams(orbSize, orbSize))
        val panelParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) }
        container.addView(panel, panelParams)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(220)
        }

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        orbFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchStartX).toInt()
                    params.y = initialY + (event.rawY - touchStartY).toInt()
                    runCatching { windowManager.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - touchStartX)
                    val dy = abs(event.rawY - touchStartY)
                    if (dx < dp(10) && dy < dp(10)) {
                        toggleMenu(!isMenuExpanded)
                    }
                    true
                }
                else -> false
            }
        }

        pulseAnimator = ValueAnimator.ofFloat(0.92f, 1.06f).apply {
            duration = 1400L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val s = anim.animatedValue as Float
                orbFrame.scaleX = s
                orbFrame.scaleY = s
            }
            start()
        }

        rootContainer = container
        orbView = orbFrame
        menuPanel = panel
        windowManager.addView(container, params)
    }

    private fun toggleMenu(show: Boolean) {
        isMenuExpanded = show
        menuPanel?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun createQuickActionChip(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#69F0AE"))
            textSize = 13f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hideIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, MarcoFloatingService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Marco Floating Orb")
            .setContentText("Tap orb to speak or open Marco AI")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Hide Orb", hideIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_orb_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_orb_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        pulseAnimator?.cancel()
        rootContainer?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        rootContainer = null
        MarcoEventBus.emit(MarcoServiceEvent.FloatingOrbStatusChanged(false))
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MarcoFloatingService"
        private const val CHANNEL_ID = "marco_orb_channel"
        private const val NOTIFICATION_ID = 4002
        const val ACTION_HIDE = "com.example.action.HIDE_FLOATING_ORB"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MarcoFloatingService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MarcoFloatingService::class.java))
        }
    }
}
