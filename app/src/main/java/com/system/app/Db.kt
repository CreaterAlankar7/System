package com.system.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "quests")
data class Quest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val type: String = "DAILY",
    val windowMin: Int = 10,
    val failStreak: Int = 0
)

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests ORDER BY hour, minute")
    fun all(): Flow<List<Quest>>

    @Insert
    suspend fun insert(q: Quest): Long
}

@Database(entities = [Quest::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): QuestDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "system.db")
                .build().also { inst = it }
        }
    }
}