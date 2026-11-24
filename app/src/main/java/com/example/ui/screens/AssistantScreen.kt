package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.AssistantStatus
import com.example.core.ChatMessage
import com.example.core.DeviceTelemetry
import com.example.core.MessageSender
import com.example.core.PendingConfirmation
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MarcoGlowingOrb
import com.example.ui.components.VoiceWaveformBar
import com.example.ui.theme.AmberConfirm
import com.example.ui.theme.AmberContainer
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

@Composable
fun AssistantScreen(
    status: AssistantStatus,
    amplitude: Float,
    partialTranscript: String,
    messages: List<ChatMessage>,
    telemetry: DeviceTelemetry,
    pendingConfirmation: PendingConfirmation?,
    onMicClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSendText: (String) -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit,
    onToggleFloatingOrb: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val quickPrompts = remember {
        listOf(
            "WhatsApp kholo",
            "Battery kitni hai?",
            "Flashlight on karo",
            "Mummy ko WhatsApp message bhejo",
            "YouTube kholo",
            "Screen padho"
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF04140E),
                        AmoledBlack,
                        Color(0xFF030A07)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.Center)
                .imePadding()
        ) {
            // Top Futuristic HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    if (telemetry.isOnline) EmeraldPrimary else CrimsonAlert
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MARCO AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Sanni Kumar • ${if (telemetry.isOnline) "Gemini Online" else "Offline Mode"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status is AssistantStatus.Speaking) {
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(onClick = onStopSpeaking)
                                .testTag("stop_speaking_button"),
                            color = EmeraldContainer,
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeOff,
                                    contentDescription = "Stop Marco speaking",
                                    tint = EmeraldBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Mute",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldBright
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onToggleFloatingOrb)
                            .testTag("floating_orb_quick_toggle"),
                        color = if (telemetry.floatingOrbRunning) EmeraldContainer else GlassSurface,
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.floatingOrbRunning) EmeraldPrimary else GlassBorderSubtle
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BubbleChart,
                                contentDescription = "Toggle floating orb",
                                tint = if (telemetry.floatingOrbRunning) EmeraldBright else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (telemetry.floatingOrbRunning) "Orb On" else "Orb",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (telemetry.floatingOrbRunning) EmeraldBright else TextSecondary
                            )
                        }
                    }

                    if (!telemetry.micGranted || !telemetry.accessibilityEnabled) {
                        IconButton(
                            onClick = onNavigateToSetup,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("setup_alert_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = "Open Permissions Setup",
                                tint = AmberConfirm
                            )
                        }
                    }
                }
            }

            // Orb + Waveform Hero Stage
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MarcoGlowingOrb(
                    status = status,
                    amplitude = amplitude,
                    onClick = onMicClick,
                    orbSize = 152.dp,
                    modifier = Modifier.testTag("marco_orb_button")
                )

                val statusLabel = when (status) {
                    AssistantStatus.Idle -> "Tap Orb or Mic • Bolne ke liye ready"
                    AssistantStatus.Listening -> if (partialTranscript.isNotBlank()) "\"$partialTranscript\"" else "Sun raha hoon, Sanni..."
                    AssistantStatus.Thinking -> "Soch raha hoon..."
                    AssistantStatus.Speaking -> "Marco bol raha hai..."
                    AssistantStatus.AwaitingConfirmation -> "Aapki confirmation chahiye, Sanni"
                    is AssistantStatus.Error -> status.message
                }

                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (status) {
                        is AssistantStatus.Error -> CrimsonAlert
                        is AssistantStatus.AwaitingConfirmation -> AmberConfirm
                        is AssistantStatus.Listening -> EmeraldBright
                        is AssistantStatus.Speaking -> CyanTealAccent
                        else -> TextSecondary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )

                VoiceWaveformBar(
                    status = status,
                    amplitude = amplitude,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .height(26.dp)
                )
            }

            // Pending Sensitive Action Confirmation Gate
            AnimatedVisibility(
                visible = pendingConfirmation != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                pendingConfirmation?.let { pending ->
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        borderColor = AmberConfirm.copy(alpha = 0.7f),
                        backgroundColor = AmberContainer
                    ) {
                        Text(
                            text = "SENSITIVE ACTION CONFIRMATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberConfirm
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pending.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onConfirmAction,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary,
                                    contentColor = AmoledBlack
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("confirm_action_button")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Confirm")
                            }
                            OutlinedButton(
                                onClick = onCancelAction,
                                border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cancel_action_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // Offline Banner
            if (!telemetry.isOnline) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F1606),
                    border = BorderStroke(1.dp, AmberConfirm.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WifiOff,
                            contentDescription = "Offline mode",
                            tint = AmberConfirm,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline mode active — local device commands ready.",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberConfirm
                        )
                    }
                }
            }

            // Conversation Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { "${it.id}_${it.timestamp}" }) { msg ->
                    ChatBubbleRow(message = msg)
                }
            }

            // Quick Hinglish / Hindi Command Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPrompts) { prompt ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSendText(prompt) },
                        color = GlassSurfaceElevated,
                        border = BorderStroke(1.dp, GlassBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Bottom Input & Glowing Mic Dock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Marco ko likho ya bolo (Hindi / Hinglish / English)...",
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GlassSurface,
                        unfocusedContainerColor = DeepObsidian,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = EmeraldBright
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                onSendText(inputText)
                                inputText = ""
                            }
                        }
                    ),
                    trailingIcon = {
                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onSendText(inputText)
                                    inputText = ""
                                },
                                modifier = Modifier.testTag("send_command_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send command",
                                    tint = EmeraldBright
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input")
                )

                val isListening = status is AssistantStatus.Listening
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMicClick)
                        .border(
                            width = 1.5.dp,
                            color = if (isListening) CrimsonAlert else EmeraldBright,
                            shape = CircleShape
                        )
                        .testTag("mic_button"),
                    shape = CircleShape,
                    color = if (isListening) CrimsonAlert.copy(alpha = 0.22f) else EmeraldContainer,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Start listening",
                            tint = if (isListening) CrimsonAlert else EmeraldBright,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) EmeraldContainer else GlassSurface,
            border = BorderStroke(
                1.dp,
                if (isUser) EmeraldPrimary.copy(alpha = 0.55f) else GlassBorderEmerald
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = if (isUser) "SANNI" else "MARCO",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) EmeraldBright else CyanTealAccent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                if (message.actionTag.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmoledBlack.copy(alpha = 0.55f),
                        border = BorderStroke(0.8.dp, GlassBorderSubtle)
                    ) {
                        Text(
                            text = "ACTION: ${message.actionTag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (message.webSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sources: ${message.webSources.joinToString(" • ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
