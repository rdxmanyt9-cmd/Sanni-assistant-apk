package com.example.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.MarcoAction
import com.example.core.MarcoActionType
import com.example.service.MarcoAccessibilityService
import com.example.service.MarcoNotificationListenerService
import java.net.URLEncoder

data class ActionExecutionResult(
    val success: Boolean,
    val feedbackMessage: String,
    val missingPermission: String? = null
)

class DeviceActionExecutor(
    private val context: Context,
    private val telemetryMonitor: DeviceTelemetryMonitor
) {

    fun execute(action: MarcoAction): ActionExecutionResult {
        return when (action.type) {
            MarcoActionType.NONE -> ActionExecutionResult(true, "")
            MarcoActionType.OPEN_APP -> openApp(action.target)
            MarcoActionType.CALL_CONTACT -> callContactOrNumber(action.target, directCall = true)
            MarcoActionType.DIAL_NUMBER -> callContactOrNumber(action.target, directCall = false)
            MarcoActionType.PREPARE_WHATSAPP -> prepareWhatsApp(action.target, action.payload)
            MarcoActionType.PREPARE_SMS -> prepareSms(action.target, action.payload)
            MarcoActionType.TOGGLE_FLASHLIGHT -> toggleFlashlight(action.target)
            MarcoActionType.ADJUST_VOLUME -> adjustVolume(action.target, action.payload)
            MarcoActionType.SET_BRIGHTNESS -> setBrightness(action.target)
            MarcoActionType.CHECK_DEVICE_STATUS -> reportDeviceStatus()
            MarcoActionType.SET_ALARM -> setAlarm(action.target, action.payload)
            MarcoActionType.SET_TIMER -> setTimer(action.target, action.payload)
            MarcoActionType.NAVIGATE_MAPS -> navigateMaps(action.target)
            MarcoActionType.OPEN_SYSTEM_SETTINGS -> openSystemSettings(action.target)
            MarcoActionType.SCREEN_READ -> readCurrentScreen()
            MarcoActionType.SCREEN_TAP -> tapScreenElement(action.target)
            MarcoActionType.SCREEN_SCROLL -> scrollScreen(action.target)
            MarcoActionType.SCREEN_INPUT_TEXT -> enterScreenText(action.target, action.payload)
            MarcoActionType.SCREEN_GLOBAL_ACTION -> performGlobalNavigation(action.target)
            MarcoActionType.READ_NOTIFICATIONS -> readNotifications()
            MarcoActionType.WEB_SEARCH,
            MarcoActionType.SAVE_MEMORY,
            MarcoActionType.TOGGLE_FLOATING_ORB -> ActionExecutionResult(true, "")
        }
    }

    private fun openApp(appQuery: String): ActionExecutionResult {
        val clean = appQuery.trim().lowercase()
        if (clean.isBlank()) return ActionExecutionResult(false, "Kaunsa app kholna hai, Sanni?")

        // Special intents for standard system utilities
        when {
            clean.contains("camera") || clean.contains("camra") -> {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchSafely(intent)) return ActionExecutionResult(true, "Camera khol raha hoon.")
            }
            clean == "settings" || clean == "setting" -> return openSystemSettings("main")
            clean.contains("calculator") || clean.contains("calc") -> {
                val calcIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchSafely(calcIntent)) return ActionExecutionResult(true, "Calculator khol raha hoon.")
            }
            clean.contains("calendar") -> {
                val calIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchSafely(calIntent)) return ActionExecutionResult(true, "Calendar khol raha hoon.")
            }
        }

        val knownPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "insta" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "spotify" to "com.spotify.music",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps"
        )

        val pm = context.packageManager
        knownPackages.entries.firstOrNull { (key, _) -> clean.contains(key) }?.value?.let { pkg ->
            pm.getLaunchIntentForPackage(pkg)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launchSafely(intent)) {
                    return ActionExecutionResult(true, "$appQuery khol raha hoon.")
                }
            }
        }

        // Dynamic fuzzy match across installed launcher apps
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val match = resolveInfos.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            label.contains(clean) || clean.contains(label)
        }

        if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launchSafely(launchIntent)) {
                    return ActionExecutionResult(true, "${match.loadLabel(pm)} khol raha hoon.")
                }
            }
        }

        return ActionExecutionResult(false, "Sanni, '$appQuery' app device par install nahi mila.")
    }

    private fun callContactOrNumber(target: String, directCall: Boolean): ActionExecutionResult {
        val clean = target.trim()
        if (clean.isBlank()) return ActionExecutionResult(false, "Kisko call karna hai, Sanni?")

        val resolvedNumber = if (clean.any { it.isDigit() } && clean.count { it.isDigit() } >= 4) {
            clean.filter { it.isDigit() || it == '+' }
        } else {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                return ActionExecutionResult(
                    success = false,
                    feedbackMessage = "Contact '$clean' ka number dhoondhne ke liye Contacts permission zaroori hai.",
                    missingPermission = Manifest.permission.READ_CONTACTS
                )
            }
            lookupContactPhone(clean) ?: return ActionExecutionResult(
                false,
                "Sanni, '$clean' naam ka contact number phonebook mein nahi mila."
            )
        }

        return if (directCall && hasPermission(Manifest.permission.CALL_PHONE)) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$resolvedNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchSafely(callIntent)) {
                ActionExecutionResult(true, "$clean ko call laga raha hoon.")
            } else {
                ActionExecutionResult(false, "Call initiate nahi ho paayi.")
            }
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$resolvedNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchSafely(dialIntent)) {
                ActionExecutionResult(
                    true,
                    if (directCall) "Direct call permission nahi hai, isliye $clean ke liye Dialer khol diya hai."
                    else "$clean ka number Dialer mein khol diya hai."
                )
            } else {
                ActionExecutionResult(false, "Phone dialer nahi khul paaya.")
            }
        }
    }

    private fun prepareWhatsApp(recipient: String, message: String): ActionExecutionResult {
        val cleanRecipient = recipient.trim()
        val encodedMsg = URLEncoder.encode(message, "UTF-8")

        // If a contact name is provided and READ_CONTACTS is granted, resolve phone number for direct chat
        val phone = when {
            cleanRecipient.any { it.isDigit() } && cleanRecipient.count { it.isDigit() } >= 7 ->
                cleanRecipient.filter { it.isDigit() }
            cleanRecipient.isNotBlank() && hasPermission(Manifest.permission.READ_CONTACTS) ->
                lookupContactPhone(cleanRecipient)?.filter { it.isDigit() }
            else -> null
        }

        if (!phone.isNullOrBlank()) {
            val waUrl = "https://api.whatsapp.com/send?phone=$phone&text=$encodedMsg"
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchSafely(waIntent)) {
                return ActionExecutionResult(true, "WhatsApp par $cleanRecipient ki chat message ke saath khol di hai.")
            }
        }

        // Fallback: open WhatsApp share chooser / compose with prefilled text
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launchSafely(shareIntent)) {
            ActionExecutionResult(
                true,
                if (cleanRecipient.isBlank()) "WhatsApp message draft ke saath khol diya hai."
                else "WhatsApp khol diya hai — $cleanRecipient ko select karke send kar dijiye."
            )
        } else {
            ActionExecutionResult(false, "WhatsApp device par install nahi hai ya open nahi ho paaya.")
        }
    }

    private fun prepareSms(recipient: String, message: String): ActionExecutionResult {
        val cleanRecipient = recipient.trim()
        val number = when {
            cleanRecipient.any { it.isDigit() } -> cleanRecipient.filter { it.isDigit() || it == '+' }
            cleanRecipient.isNotBlank() && hasPermission(Manifest.permission.READ_CONTACTS) ->
                lookupContactPhone(cleanRecipient).orEmpty()
            else -> ""
        }

        val uri = if (number.isNotBlank()) Uri.parse("smsto:$number") else Uri.parse("smsto:")
        val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launchSafely(smsIntent)) {
            ActionExecutionResult(true, "SMS app mein message tayyar karke khol diya hai.")
        } else {
            ActionExecutionResult(false, "SMS app available nahi hai.")
        }
    }

    fun toggleFlashlight(mode: String): ActionExecutionResult {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ActionExecutionResult(false, "Camera service available nahi hai.")
        return runCatching {
            val torchCameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionExecutionResult(false, "Is device mein flashlight hardware nahi mila.")

            val enable = when (mode.trim().lowercase()) {
                "on", "true", "enable", "chalu" -> true
                "off", "false", "disable", "band" -> false
                else -> !telemetryMonitor.isTorchActive
            }
            cm.setTorchMode(torchCameraId, enable)
            telemetryMonitor.isTorchActive = enable
            ActionExecutionResult(
                true,
                if (enable) "Ji Sanni, flashlight on kar di hai."
                else "Ji Sanni, flashlight off kar di hai."
            )
        }.getOrElse { e ->
            Log.e(TAG, "Torch toggle failed", e)
            ActionExecutionResult(false, "Flashlight control failed: ${e.localizedMessage}")
        }
    }

    fun adjustVolume(mode: String, valueStr: String = ""): ActionExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionExecutionResult(false, "Audio service available nahi hai.")
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)

        return when (mode.trim().lowercase()) {
            "up", "increase", "badhao", "jyada" -> {
                val next = (current + (max / 6).coerceAtLeast(1)).coerceAtMost(max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, next, AudioManager.FLAG_SHOW_UI)
                ActionExecutionResult(true, "Media volume badha diya hai (${(next * 100) / max}%).")
            }
            "down", "decrease", "kam" -> {
                val next = (current - (max / 6).coerceAtLeast(1)).coerceAtLeast(0)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, next, AudioManager.FLAG_SHOW_UI)
                ActionExecutionResult(true, "Media volume kam kar diya hai (${(next * 100) / max}%).")
            }
            "mute", "silent", "band" -> {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                ActionExecutionResult(true, "Media volume mute kar diya hai.")
            }
            "unmute" -> {
                val target = if (current == 0) (max / 2).coerceAtLeast(1) else current
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                ActionExecutionResult(true, "Media volume unmute kar diya hai.")
            }
            "set" -> {
                val pct = valueStr.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: 50
                val level = ((pct * max) / 100).coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI)
                ActionExecutionResult(true, "Media volume $pct% par set kar diya hai.")
            }
            else -> ActionExecutionResult(false, "Volume command samajh nahi aaya.")
        }
    }

    fun setBrightness(percentStr: String): ActionExecutionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionExecutionResult(
                success = false,
                feedbackMessage = "Brightness change karne ke liye 'Modify system settings' permission chahiye.",
                missingPermission = Settings.ACTION_MANAGE_WRITE_SETTINGS
            )
        }
        val pct = percentStr.filter { it.isDigit() }.toIntOrNull()?.coerceIn(5, 100) ?: 60
        val value = ((pct * 255) / 100).coerceIn(10, 255)
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return ActionExecutionResult(true, "Screen brightness $pct% par set kar di hai.")
    }

    private fun reportDeviceStatus(): ActionExecutionResult {
        val t = telemetryMonitor.readTelemetry()
        val chargingTxt = if (t.isCharging) "aur charge ho raha hai" else ""
        val netTxt = when {
            t.wifiConnected -> "Wi-Fi connected hai"
            t.mobileDataConnected -> "Mobile data active hai"
            else -> "Abhi internet offline hai"
        }
        return ActionExecutionResult(
            true,
            "Sanni, aapke phone ki battery ${t.batteryPercent}% hai $chargingTxt. $netTxt aur media volume ${t.mediaVolumePercent}% par hai."
        )
    }

    private fun setAlarm(timeTarget: String, label: String): ActionExecutionResult {
        val parts = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
            .find(timeTarget)
        var hour = parts?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
        val minute = parts?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        val amPm = parts?.groupValues?.getOrNull(3)?.lowercase().orEmpty()
        if (amPm == "pm" && hour < 12) hour += 12
        if (amPm == "am" && hour == 12) hour = 0

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launchSafely(intent)) {
            ActionExecutionResult(true, "${String.format("%02d:%02d", hour, minute)} ka alarm set kar raha hoon.")
        } else {
            ActionExecutionResult(false, "Clock app alarm support nahi mila.")
        }
    }

    private fun setTimer(durationTarget: String, label: String): ActionExecutionResult {
        val num = durationTarget.filter { it.isDigit() }.toIntOrNull() ?: 60
        val seconds = when {
            durationTarget.contains("hour", true) || durationTarget.contains("ghant", true) -> num * 3600
            durationTarget.contains("min", true) -> num * 60
            else -> num
        }.coerceAtLeast(1)

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launchSafely(intent)) {
            ActionExecutionResult(true, "$seconds seconds ka timer start kar diya hai.")
        } else {
            ActionExecutionResult(false, "Timer app available nahi hai.")
        }
    }

    private fun navigateMaps(destination: String): ActionExecutionResult {
        val clean = destination.trim()
        if (clean.isBlank()) return ActionExecutionResult(false, "Kahan navigate karna hai, Sanni?")
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchSafely(navIntent)) {
            return ActionExecutionResult(true, "$clean ke liye Google Maps navigation shuru kar raha hoon.")
        }
        val geoFallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launchSafely(geoFallback)) {
            ActionExecutionResult(true, "$clean ke liye Maps khol raha hoon.")
        } else {
            ActionExecutionResult(false, "Maps app available nahi hai.")
        }
    }

    private fun openSystemSettings(section: String): ActionExecutionResult {
        val action = when (section.trim().lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "overlay" -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            "notification_listener" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply {
            if (action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {
                data = Uri.parse("package:${context.packageName}")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launchSafely(intent)) {
            ActionExecutionResult(true, "System settings khol raha hoon.")
        } else {
            ActionExecutionResult(false, "Settings page nahi khul paaya.")
        }
    }

    private fun readCurrentScreen(): ActionExecutionResult {
        val service = MarcoAccessibilityService.instance ?: return ActionExecutionResult(
            false,
            "Screen padhne ke liye Marco Accessibility Service ko Setup tab mein enable kijiye."
        )
        val summary = service.readVisibleScreenSummary()
        return ActionExecutionResult(true, summary)
    }

    private fun tapScreenElement(target: String): ActionExecutionResult {
        val service = MarcoAccessibilityService.instance ?: return ActionExecutionResult(
            false,
            "Screen par tap karne ke liye Accessibility Service enable kijiye."
        )
        val tapped = service.tapElementByText(target)
        return if (tapped) {
            ActionExecutionResult(true, "'$target' par tap kar diya hai.")
        } else {
            ActionExecutionResult(false, "Screen par '$target' button nahi mila.")
        }
    }

    private fun scrollScreen(direction: String): ActionExecutionResult {
        val service = MarcoAccessibilityService.instance ?: return ActionExecutionResult(
            false,
            "Scroll karne ke liye Accessibility Service enable kijiye."
        )
        val forward = !direction.contains("up", true) && !direction.contains("back", true) &&
            !direction.contains("upar", true)
        val ok = service.scrollScreen(forward)
        return if (ok) {
            ActionExecutionResult(true, if (forward) "Neeche scroll kar diya." else "Upar scroll kar diya.")
        } else {
            ActionExecutionResult(false, "Current screen scroll nahi ho sakti.")
        }
    }

    private fun enterScreenText(targetField: String, text: String): ActionExecutionResult {
        val service = MarcoAccessibilityService.instance ?: return ActionExecutionResult(
            false,
            "Text type karne ke liye Accessibility Service enable kijiye."
        )
        val ok = service.enterTextIntoField(targetField, text)
        return if (ok) {
            ActionExecutionResult(true, "Field mein text enter kar diya hai.")
        } else {
            ActionExecutionResult(false, "Screen par koi editable text field nahi mila.")
        }
    }

    private fun performGlobalNavigation(actionName: String): ActionExecutionResult {
        val service = MarcoAccessibilityService.instance ?: return ActionExecutionResult(
            false,
            "System navigation ke liye Accessibility Service enable kijiye."
        )
        val ok = service.performNavigation(actionName)
        return if (ok) {
            ActionExecutionResult(true, "Navigation action '$actionName' execute kar diya.")
        } else {
            ActionExecutionResult(false, "Navigation action fail ho gaya.")
        }
    }

    private fun readNotifications(): ActionExecutionResult {
        val listener = MarcoNotificationListenerService.instance ?: return ActionExecutionResult(
            false,
            "Notifications padhne ke liye Setup tab mein Notification Listener enable kijiye."
        )
        val list = listener.readActiveNotifications(5)
        if (list.isEmpty()) {
            return ActionExecutionResult(true, "Sanni, abhi koi naya unread notification nahi hai.")
        }
        val formatted = list.joinToString("\n") { n ->
            "• ${n.appPackage}: ${n.title} — ${n.text}"
        }
        return ActionExecutionResult(true, "Abhi yeh notifications aaye hain:\n$formatted")
    }

    private fun lookupContactPhone(nameQuery: String): String? {
        val clean = nameQuery.trim()
        if (clean.isEmpty()) return null
        return runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$clean%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(1)
                } else null
            }
        }.getOrNull()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchSafely(intent: Intent): Boolean {
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed launching intent $intent", e)
            false
        }
    }

    companion object {
        private const val TAG = "DeviceActionExecutor"
    }
}
