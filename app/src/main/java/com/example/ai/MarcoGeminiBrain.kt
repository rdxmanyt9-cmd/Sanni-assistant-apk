package com.example.ai

import android.util.Log
import com.example.core.ChatMessage
import com.example.core.DeviceTelemetry
import com.example.core.MarcoAction
import com.example.core.MarcoActionType
import com.example.core.MarcoBrainResponse
import com.example.core.MarcoLanguageMode
import com.example.core.MarcoSettings
import com.example.core.MemoryPreference
import com.example.core.MessageSender
import com.example.data.network.WebSearchService
import com.example.service.MarcoAccessibilityService
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class MarcoGeminiBrain(
    private val webSearchService: WebSearchService
) {

    private val actionItemSchema = Schema.obj(
        properties = mapOf(
            "type" to Schema.enumeration(
                values = MarcoActionType.entries.map { it.name },
                description = "Exact action type to execute on Android."
            ),
            "target" to Schema.string(
                description = "Primary target: app name, contact name, phone number, on/off/up/down/mute, brightness %, time HH:mm, destination, settings section, UI element text, memory key, or search query."
            ),
            "payload" to Schema.string(
                description = "Secondary payload: message body, text to type, volume %, timer label, or memory value."
            ),
            "extra" to Schema.string(
                description = "Optional extra metadata."
            )
        ),
        optionalProperties = listOf("target", "payload", "extra")
    )

    private val brainResponseSchema = Schema.obj(
        properties = mapOf(
            "spokenReply" to Schema.string(
                description = "Natural, concise response in Marco's male AI personality for Sanni Kumar, matching language mode (Hindi, Hinglish, or English)."
            ),
            "replyLanguage" to Schema.enumeration(
                values = listOf("hi-IN", "en-IN"),
                description = "Best TTS locale tag for spokenReply."
            ),
            "requiresConfirmation" to Schema.boolean(
                description = "True if action involves sending WhatsApp/SMS, placing a direct call, deleting content, payments, or sensitive screen taps."
            ),
            "confirmationPrompt" to Schema.string(
                description = "Clear confirmation question for Sanni before executing sensitive actions."
            ),
            "actions" to Schema.array(
                items = actionItemSchema,
                description = "Ordered list of Android actions to execute (empty if conversational only)."
            )
        ),
        optionalProperties = listOf("confirmationPrompt")
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.55f
                responseMimeType = "application/json"
                responseSchema = brainResponseSchema
            },
            systemInstruction = content {
                text(SYSTEM_PROMPT)
            }
        )
    }

    private val synthesisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.5f
            },
            systemInstruction = content {
                text(
                    "You are MARCO, a smart, friendly, slightly witty male AI personal assistant for Sanni Kumar. " +
                        "Synthesize live web search results into a crisp, natural spoken response in Hinglish/Hindi/English " +
                        "that directly answers Sanni's question without robotic jargon."
                )
            }
        )
    }

    suspend fun processCommand(
        userText: String,
        history: List<ChatMessage>,
        memories: List<MemoryPreference>,
        telemetry: DeviceTelemetry,
        settings: MarcoSettings
    ): MarcoBrainResponse {
        if (!telemetry.isOnline) {
            return offlineFallback(
                userText = userText,
                telemetry = telemetry,
                reason = "Internet connection abhi offline hai"
            )
        }

        val screenContext = if (telemetry.accessibilityEnabled) {
            MarcoAccessibilityService.instance?.readVisibleScreenSummary().orEmpty()
        } else ""

        val prompt = buildContextPrompt(
            userText = userText,
            history = history,
            memories = memories,
            telemetry = telemetry,
            settings = settings,
            screenContext = screenContext
        )

        return try {
            val response = withTimeout(45_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Empty Gemini response.")
            val parsed = parseStructuredJson(rawJson, settings)

            // Handle live Web Search if Gemini requested WEB_SEARCH and webSearchEnabled is true
            val searchAction = parsed.actions.firstOrNull { it.type == MarcoActionType.WEB_SEARCH }
            if (searchAction != null && settings.webSearchEnabled) {
                val query = searchAction.target.ifBlank { userText }
                val searchResult = webSearchService.searchWeb(query)
                val synthesized = synthesizeSearchAnswer(userText, searchResult.summaryText, settings)
                parsed.copy(
                    spokenReply = synthesized,
                    actions = parsed.actions.filterNot { it.type == MarcoActionType.WEB_SEARCH },
                    webSources = searchResult.sources
                )
            } else {
                parsed
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini request timed out", e)
            offlineFallback(
                userText = userText,
                telemetry = telemetry,
                reason = "Gemini response mein timeout ho gaya (45s)"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini inference failed", e)
            val fallback = offlineFallback(
                userText = userText,
                telemetry = telemetry,
                reason = e.localizedMessage ?: "Gemini API error"
            )
            // If offline parser didn't match a device command, surface real error transparently
            if (fallback.actions.isEmpty()) {
                MarcoBrainResponse(
                    spokenReply = "Sanni, AI server se connect karne mein problem aayi: ${e.localizedMessage ?: "Network/App Check error"}. Main offline device commands abhi bhi chala sakta hoon.",
                    replyLanguage = "hi-IN",
                    requiresConfirmation = false,
                    actions = emptyList(),
                    isOfflineFallback = true
                )
            } else {
                fallback
            }
        }
    }

    suspend fun testConnection(): Result<String> {
        return try {
            val resp = withTimeout(30_000L) {
                synthesisModel.generateContent(
                    "Say a 1-sentence Hinglish greeting confirming Marco's Gemini brain is online for Sanni Kumar."
                )
            }
            val text = resp.text?.trim().orEmpty()
            if (text.isNotBlank()) Result.success(text)
            else Result.failure(IllegalStateException("Gemini returned empty response."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini connection test failed", e)
            Result.failure(e)
        }
    }

    private suspend fun synthesizeSearchAnswer(
        userQuery: String,
        webSnippets: String,
        settings: MarcoSettings
    ): String {
        val langHint = when (settings.languageMode) {
            MarcoLanguageMode.HINDI -> "Reply in natural Hindi/Hinglish."
            MarcoLanguageMode.ENGLISH -> "Reply in natural Indian English."
            MarcoLanguageMode.AUTO_HINGLISH -> "Reply in natural Hinglish/Hindi matching the user's tone."
        }
        return try {
            val resp = withTimeout(30_000L) {
                synthesisModel.generateContent(
                    "Sanni asked: \"$userQuery\"\n$langHint\n\nLive web search results:\n$webSnippets\n\n" +
                        "Give a clear, friendly 2-3 sentence spoken answer for Sanni."
                )
            }
            resp.text?.trim().takeIf { !it.isNullOrBlank() } ?: webSnippets
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Search synthesis fallback: ${e.message}")
            "Yeh mila web par, Sanni: $webSnippets"
        }
    }

    private fun buildContextPrompt(
        userText: String,
        history: List<ChatMessage>,
        memories: List<MemoryPreference>,
        telemetry: DeviceTelemetry,
        settings: MarcoSettings,
        screenContext: String
    ): String {
        val recentHistory = history.takeLast(8).joinToString("\n") { msg ->
            val role = when (msg.sender) {
                MessageSender.USER -> "Sanni"
                MessageSender.MARCO -> "Marco"
                MessageSender.SYSTEM -> "System"
            }
            "$role: ${msg.text}"
        }

        val memoryBlock = if (memories.isEmpty()) "None saved yet."
        else memories.joinToString("\n") { "- ${it.key}: ${it.value}" }

        return buildString {
            appendLine("Owner: Sanni Kumar")
            appendLine("Preferred Language Mode: ${settings.languageMode.displayName}")
            appendLine("Confirm Sensitive Actions Setting: ${settings.confirmSensitiveActions}")
            appendLine("Web Search Enabled: ${settings.webSearchEnabled}")
            appendLine()
            appendLine("Live Device Telemetry:")
            appendLine("- Battery: ${telemetry.batteryPercent}% (charging=${telemetry.isCharging})")
            appendLine("- Network: WiFi=${telemetry.wifiConnected}, MobileData=${telemetry.mobileDataConnected}")
            appendLine("- Media Volume: ${telemetry.mediaVolumePercent}% (muted=${telemetry.isMuted})")
            appendLine("- Flashlight: ${if (telemetry.flashlightOn) "ON" else "OFF"}")
            appendLine("- Permissions: Contacts=${telemetry.contactsGranted}, CallPhone=${telemetry.callPhoneGranted}, Accessibility=${telemetry.accessibilityEnabled}, NotificationListener=${telemetry.notificationListenerEnabled}, Overlay=${telemetry.overlayGranted}")
            if (screenContext.isNotBlank()) {
                appendLine()
                appendLine("Current Screen Context (Authorized Accessibility):")
                appendLine(screenContext)
            }
            appendLine()
            appendLine("User-Approved Memories:")
            appendLine(memoryBlock)
            if (recentHistory.isNotBlank()) {
                appendLine()
                appendLine("Recent Conversation:")
                appendLine(recentHistory)
            }
            appendLine()
            appendLine("Current Command from Sanni:")
            appendLine(userText)
        }
    }

    private fun parseStructuredJson(rawJson: String, settings: MarcoSettings): MarcoBrainResponse {
        val json = JSONObject(rawJson)
        val spokenReply = json.optString("spokenReply").ifBlank {
            "Ji Sanni, main samajh gaya."
        }
        val replyLanguage = json.optString("replyLanguage").ifBlank {
            settings.languageMode.localeTag
        }
        val requiresConfirmation = json.optBoolean("requiresConfirmation", false)
        val confirmationPrompt = json.optString("confirmationPrompt").takeIf { it.isNotBlank() }

        val actionsJson: JSONArray? = json.optJSONArray("actions")
        val actions = mutableListOf<MarcoAction>()
        if (actionsJson != null) {
            for (i in 0 until actionsJson.length()) {
                val obj = actionsJson.optJSONObject(i) ?: continue
                val typeRaw = obj.optString("type")
                val type = runCatching { MarcoActionType.valueOf(typeRaw) }.getOrNull() ?: continue
                if (type == MarcoActionType.NONE) continue
                actions += MarcoAction(
                    type = type,
                    target = obj.optString("target").trim(),
                    payload = obj.optString("payload").trim(),
                    extra = obj.optString("extra").trim()
                )
            }
        }

        val sensitiveTypes = setOf(
            MarcoActionType.CALL_CONTACT,
            MarcoActionType.PREPARE_WHATSAPP,
            MarcoActionType.PREPARE_SMS
        )
        val enforceConfirm = settings.confirmSensitiveActions &&
            (requiresConfirmation || actions.any { it.type in sensitiveTypes })

        return MarcoBrainResponse(
            spokenReply = spokenReply,
            replyLanguage = replyLanguage,
            requiresConfirmation = enforceConfirm && actions.isNotEmpty(),
            confirmationPrompt = confirmationPrompt ?: if (enforceConfirm) spokenReply else null,
            actions = actions
        )
    }

    fun offlineFallback(
        userText: String,
        telemetry: DeviceTelemetry,
        reason: String
    ): MarcoBrainResponse {
        val q = userText.trim().lowercase()

        // Flashlight
        if (q.contains("flashlight") || q.contains("torch")) {
            val mode = if (q.contains("off") || q.contains("band")) "off" else "on"
            return MarcoBrainResponse(
                spokenReply = if (mode == "on") "Ji Sanni, flashlight on kar raha hoon." else "Ji Sanni, flashlight off kar raha hoon.",
                actions = listOf(MarcoAction(MarcoActionType.TOGGLE_FLASHLIGHT, target = mode)),
                isOfflineFallback = true
            )
        }

        // Battery / status
        if (q.contains("battery") || q.contains("status") || q.contains("wifi")) {
            return MarcoBrainResponse(
                spokenReply = "Sanni, aapke phone ki battery ${telemetry.batteryPercent}% hai.",
                actions = listOf(MarcoAction(MarcoActionType.CHECK_DEVICE_STATUS)),
                isOfflineFallback = true
            )
        }

        // Volume
        if (q.contains("volume") || q.contains("awaaz") || q.contains("awaz") || q.contains("mute")) {
            val mode = when {
                q.contains("unmute") -> "unmute"
                q.contains("mute") || q.contains("band") -> "mute"
                q.contains("kam") || q.contains("down") || q.contains("decrease") -> "down"
                else -> "up"
            }
            return MarcoBrainResponse(
                spokenReply = "Ji Sanni, volume adjust kar raha hoon.",
                actions = listOf(MarcoAction(MarcoActionType.ADJUST_VOLUME, target = mode)),
                isOfflineFallback = true
            )
        }

        // Open known apps
        val appKeywords = mapOf(
            "whatsapp" to "WhatsApp",
            "youtube" to "YouTube",
            "instagram" to "Instagram",
            "chrome" to "Chrome",
            "camera" to "Camera",
            "settings" to "Settings",
            "calculator" to "Calculator",
            "calendar" to "Calendar",
            "gmail" to "Gmail",
            "spotify" to "Spotify",
            "maps" to "Maps"
        )
        val matchedEntry = appKeywords.entries.firstOrNull { (k, _) -> q.contains(k) }
        if (matchedEntry != null && (q.contains("open") || q.contains("khol") || q.contains("chala") || q.contains("launch"))) {
            val pretty = matchedEntry.value
            return MarcoBrainResponse(
                spokenReply = "Ji Sanni, $pretty khol raha hoon.",
                actions = listOf(MarcoAction(MarcoActionType.OPEN_APP, target = pretty)),
                isOfflineFallback = true
            )
        }

        // Accessibility screen read/scroll
        if (q.contains("screen") && (q.contains("padh") || q.contains("read"))) {
            return MarcoBrainResponse(
                spokenReply = "Current screen check kar raha hoon, Sanni.",
                actions = listOf(MarcoAction(MarcoActionType.SCREEN_READ)),
                isOfflineFallback = true
            )
        }
        if (q.contains("scroll")) {
            val dir = if (q.contains("up") || q.contains("upar")) "up" else "down"
            return MarcoBrainResponse(
                spokenReply = "Scroll kar raha hoon, Sanni.",
                actions = listOf(MarcoAction(MarcoActionType.SCREEN_SCROLL, target = dir)),
                isOfflineFallback = true
            )
        }

        return MarcoBrainResponse(
            spokenReply = "Sanni, $reason. Online aane par main Gemini AI se saare complex sawaal aur web search solve kar sakta hoon.",
            isOfflineFallback = true
        )
    }

    companion object {
        private const val TAG = "MarcoGeminiBrain"
        private const val MODEL_NAME = "gemini-3.8-flash"

        private const val SYSTEM_PROMPT = """
You are MARCO, a male personal AI assistant owned by Sanni Kumar.
Identity & Personality:
- Name: MARCO (Male AI Assistant)
- Owner: Sanni Kumar
- Tone: Intelligent, friendly, confident, professional, helpful, and slightly humorous. Never robotic.
- Address the owner naturally as "Sanni" or "Sanni ji" when appropriate.
- Primary language: Hindi / Hinglish (natural conversational Roman Hindi + English mix, or Devanagari if user prefers), Secondary: English. Match Sanni's language naturally.
- Keep spokenReply concise (1-3 natural spoken sentences) so TextToSpeech sounds fluid.

Capabilities & Action Rules:
You return structured JSON with `spokenReply`, `replyLanguage` ("hi-IN" or "en-IN"), `requiresConfirmation`, `confirmationPrompt`, and an ordered `actions` list.
Supported Action Types:
1. OPEN_APP: target = app name (e.g. "WhatsApp", "YouTube", "Instagram", "Chrome", "Camera", "Settings", "Calculator", "Calendar", "Gmail", "Spotify", or any installed app).
2. CALL_CONTACT: target = contact name or phone number. ALWAYS set requiresConfirmation=true!
3. DIAL_NUMBER: target = phone number or contact name (opens dialer safely).
4. PREPARE_WHATSAPP: target = recipient name/number, payload = message body.
   - IMPORTANT multi-turn rule: If Sanni says "Mummy ko WhatsApp message bhejo" without specifying the message text yet, DO NOT add PREPARE_WHATSAPP yet; set actions=[] and ask "Kya message bhejna hai, Sanni?"
   - Once message text is known, include PREPARE_WHATSAPP with target & payload, set requiresConfirmation=true, and ask "Message ready hai. Kya main ise WhatsApp par open/send kar doon?"
5. PREPARE_SMS: target = recipient, payload = message body. Set requiresConfirmation=true.
6. TOGGLE_FLASHLIGHT: target = "on" or "off".
7. ADJUST_VOLUME: target = "up", "down", "mute", "unmute", or "set"; payload = percentage ("0"-"100") if "set".
8. SET_BRIGHTNESS: target = percentage "10"-"100".
9. CHECK_DEVICE_STATUS: use when Sanni asks battery %, Wi-Fi, mobile data, or phone status (also quote exact battery/status from telemetry in spokenReply!).
10. SET_ALARM: target = "HH:mm" or "7:30 am", payload = optional alarm label.
11. SET_TIMER: target = duration like "5 minutes" or "30 seconds", payload = optional label.
12. NAVIGATE_MAPS: target = destination name or address.
13. OPEN_SYSTEM_SETTINGS: target = "main", "wifi", "bluetooth", "display", "sound", "battery", "accessibility", "overlay", or "notification_listener".
14. SCREEN_READ: read current visible screen text & buttons via Accessibility Service.
15. SCREEN_TAP: target = visible button text or label. If tapping Send/Pay/Delete/Confirm, set requiresConfirmation=true!
16. SCREEN_SCROLL: target = "down" or "up".
17. SCREEN_INPUT_TEXT: target = field hint (or ""), payload = text to enter.
18. SCREEN_GLOBAL_ACTION: target = "back", "home", "recents", or "notifications".
19. READ_NOTIFICATIONS: read recent notifications via Notification Listener.
20. WEB_SEARCH: target = crisp English/Hindi search query when Sanni asks about current facts, news, weather, cricket, prices, definitions, or live info.
21. SAVE_MEMORY: target = short preference key (e.g. "favorite_music", "home_city"), payload = value when Sanni asks you to remember something.
22. TOGGLE_FLOATING_ORB: target = "on" or "off".

Multi-step tasks: You can return multiple actions in sequence (e.g., OPEN_APP "YouTube" then SCREEN_INPUT_TEXT or NAVIGATE_MAPS).
Safety: NEVER trigger payments, deletions, or message sending without requiresConfirmation=true!
"""
    }
}
