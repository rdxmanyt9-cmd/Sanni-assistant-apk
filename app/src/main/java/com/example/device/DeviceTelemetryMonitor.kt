package com.example.device

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.core.DeviceTelemetry
import com.example.core.MarcoEventBus
import com.example.service.MarcoAccessibilityService
import com.example.service.MarcoNotificationListenerService

class DeviceTelemetryMonitor(private val context: Context) {

    @Volatile
    var isTorchActive: Boolean = false

    fun readTelemetry(): DeviceTelemetry {
        val batteryInfo = readBatteryInfo()
        val networkInfo = readNetworkInfo()
        val audioInfo = readAudioInfo()
        val brightnessPercent = readBrightnessPercent()

        return DeviceTelemetry(
            batteryPercent = batteryInfo.first,
            isCharging = batteryInfo.second,
            wifiConnected = networkInfo.first,
            mobileDataConnected = networkInfo.second,
            mediaVolumePercent = audioInfo.first,
            isMuted = audioInfo.second,
            screenBrightnessPercent = brightnessPercent,
            flashlightOn = isTorchActive,
            hasFlashlight = checkHasFlashlight(),
            isOnline = networkInfo.first || networkInfo.second,
            micGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else true,
            overlayGranted = Settings.canDrawOverlays(context),
            accessibilityEnabled = isAccessibilityServiceEnabled(),
            notificationListenerEnabled = isNotificationListenerEnabled(),
            contactsGranted = hasPermission(Manifest.permission.READ_CONTACTS),
            callPhoneGranted = hasPermission(Manifest.permission.CALL_PHONE),
            cameraGranted = hasPermission(Manifest.permission.CAMERA),
            writeSettingsGranted = Settings.System.canWrite(context),
            batteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            backgroundServiceRunning = MarcoEventBus.voiceServiceRunning.value,
            floatingOrbRunning = MarcoEventBus.floatingOrbRunning.value
        )
    }

    private fun readBatteryInfo(): Pair<Int, Boolean> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale
        else bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return pct.coerceIn(0, 100) to isCharging
    }

    private fun readNetworkInfo(): Pair<Boolean, Boolean> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false to false
        val activeNetwork = cm.activeNetwork ?: return false to false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false to false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return wifi to cellular
    }

    private fun readAudioInfo(): Pair<Int, Boolean> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return 50 to false
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val pct = (current * 100) / max
        return pct to (current == 0)
    }

    private fun readBrightnessPercent(): Int {
        return runCatching {
            val raw = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            ((raw.coerceIn(0, 255) * 100) / 255)
        }.getOrDefault(50)
    }

    private fun checkHasFlashlight(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            cm.cameraIdList.any { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrDefault(false)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (MarcoAccessibilityService.instance != null) return true
        val expected = ComponentName(context, MarcoAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun isNotificationListenerEnabled(): Boolean {
        if (MarcoNotificationListenerService.instance != null) return true
        val expected = ComponentName(context, MarcoNotificationListenerService::class.java).flattenToString()
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return listeners.contains(expected, ignoreCase = true)
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
