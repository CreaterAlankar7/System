package com.system.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
        private const val FAIL_PENALTY = 10
        private const val WARN_MS = 15_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focusReq: AudioFocusRequest? = null
    private var timer: Job? = null
    private var overlay: View? = null
    private var tickRunnable: Runnable? = null
    private var blinkAnim: ObjectAnimator? = null

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
        val win = intent?.getIntExtra("win", 5) ?: 5
        val streak = intent?.getIntExtra("streak", 0) ?: 0
        val xp = intent?.getIntExtra("xp", 0) ?: 0
        val running = AlarmState.current.value
        if (running != null) {
            // Another quest hit its deadline while a warning is showing: fail it right away.
            startFg(alertNote(running.title))
            failNow(id, title, streak, win)
            return START_NOT_STICKY
        }
        val end = System.currentTimeMillis() + WARN_MS
        AlarmState.current.value = Active(id, title, end, win, streak, xp)
        startFg(alertNote(title))
        ring()
        timer = scope.launch {
            while (end - System.currentTimeMillis() > 0) {
                if (AlarmState.activityVisible) {
                    if (overlay != null) hideOverlay()
                } else if (overlay == null) {
                    showOverlay()
                }
                delay(1000)
            }
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

    // Used on the lock screen / screen-off case (full-screen intent).
    private fun alertNote(title: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("[ WARNING ] Quest deadline passed!")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
    }

    private fun failNow(id: Long, title: String, streak: Int, win: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDb.get(this@AlarmService).dao().setStreak(id, streak + 1, win)
            }
            Rewards.loss(this@AlarmService, FAIL_PENALTY, "Quest failed: $title")
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun lp(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    // Full-screen red warning drawn over every app (needs "Display over other apps").
    private fun showOverlay() {
        if (overlay != null) return
        val a = AlarmState.current.value ?: return
        if (!Settings.canDrawOverlays(this)) return
        runCatching {
            val red = 0xFFFF3B4D.toInt()
            val warn = label("[ WARNING ]", 34f, red, true)
            val sub = label("QUEST DEADLINE PASSED", 14f, Color.WHITE, false)
            val title = label(a.title, 24f, Color.WHITE, true)
            val clock = label("", 54f, red, true).apply { typeface = Typeface.MONOSPACE }
            val hint = label("Deadline missed. Penalty incoming.", 13f, 0xFFFFB3BA.toInt(), false)
            val btn = label("[ PENALTY QUEST ]", 16f, red, true).apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), red)
                }
                setOnClickListener {
                    Toast.makeText(this@AlarmService, "Penalty quest: coming soon", Toast.LENGTH_SHORT).show()
                }
            }
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xFF1A0004.toInt(), 0xFF4A0010.toInt())
                )
                isClickable = true
                addView(warn, lp(0))
                addView(sub, lp(10))
                addView(title, lp(28))
                addView(clock, lp(12))
                addView(hint, lp(8))
                addView(btn, lp(28))
            }
            val tick = object : Runnable {
                override fun run() {
                    val left = maxOf(0L, a.endAt - System.currentTimeMillis()) / 1000
                    clock.text = "%02d:%02d".format(left / 60, left % 60)
                    handler.postDelayed(this, 500)
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
            getSystemService(WindowManager::class.java).addView(root, params)
            overlay = root
            tickRunnable = tick
            handler.post(tick)
            blinkAnim = ObjectAnimator.ofFloat(warn, "alpha", 0.35f, 1f).apply {
                duration = 500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    private fun hideOverlay() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        blinkAnim?.cancel()
        blinkAnim = null
        overlay?.let { v ->
            runCatching { getSystemService(WindowManager::class.java).removeView(v) }
        }
        overlay = null
    }

    private fun ring() {
        runCatching {
            val am = getSystemService(AudioManager::class.java)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
            )
            // Ask other apps (music/video) to pause while the alarm is active.
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { _ -> }
                .build()
            focusReq = req
            am.requestAudioFocus(req)
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
                setOnPreparedListener { it.start() }
                prepareAsync()
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
        runCatching {
            focusReq?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        }
        focusReq = null
        runCatching { getSystemService(Vibrator::class.java).cancel() }
    }

    private fun finish(success: Boolean) {
        timer?.cancel()
        hideOverlay()
        stopRing()
        val a = AlarmState.current.value
        AlarmState.current.value = null
        if (a == null) {
            stopSelf()
            return
        }
        val streak = if (success) 0 else a.streak + 1
        scope.launch {
            withContext(Dispatchers.IO) {
                val dao = AppDb.get(this@AlarmService).dao()
                if (success) {
                    dao.markDone(a.questId, java.time.LocalDate.now().toEpochDay())
                } else {
                    dao.setStreak(a.questId, streak, a.win)
                }
            }
            if (success) {
                Rewards.gain(this@AlarmService, a.xp / 2, "", 0, "Quest cleared late: ${a.title}")
            } else {
                Rewards.loss(this@AlarmService, FAIL_PENALTY, "Quest failed: ${a.title}")
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notifyResult(a: Active, ok: Boolean, streak: Int, delta: Int) {
        val text = if (ok) {
            "Quest cleared late: ${a.title}. +$delta XP."
        } else {
            "Quest failed: ${a.title}. $delta XP. Fail streak: $streak."
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
        hideOverlay()
        stopRing()
        scope.cancel()
        super.onDestroy()
    }
}
