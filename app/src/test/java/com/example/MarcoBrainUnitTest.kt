package com.example

import com.example.ai.MarcoGeminiBrain
import com.example.core.DeviceTelemetry
import com.example.core.MarcoActionType
import com.example.data.network.WebSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarcoBrainUnitTest {

    @Test
    fun offlineFallbackParsesHinglishCommands() {
        val brain = MarcoGeminiBrain(WebSearchService())
        val telemetry = DeviceTelemetry(batteryPercent = 65, isOnline = false)

        val torchResp = brain.offlineFallback("Flashlight on karo", telemetry, "offline")
        assertEquals(MarcoActionType.TOGGLE_FLASHLIGHT, torchResp.actions.first().type)
        assertEquals("on", torchResp.actions.first().target)

        val batteryResp = brain.offlineFallback("Phone ki battery kitni hai?", telemetry, "offline")
        assertEquals(MarcoActionType.CHECK_DEVICE_STATUS, batteryResp.actions.first().type)
        assertTrue(batteryResp.spokenReply.contains("65%"))

        val appResp = brain.offlineFallback("Hey Marco, WhatsApp kholo", telemetry, "offline")
        assertEquals(MarcoActionType.OPEN_APP, appResp.actions.first().type)
        assertEquals("WhatsApp", appResp.actions.first().target)

        val volumeResp = brain.offlineFallback("Volume mute kar do", telemetry, "offline")
        assertEquals(MarcoActionType.ADJUST_VOLUME, volumeResp.actions.first().type)
        assertEquals("mute", volumeResp.actions.first().target)
    }
}
