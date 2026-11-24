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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.core.MarcoLanguageMode
import com.example.core.MarcoSettings
import com.example.core.MemoryPreference
import com.example.core.TtsVoiceOption
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AmberConfirm
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanTealAccent
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GlassBorderEmerald
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: MarcoSettings,
    telemetry: DeviceTelemetry,
    voices: List<TtsVoiceOption>,
    memories: List<MemoryPreference>,
    geminiDiagnostics: String?,
    onUpdateSettings: ((MarcoSettings) -> MarcoSettings) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onToggleFloatingOrb: (Boolean) -> Unit,
    onPreviewVoice: () -> Unit,
    onTestGemini: () -> Unit,
    onAddMemory: (String, String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var wakeInput by remember(settings.wakePhrase) { mutableStateOf(settings.wakePhrase) }
    var memKey by remember { mutableStateOf("") }
    var memValue by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = DeepObsidian,
            title = { Text("Clear Conversation History?", color = TextPrimary) },
            text = {
                Text(
                    "Sanni, kya aap Marco ki poori local conversation history delete karna chahte hain?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = Color.White)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = EmeraldBright)
                }
            }
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
                Text(
                    text = "Marco Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Personalized for Sanni Kumar • Saved locally",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // 1. Gemini AI Configuration & Diagnostics
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EmeraldBright, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GEMINI AI BRAIN & SEARCH", style = MaterialTheme.typography.labelMedium, color = EmeraldBright)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Model: gemini-3.8-flash via Firebase AI Logic + App Check (No hardcoded API key in APK).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Live Web Search",
                        subtitle = "Allow Marco to query DuckDuckGo & Wikipedia for live answers",
                        checked = settings.webSearchEnabled,
                        onCheckedChange = { enabled -> onUpdateSettings { it.copy(webSearchEnabled = enabled) } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onTestGemini,
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("test_gemini_button")
                    ) {
                        Text("Test Gemini Connection", color = EmeraldBright)
                    }
                    if (!geminiDiagnostics.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = geminiDiagnostics,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (geminiDiagnostics.startsWith("Connected")) EmeraldBright else AmberConfirm
                        )
                    }
                }
            }

            // 2. Language & Male Voice Settings
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = EmeraldBright, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("LANGUAGE & MALE VOICE", style = MaterialTheme.typography.labelMedium, color = EmeraldBright)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Language Mode", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarcoLanguageMode.entries.forEach { mode ->
                            val selected = settings.languageMode == mode
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onUpdateSettings { it.copy(languageMode = mode) } },
                                color = if (selected) EmeraldContainer else GlassSurfaceElevated,
                                border = BorderStroke(1.dp, if (selected) EmeraldPrimary else GlassBorderSubtle)
                            ) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) EmeraldBright else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Speech Speed (${String.format("%.2f", settings.speechRate)}x)", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Slider(
                        value = settings.speechRate,
                        onValueChange = { rate -> onUpdateSettings { it.copy(speechRate = rate) } },
                        valueRange = 0.7f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldBright, activeTrackColor = EmeraldPrimary)
                    )

                    Text("Male Voice Pitch (${String.format("%.2f", settings.speechPitch)})", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Slider(
                        value = settings.speechPitch,
                        onValueChange = { pitch -> onUpdateSettings { it.copy(speechPitch = pitch) } },
                        valueRange = 0.65f..1.25f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldBright, activeTrackColor = EmeraldPrimary)
                    )

                    if (voices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Installed TTS Voices (Male prioritized)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            voices.take(6).forEach { voice ->
                                val selected = settings.preferredVoiceName == voice.name
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onUpdateSettings { it.copy(preferredVoiceName = voice.name) }
                                        },
                                    color = if (selected) EmeraldContainer else GlassSurfaceElevated,
                                    border = BorderStroke(1.dp, if (selected) EmeraldPrimary else GlassBorderSubtle)
                                ) {
                                    Text(
                                        text = "${voice.locale}${if (voice.isMaleLikely) " • Male" else ""} (${voice.qualityLabel})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) EmeraldBright else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onPreviewVoice,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = AmoledBlack),
                        modifier = Modifier.testTag("preview_voice_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Marco Voice")
                    }
                }
            }

            // 3. Wake Phrase, Background Service & Floating Orb
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("WAKE PHRASE & SERVICES", style = MaterialTheme.typography.labelMedium, color = EmeraldBright)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = wakeInput,
                        onValueChange = { wakeInput = it },
                        label = { Text("Wake Phrase", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = GlassSurface,
                            unfocusedContainerColor = DeepObsidian
                        ),
                        trailingIcon = {
                            TextButton(onClick = {
                                onUpdateSettings { it.copy(wakePhrase = wakeInput.ifBlank { "Hey Marco" }) }
                            }) {
                                Text("Save", color = EmeraldBright)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Background Voice Service",
                        subtitle = if (telemetry.backgroundServiceRunning) "Active foreground notification running" else "Listen for wake phrase in background",
                        checked = telemetry.backgroundServiceRunning || settings.backgroundListeningEnabled,
                        onCheckedChange = onToggleBackgroundService
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "Floating AI Orb Bubble",
                        subtitle = if (telemetry.floatingOrbRunning) "Orb visible over other apps" else "Movable green orb over apps",
                        checked = telemetry.floatingOrbRunning || settings.floatingOrbEnabled,
                        onCheckedChange = onToggleFloatingOrb
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenPermissions,
                        border = BorderStroke(1.dp, GlassBorderEmerald)
                    ) {
                        Text("Open Accessibility, Overlay & Notification Setup", color = EmeraldBright)
                    }
                }
            }

            // 4. User-Approved Memory & Preferences
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = CyanTealAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("SAVED USER PREFERENCES", style = MaterialTheme.typography.labelMedium, color = CyanTealAccent)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = memKey,
                            onValueChange = { memKey = it },
                            placeholder = { Text("Key (e.g. City)", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = GlassBorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        OutlinedTextField(
                            value = memValue,
                            onValueChange = { memValue = it },
                            placeholder = { Text("Value (e.g. Delhi)", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1.1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = GlassBorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        IconButton(
                            onClick = {
                                if (memKey.isNotBlank() && memValue.isNotBlank()) {
                                    onAddMemory(memKey, memValue)
                                    memKey = ""
                                    memValue = ""
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add memory", tint = EmeraldBright)
                        }
                    }

                    if (memories.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Koi saved preference nahi hai. Aap Marco ko bol sakte hain: 'Yaad rakho mujhe Arijit Singh pasand hai.'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        memories.forEach { mem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${mem.key}: ${mem.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteMemory(mem.id) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete memory", tint = CrimsonAlert, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 5. Privacy & Theme Controls
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = EmeraldBright, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("PRIVACY & THEME", style = MaterialTheme.typography.labelMedium, color = EmeraldBright)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = "Confirm Sensitive Actions",
                        subtitle = "Always ask before sending WhatsApp/SMS, calling, or deleting",
                        checked = settings.confirmSensitiveActions,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(confirmSensitiveActions = v) } }
                    )
                    SettingSwitchRow(
                        title = "Save Conversation History",
                        subtitle = "Store recent chat locally for multi-turn context",
                        checked = settings.saveHistoryEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(saveHistoryEnabled = v) } }
                    )
                    SettingSwitchRow(
                        title = "Pure AMOLED Black",
                        subtitle = "Deep #020504 contrast for OLED screens",
                        checked = settings.amoledPureBlack,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(amoledPureBlack = v) } }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Conversation History")
                    }
                }
            }

            // 6. APK Build Reference
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("BUILD INSTRUCTIONS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Debug APK: ./gradlew assembleDebug (or gradle assembleDebug)\n" +
                            "Release APK: Set KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD env vars and run assembleRelease.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmoledBlack,
                checkedTrackColor = EmeraldPrimary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassSurfaceElevated
            )
        )
    }
}
