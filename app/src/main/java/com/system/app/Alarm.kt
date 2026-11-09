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
    val streak: Int
)

object AlarmState {
    val current = MutableStateFlow<Active?>(null)
}

object AlarmScheduler {
    fun schedule(c: Context, q: Quest) {
        set(c, q, "start", q.hour, q.minute, q.id.toInt() * 2)
        set(c, q, "end", q.endHour, q.endMinute, q.id.toInt() * 2 + 1)
    }

    private fun set(c: Context, q: Quest, kind: String, h: Int, m: Int, req: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pi = PendingIntent.getBroadcast(
            c, req,
            Intent(c, AlarmReceiver::class.java).putExtra("id", q.id).putExtra("kind", kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
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
                    AlarmScheduler.schedule(c, q)
                    if (kind == "start") {
                        notifyStart(c, q)
                    } else if (q.lastDoneDay != LocalDate.now().toEpochDay()) {
                        startAlarm(c, q)
                    }
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
        val text = "Quest started: ${q.title}. Submit proof before " +
            "%02d:%02d.".format(q.endHour, q.endMinute)
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