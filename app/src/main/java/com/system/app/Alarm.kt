package com.system.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, q.hour)
            set(Calendar.MINUTE, q.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pi = PendingIntent.getBroadcast(
            c, q.id.toInt(),
            Intent(c, AlarmReceiver::class.java).putExtra("id", q.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1)
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val q = AppDb.get(c).dao().byId(id)
                if (q != null) {
                    AlarmScheduler.schedule(c, q)
                    val si = Intent(c, AlarmService::class.java)
                        .setAction(AlarmService.ACTION_START)
                        .putExtra("id", q.id)
                        .putExtra("title", q.title)
                        .putExtra("win", q.windowMin)
                        .putExtra("streak", q.failStreak)
                    ContextCompat.startForegroundService(c, si)
                }
            } finally {
                pr.finish()
            }
        }
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