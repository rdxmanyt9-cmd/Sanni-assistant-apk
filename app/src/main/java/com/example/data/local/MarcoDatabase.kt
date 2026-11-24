package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversation_messages")
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val text: String,
    val actionTag: String = "",
    val webSourcesJoined: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_preferences")
data class MemoryPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryKey: String,
    val memoryValue: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp ASC, id ASC")
    fun observeMessages(): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: ConversationMessageEntity)

    @Query("DELETE FROM conversation_messages")
    suspend fun clearAllMessages()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_preferences ORDER BY createdAt DESC")
    fun observeMemories(): Flow<List<MemoryPreferenceEntity>>

    @Query("SELECT * FROM memory_preferences ORDER BY createdAt DESC")
    suspend fun getAllMemoriesOnce(): List<MemoryPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entity: MemoryPreferenceEntity)

    @Query("DELETE FROM memory_preferences WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memory_preferences WHERE LOWER(memoryKey) = LOWER(:key)")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM memory_preferences")
    suspend fun clearAllMemories()
}

@Database(
    entities = [ConversationMessageEntity::class, MemoryPreferenceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MarcoDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: MarcoDatabase? = null

        fun getInstance(context: Context): MarcoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarcoDatabase::class.java,
                    "marco_ai_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
