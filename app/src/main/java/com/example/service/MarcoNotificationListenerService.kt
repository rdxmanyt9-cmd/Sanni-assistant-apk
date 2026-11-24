package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

data class NotificationSnapshot(
    val appPackage: String,
    val title: String,
    val text: String,
    val postTime: Long
)

class MarcoNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "MarcoNotificationListenerService connected.")
    }

    override fun onListenerDisconnected() {
        if (instance === this) {
            instance = null
        }
        super.onListenerDisconnected()
    }

    fun readActiveNotifications(limit: Int = 6): List<NotificationSnapshot> {
        return runCatching {
            activeNotifications
                ?.filter { sbn ->
                    sbn.packageName != packageName && !sbn.isOngoing
                }
                ?.sortedByDescending { it.postTime }
                ?.take(limit)
                ?.mapNotNull { sbn -> toSnapshot(sbn) }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun toSnapshot(sbn: StatusBarNotification): NotificationSnapshot? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return NotificationSnapshot(
            appPackage = sbn.packageName.substringAfterLast('.'),
            title = title,
            text = text,
            postTime = sbn.postTime
        )
    }

    companion object {
        private const val TAG = "MarcoNotification"

        @Volatile
        var instance: MarcoNotificationListenerService? = null
            private set
    }
}
