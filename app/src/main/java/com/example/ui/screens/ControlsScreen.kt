package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.DeviceTelemetry
import com.example.core.MarcoAction
import com.example.core.MarcoActionType
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AmberConfirm
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.CyanTealAccent
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GlassBorderEmerald
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsScreen(
    telemetry: DeviceTelemetry,
    onRefreshTelemetry: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onAdjustVolume: (String) -> Unit,
    onSetBrightness: (Int) -> Unit,
    onRunAction: (MarcoAction) -> Unit,
    onRunVoiceExample: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var brightnessSlider by remember(telemetry.screenBrightnessPercent) {
        mutableFloatStateOf(telemetry.screenBrightnessPercent.toFloat())
    }

    val quickApps = remember {
        listOf(
            "WhatsApp", "YouTube", "Instagram", "Chrome",
            "Camera", "Settings", "Calculator", "Calendar",
            "Gmail", "Spotify", "Maps"
        )
    }

    val voiceExamples = remember {
        listOf(
            "Hey Marco, WhatsApp kholo." to "Apps",
            "Mummy ko WhatsApp message bhejo." to "Messaging + Confirm",
            "Phone ki battery kitni hai?" to "Telemetry",
            "Flashlight on karo." to "Camera2 Torch",
            "YouTube kholo." to "Apps",
            "7 baje ka alarm lagao." to "AlarmClock",
            "Connaught Place ka rasta dikhao." to "Maps Navigation",
            "Mere notifications padho." to "Notification Reader"
        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Device Command Deck",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Real Android hardware, apps & accessibility control",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onRefreshTelemetry,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("refresh_telemetry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh telemetry",
                            tint = EmeraldBright
                        )
                    }
                }
            }

            // Live Telemetry Matrix
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "LIVE DEVICE TELEMETRY",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryTile(
                            icon = if (telemetry.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                            label = "Battery",
                            value = "${telemetry.batteryPercent}%",
                            highlight = telemetry.batteryPercent > 20,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            icon = Icons.Filled.Wifi,
                            label = "Network",
                            value = when {
                                telemetry.wifiConnected -> "Wi-Fi"
                                telemetry.mobileDataConnected -> "Mobile"
                                else -> "Offline"
                            },
                            highlight = telemetry.isOnline,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "Media",
                            value = if (telemetry.isMuted) "Muted" else "${telemetry.mediaVolumePercent}%",
                            highlight = !telemetry.isMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Hardware Controls (Flashlight, Volume, Brightness)
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "HARDWARE CONTROLS",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onToggleFlashlight,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (telemetry.flashlightOn) EmeraldPrimary else GlassSurfaceElevated,
                                contentColor = if (telemetry.flashlightOn) AmoledBlack else TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("flashlight_toggle")
                        ) {
                            Icon(
                                imageVector = if (telemetry.flashlightOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                contentDescription = "Toggle flashlight",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (telemetry.flashlightOn) "Torch ON" else "Torch OFF")
                        }

                        OutlinedButton(
                            onClick = { onAdjustVolume("down") },
                            border = BorderStroke(1.dp, GlassBorderSubtle),
                            modifier = Modifier.testTag("volume_down_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume down", tint = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = { onAdjustVolume(if (telemetry.isMuted) "unmute" else "mute") },
                            border = BorderStroke(1.dp, GlassBorderSubtle),
                            modifier = Modifier.testTag("volume_mute_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Mute or unmute",
                                tint = if (telemetry.isMuted) AmberConfirm else TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = { onAdjustVolume("up") },
                            border = BorderStroke(1.dp, GlassBorderSubtle),
                            modifier = Modifier.testTag("volume_up_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume up", tint = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Brightness6,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Screen Brightness (${brightnessSlider.toInt()}%)" +
                                if (!telemetry.writeSettingsGranted) " • Tap to grant Write Settings" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Slider(
                        value = brightnessSlider,
                        onValueChange = { brightnessSlider = it },
                        onValueChangeFinished = { onSetBrightness(brightnessSlider.toInt()) },
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldBright,
                            activeTrackColor = EmeraldPrimary,
                            inactiveTrackColor = GlassSurfaceElevated
                        ),
                        modifier = Modifier.testTag("brightness_slider")
                    )
                }
            }

            // Quick App Launchers
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Apps, contentDescription = null, tint = EmeraldBright, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "QUICK APP LAUNCHER",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldBright
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickApps.forEach { appName ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onRunAction(MarcoAction(MarcoActionType.OPEN_APP, target = appName))
                                    }
                                    .testTag("launch_${appName.lowercase()}"),
                                color = GlassSurfaceElevated,
                                border = BorderStroke(1.dp, GlassBorderSubtle)
                            ) {
                                Text(
                                    text = appName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Accessibility Screen Control & Notification Reader
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (telemetry.accessibilityEnabled) GlassBorderEmerald else AmberConfirm.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.AccessibilityNew,
                                contentDescription = null,
                                tint = if (telemetry.accessibilityEnabled) EmeraldBright else AmberConfirm,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "SCREEN & NOTIFICATION CONTROL",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (telemetry.accessibilityEnabled) EmeraldBright else AmberConfirm
                            )
                        }
                        Text(
                            text = if (telemetry.accessibilityEnabled) "ACTIVE" else "SETUP NEEDED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.accessibilityEnabled) EmeraldPrimary else AmberConfirm
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionChip("Read Screen", Icons.Filled.Visibility) {
                            onRunAction(MarcoAction(MarcoActionType.SCREEN_READ))
                        }
                        ActionChip("Scroll Down", Icons.Filled.SwipeDown) {
                            onRunAction(MarcoAction(MarcoActionType.SCREEN_SCROLL, target = "down"))
                        }
                        ActionChip("Scroll Up", Icons.Filled.SwipeUp) {
                            onRunAction(MarcoAction(MarcoActionType.SCREEN_SCROLL, target = "up"))
                        }
                        ActionChip("Back", Icons.AutoMirrored.Filled.ArrowBack) {
                            onRunAction(MarcoAction(MarcoActionType.SCREEN_GLOBAL_ACTION, target = "back"))
                        }
                        ActionChip("Home", Icons.Filled.Home) {
                            onRunAction(MarcoAction(MarcoActionType.SCREEN_GLOBAL_ACTION, target = "home"))
                        }
                        ActionChip("Notifications", Icons.Filled.NotificationsActive) {
                            onRunAction(MarcoAction(MarcoActionType.READ_NOTIFICATIONS))
                        }
                    }
                }
            }

            // Hinglish Voice Command Cookbook
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "TAP TO RUN VOICE EXAMPLES",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanTealAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        voiceExamples.forEach { (phrase, tag) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onRunVoiceExample(phrase) },
                                color = GlassSurfaceElevated,
                                border = BorderStroke(1.dp, GlassBorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "\"$phrase\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldBright
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = GlassSurfaceElevated,
        border = BorderStroke(1.dp, if (highlight) GlassBorderEmerald else GlassBorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlight) EmeraldBright else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = EmeraldContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, GlassBorderEmerald)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = EmeraldBright, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
    }
}
