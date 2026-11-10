package com.system.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "quests")
data class Quest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val endHour: Int,
    val endMinute: Int,
    val type: String = "DAILY",
    val windowMin: Int = 5,
    val failStreak: Int = 0,
    val lastDoneDay: Long = -1L,
    val stat: String = "STR"
)

fun Quest.xpReward(): Int =
    ((endHour * 60 + endMinute) - (hour * 60 + minute)).coerceAtLeast(0) * 2

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests ORDER BY hour, minute")
    fun all(): Flow<List<Quest>>

    @Query("SELECT * FROM quests")
    suspend fun list(): List<Quest>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun byId(id: Long): Quest?

    @Insert
    suspend fun insert(q: Quest): Long

    @Query("UPDATE quests SET failStreak = :streak, windowMin = :win WHERE id = :id")
    suspend fun setStreak(id: Long, streak: Int, win: Int)

    @Query("UPDATE quests SET lastDoneDay = :day, failStreak = 0 WHERE id = :id")
    suspend fun markDone(id: Long, day: Long)
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE quests ADD COLUMN stat TEXT NOT NULL DEFAULT 'STR'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE quests SET windowMin = 5 WHERE windowMin = 10")
    }
}

@Database(entities = [Quest::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): QuestDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "system.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build().also { inst = it }
        }
    }
}
