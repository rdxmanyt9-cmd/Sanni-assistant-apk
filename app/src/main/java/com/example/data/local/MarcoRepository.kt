package com.example.data.local

import com.example.core.ChatMessage
import com.example.core.MarcoSettings
import com.example.core.MemoryPreference
import com.example.core.MessageSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MarcoRepository(
    private val database: MarcoDatabase,
    private val settingsStore: MarcoSettingsStore
) {
    private val conversationDao = database.conversationDao()
    private val memoryDao = database.memoryDao()

    val messagesFlow: Flow<List<ChatMessage>> = conversationDao.observeMessages().map { list ->
        list.map { entity ->
            ChatMessage(
                id = entity.id,
                sender = runCatching { MessageSender.valueOf(entity.sender) }.getOrDefault(MessageSender.MARCO),
                text = entity.text,
                actionTag = entity.actionTag,
                webSources = entity.webSourcesJoined.split("|").filter { it.isNotBlank() },
                timestamp = entity.timestamp
            )
        }
    }

    val memoriesFlow: Flow<List<MemoryPreference>> = memoryDao.observeMemories().map { list ->
        list.map { entity ->
            MemoryPreference(
                id = entity.id,
                key = entity.memoryKey,
                value = entity.memoryValue,
                createdAt = entity.createdAt
            )
        }
    }

    val settingsFlow: Flow<MarcoSettings> = settingsStore.settingsFlow

    suspend fun getCurrentSettings(): MarcoSettings = settingsStore.getCurrentSettings()

    suspend fun updateSettings(transform: (MarcoSettings) -> MarcoSettings) {
        settingsStore.updateSettings(transform)
    }

    suspend fun addMessage(
        sender: MessageSender,
        text: String,
        actionTag: String = "",
        webSources: List<String> = emptyList()
    ) {
        val settings = settingsStore.getCurrentSettings()
        if (!settings.saveHistoryEnabled && sender != MessageSender.SYSTEM) return
        conversationDao.insertMessage(
            ConversationMessageEntity(
                sender = sender.name,
                text = text,
                actionTag = actionTag,
                webSourcesJoined = webSources.joinToString("|"),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getRecentHistoryForPrompt(limit: Int = 10): List<ChatMessage> {
        return conversationDao.getRecentMessages(limit).reversed().map { entity ->
            ChatMessage(
                id = entity.id,
                sender = runCatching { MessageSender.valueOf(entity.sender) }.getOrDefault(MessageSender.MARCO),
                text = entity.text,
                actionTag = entity.actionTag,
                timestamp = entity.timestamp
            )
        }
    }

    suspend fun clearConversationHistory() {
        conversationDao.clearAllMessages()
    }

    suspend fun saveMemory(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        memoryDao.deleteByKey(key.trim())
        memoryDao.insertMemory(
            MemoryPreferenceEntity(
                memoryKey = key.trim(),
                memoryValue = value.trim()
            )
        )
    }

    suspend fun getAllMemoriesOnce(): List<MemoryPreference> {
        return memoryDao.getAllMemoriesOnce().map {
            MemoryPreference(it.id, it.memoryKey, it.memoryValue, it.createdAt)
        }
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun clearAllMemories() {
        memoryDao.clearAllMemories()
    }
}
