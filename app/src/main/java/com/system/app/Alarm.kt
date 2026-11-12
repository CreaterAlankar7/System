package com.system.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

data class Active(
    val questId: Long,
    val title: String,
    val endAt: Long,
    val win: Int,
    val streak: Int,
    val xp: Int
)

object AlarmState {
    val current = MutableStateFlow<Active?>(null)
    @Volatile var activityVisible = false
}

object AlarmScheduler {
    fun schedule(c: Context, q: Quest) {
        set(c, q, "start", q.hour, q.minute, q.id.toInt() * 2)
        set(c, q, "end", q.endHour, q.endMinute, q.id.toInt() * 2 + 1)
    }

    // Next time (h:m) that falls on a day this quest is scheduled for.
    private fun nextTrigger(q: Quest, h: Int, m: Int): Long? {
        val now = System.currentTimeMillis()
        for (off in 0..400) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, off)
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now + 1000) continue
            val d = LocalDate.of(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            if (q.appliesOn(d)) return cal.timeInMillis
        }
        return null
    }

    private fun set(c: Context, q: Quest, kind: String, h: Int, m: Int, req: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
        val at = nextTrigger(q, h, m) ?: return
        val pi = PendingIntent.getBroadcast(
            c, req,
            Intent(c, AlarmReceiver::class.java).putExtra("id", q.id).putExtra("kind", kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (kind == "end") {
            val show = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1)
        val kind = i.getStringExtra("kind") ?: "end"
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val q = AppDb.get(c).dao().byId(id)
                if (q != null) {
                    val today = LocalDate.now()
                    if (q.appliesOn(today)) {
                        if (kind == "start") {
                            SysPopup.post(
                                c, "QUEST INFO",
                                "[Daily Quest: ${q.title} has arrived.]\nReward: +${q.xpReward()} XP",
                                0, "Deadline  " + fmt12(q.endHour, q.endMinute)
                            )
                        } else if (q.lastDoneDay != today.toEpochDay()) {
                            startAlarm(c, q)
                        }
                    }
                    AlarmScheduler.schedule(c, q)
                }
            } finally {
                pr.finish()
            }
        }
    }

    private fun startAlarm(c: Context, q: Quest) {
        val si = Intent(c, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
            .putExtra("id", q.id)
            .putExtra("title", q.title)
            .putExtra("win", q.windowMin)
            .putExtra("streak", q.failStreak)
            .putExtra("xp", q.xpReward())
        ContextCompat.startForegroundService(c, si)
    }

    private fun notifyStart(c: Context, q: Quest) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("system_quests", "System Quests", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = "Quest started: ${q.title}. Submit proof before " + fmt12(q.endHour, q.endMinute) + "."
        val n = NotificationCompat.Builder(c, "system_quests")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("[SYSTEM] NEW QUEST")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(2000 + q.id.toInt(), n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDb.get(c).dao().list().forEach { AlarmScheduler.schedule(c, it) }
            } finally {
                pr.finish()
            }
        }
    }
}
