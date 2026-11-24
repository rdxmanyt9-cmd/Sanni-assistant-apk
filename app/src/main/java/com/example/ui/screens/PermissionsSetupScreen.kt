package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.DeviceTelemetry
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AmberConfirm
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.CyanTealAccent
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GlassBorderEmerald
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PermissionsSetupScreen(
    telemetry: DeviceTelemetry,
    onRefreshTelemetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val singlePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onRefreshTelemetry()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF04140E), AmoledBlack)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.Center),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Permissions & Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Marco sirf zaroorat padne par permissions maangta hai. Har permission ka clear reason neeche diya gaya hai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.Mic,
                    title = "Microphone (RECORD_AUDIO)",
                    description = "Sanni ki Hindi, Hinglish aur English voice commands sunne ke liye zaroori hai.",
                    isGranted = telemetry.micGranted,
                    actionLabel = "Grant Mic",
                    testTag = "grant_mic_button",
                    onAction = { singlePermLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionCard(
                        icon = Icons.Filled.Notifications,
                        title = "Notifications (POST_NOTIFICATIONS)",
                        description = "Background Voice Service aur Floating Orb ke Stop/Pause controls dikhane ke liye (Android 13+).",
                        isGranted = telemetry.notificationsGranted,
                        actionLabel = "Allow Notify",
                        testTag = "grant_notifications_button",
                        onAction = { singlePermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.BubbleChart,
                    title = "Floating Orb Overlay (SYSTEM_ALERT_WINDOW)",
                    description = "Doosre apps ke upar movable green Marco orb aur quick mic shortcut dikhane ke liye.",
                    isGranted = telemetry.overlayGranted,
                    actionLabel = "Enable Overlay",
                    testTag = "grant_overlay_button",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.AccessibilityNew,
                    title = "Marco Accessibility Service",
                    description = "Explicit permission ke baad hi screen text padhne, buttons tap karne, scroll aur text enter karne ke liye. Marco kabhi bhi chुपke se screen monitor nahi karta.",
                    isGranted = telemetry.accessibilityEnabled,
                    actionLabel = "Open Accessibility",
                    testTag = "grant_accessibility_button",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Notification Listener (Optional)",
                    description = "Sirf tab notifications padhne ke liye jab Sanni khud poochein 'Mere notifications padho'.",
                    isGranted = telemetry.notificationListenerEnabled,
                    actionLabel = "Enable Reader",
                    testTag = "grant_notification_listener_button",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.Contacts,
                    title = "Contacts (READ_CONTACTS • On-Demand)",
                    description = "Naam bolkar call ya WhatsApp chat open karne ke liye (jaise 'Mummy ko call karo').",
                    isGranted = telemetry.contactsGranted,
                    actionLabel = "Grant Contacts",
                    testTag = "grant_contacts_button",
                    onAction = { singlePermLauncher.launch(Manifest.permission.READ_CONTACTS) }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.Call,
                    title = "Direct Calling (CALL_PHONE • Optional)",
                    description = "Confirmation ke baad seedha call connect karne ke liye. Iske bina bhi Dialer safely khulta hai.",
                    isGranted = telemetry.callPhoneGranted,
                    actionLabel = "Grant Call",
                    testTag = "grant_call_button",
                    onAction = { singlePermLauncher.launch(Manifest.permission.CALL_PHONE) }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.CameraAlt,
                    title = "Camera & Torch (CAMERA)",
                    description = "Camera2 API ke zariye flashlight aur camera actions reliably chalane ke liye.",
                    isGranted = telemetry.cameraGranted,
                    actionLabel = "Grant Camera",
                    testTag = "grant_camera_button",
                    onAction = { singlePermLauncher.launch(Manifest.permission.CAMERA) }
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Filled.Tune,
                    title = "System Brightness (WRITE_SETTINGS)",
                    description = "Voice se screen brightness kam ya zyada karne ke liye.",
                    isGranted = telemetry.writeSettingsGranted,
                    actionLabel = "Allow Write",
                    testTag = "grant_write_settings_button",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CyanTealAccent.copy(alpha = 0.4f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.BatteryAlert,
                            contentDescription = null,
                            tint = CyanTealAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android 12–16 Background & Battery Guide",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Android 12–16 par background microphone sirf active Foreground Service notification ke saath hi allowed hota hai.\n" +
                            "• Kuch OEMs (Xiaomi/Samsung/Realme) battery saver mein background mic band kar dete hain — agar wake phrase ruk jaaye, to Floating Orb ya Mic button manual fallback ki tarah hamesha turant kaam karega.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }.onFailure {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        border = BorderStroke(1.dp, CyanTealAccent.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (telemetry.batteryOptimizationIgnored) "Battery Unrestricted ✓"
                            else "Optimize Battery Settings",
                            color = CyanTealAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isGranted) GlassBorderEmerald else AmberConfirm.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) EmeraldBright else AmberConfirm,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isGranted) EmeraldContainer else Color(0xFF281E05),
                border = BorderStroke(1.dp, if (isGranted) GlassBorderEmerald else AmberConfirm.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGranted) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldBright,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isGranted) "AUTHORIZED" else "NOT GRANTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGranted) EmeraldBright else AmberConfirm
                    )
                }
            }

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) Color(0xFF10241C) else EmeraldPrimary,
                    contentColor = if (isGranted) EmeraldBright else AmoledBlack
                ),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(if (isGranted) "Manage" else actionLabel)
            }
        }
    }
}
