package com.system.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmService : Service() {
    companion object {
        const val ACTION_START = "com.system.app.START"
        const val ACTION_PROOF = "com.system.app.PROOF"
        private const val CH_ALARM = "system_alarm"
        private const val CH_RESULT = "system_result"
        private const val NOTIF_ID = 42
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var timer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "System Alarm", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "System Results", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PROOF) {
            finish(true)
            return START_NOT_STICKY
        }
        val id = intent?.getLongExtra("id", -1) ?: -1
        val title = intent?.getStringExtra("title") ?: "Quest"
        val win = intent?.getIntExtra("win", 10) ?: 10
        val streak = intent?.getIntExtra("streak", 0) ?: 0
        val running = AlarmState.current.value
        if (running != null) {
            startFg(alertNote(running.title))
            return START_NOT_STICKY
        }
        AlarmState.current.value =
            Active(id, title, System.currentTimeMillis() + win * 60_000L, win, streak)
        startFg(alertNote(title))
        ring()
        timer = scope.launch {
            delay(win * 60_000L)
            finish(false)
        }
        return START_NOT_STICKY
    }

    private fun startFg(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun alertNote(title: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("[ WARNING ] Quest time!")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
    }

    private fun ring() {
        runCatching {
            val am = getSystemService(AudioManager::class.java)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
            )
        }
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
                start()
            }
        }
        runCatching {
            getSystemService(Vibrator::class.java)
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
        }
    }

    private fun stopRing() {
        runCatching { player?.stop(); player?.release() }
        player = null
        runCatching { getSystemService(Vibrator::class.java).cancel() }
    }

    private fun finish(success: Boolean) {
        timer?.cancel()
        stopRing()
        val a = AlarmState.current.value
        AlarmState.current.value = null
        if (a == null) {
            stopSelf()
            return
        }
        val streak = if (success) 0 else a.streak + 1
        val win = if (!success && streak % 3 == 0) minOf(30, a.win + 5) else a.win
        scope.launch {
           withContext(Dispatchers.IO) {
           val dao = AppDb.get(this@AlarmService).dao()
                if (success) {
                    dao.markDone(a.questId, java.time.LocalDate.now().toEpochDay())
                } else {
                    dao.setStreak(a.questId, streak, win)
                }
            }
            notifyResult(a, success, streak, win)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notifyResult(a: Active, ok: Boolean, streak: Int, win: Int) {
        val text = if (ok) {
            "Quest cleared: ${a.title}. Well done, Player."
        } else {
            "Quest failed: ${a.title}. Fail streak: $streak." +
                (if (win > a.win) " Alarm window increased to $win min." else "")
        }
        val n = NotificationCompat.Builder(this, CH_RESULT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (ok) "[SYSTEM] QUEST CLEARED" else "[SYSTEM] QUEST FAILED")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(1000 + a.questId.toInt(), n)
    }

    override fun onDestroy() {
        stopRing()
        scope.cancel()
        super.onDestroy()
    }
}