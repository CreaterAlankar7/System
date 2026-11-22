package com.system.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.Locale

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
    val stat: String = "STR",
    val repeatType: String = "DAILY",
    val daysMask: Int = 127,
    val dateDay: Long = -1L,
    val proofType: String = "AUTO"
)

// One counter inside a quest, e.g. Push-ups [37/100].
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questId: Long,
    val name: String,
    val target: Int,
    val unit: String = "reps",
    val proofType: String = "AUTO",
    val progress: Int = 0,
    val progressDay: Long = -1L,
    val doneDay: Long = -1L
)

fun Goal.progressOn(day: Long): Int = if (progressDay == day) progress else 0
fun Goal.doneOn(day: Long): Boolean = doneDay == day
fun Goal.unitSuffix(): String = if (unit == "reps" || unit.isBlank()) "" else " $unit"

fun Quest.xpReward(): Int =
    ((endHour * 60 + endMinute) - (hour * 60 + minute)).coerceAtLeast(0)

fun Quest.appliesOn(d: LocalDate): Boolean = when (repeatType) {
    "DATE" -> d.toEpochDay() == dateDay
    "DAYS" -> ((daysMask shr (d.dayOfWeek.value - 1)) and 1) == 1
    else -> true
}

fun fmtDate(d: LocalDate): String =
    "${d.dayOfMonth} " +
        d.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).uppercase() +
        " ${d.year}"

fun Quest.scheduleLabel(): String = when (repeatType) {
    "DATE" -> fmtDate(LocalDate.ofEpochDay(dateDay))
    "DAYS" -> listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        .filterIndexed { i, _ -> ((daysMask shr i) and 1) == 1 }
        .joinToString(" ")
    else -> "EVERY DAY"
}

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

    @Insert
    suspend fun insertGoal(g: Goal): Long

    @Query("SELECT * FROM goals")
    fun allGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE questId = :qid")
    suspend fun goalsFor(qid: Long): List<Goal>

    @Query("UPDATE goals SET progress = :p, progressDay = :day WHERE id = :id")
    suspend fun setProgress(id: Long, p: Int, day: Long)

    @Query("UPDATE goals SET doneDay = :day WHERE id = :id")
    suspend fun markGoalDone(id: Long, day: Long)
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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE quests ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'DAILY'")
        db.execSQL("ALTER TABLE quests ADD COLUMN daysMask INTEGER NOT NULL DEFAULT 127")
        db.execSQL("ALTER TABLE quests ADD COLUMN dateDay INTEGER NOT NULL DEFAULT -1")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE quests ADD COLUMN proofType TEXT NOT NULL DEFAULT 'AUTO'")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`questId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`target` INTEGER NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`proofType` TEXT NOT NULL, " +
                "`progress` INTEGER NOT NULL, " +
                "`progressDay` INTEGER NOT NULL, " +
                "`doneDay` INTEGER NOT NULL)"
        )
    }
}

@Database(entities = [Quest::class, Goal::class], version = 7, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): QuestDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "system.db")
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                )
                .build().also { inst = it }
        }
    }
}

// Splits the quest XP equally between its goals (remainder goes to the first goals).
fun Quest.goalXp(goals: List<Goal>): Map<Long, Int> {
    if (goals.isEmpty()) return emptyMap()
    val sorted = goals.sortedBy { it.id }
    val total = xpReward()
    val per = total / sorted.size
    val extra = total % sorted.size
    return sorted.mapIndexed { i, g -> g.id to (per + if (i < extra) 1 else 0) }.toMap()
}
