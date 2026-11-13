package com.system.app

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentLinkedQueue

data class PopupMsg(val title: String, val body: String, val kind: Int, val big: String?)

object SysPopup {
    val queue = ConcurrentLinkedQueue<PopupMsg>()
    @Volatile var busyUntil = 0L
    private const val CH_MSG = "system_popup_msg"

    // kind: 0 = info (white), 1 = gain (cyan), 2 = loss (red)
    fun post(c: Context, title: String, body: String, kind: Int = 0, big: String? = null) {
        val m = PopupMsg(title, body, kind, big)
        queue.add(m)
        val app = c.applicationContext
        try {
            ContextCompat.startForegroundService(app, Intent(app, PopupService::class.java))
        } catch (e: Exception) {
            // Service could not start from the background: show a normal notification instead.
            queue.remove(m)
            notifyFallback(app, m, false)
        }
    }

    // Normal heads-up notification, or (fullScreen) a notification that wakes the screen
    // and opens PopupActivity over the lock screen.
    fun notifyFallback(c: Context, m: PopupMsg, fullScreen: Boolean) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MSG, "System Messages", NotificationManager.IMPORTANCE_HIGH)
        )
        val nid = (System.currentTimeMillis() % 100000).toInt() + 5000
        val text = m.body.replace("\n", " ") + (if (m.big != null) "  " + m.big else "")
        val b = NotificationCompat.Builder(c, CH_MSG)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("[SYSTEM] " + m.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
        if (fullScreen) {
            val pi = PendingIntent.getActivity(
                c, nid,
                Intent(c, PopupActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("title", m.title)
                    .putExtra("body", m.body)
                    .putExtra("kind", m.kind)
                    .putExtra("big", m.big)
                    .putExtra("nid", nid),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.setFullScreenIntent(pi, true).setContentIntent(pi)
        }
        nm.notify(nid, b.build())
    }
}

class PopupService : Service() {
    companion object {
        private const val CH_FG = "system_popup_fg"
        private const val FG_ID = 43
        private const val SHOW_MS = 7000L
        private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var autoHide: Runnable? = null
    private val pumpRun = Runnable { pump() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_FG, "System Popups", NotificationManager.IMPORTANCE_MIN)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        pump()
        return START_NOT_STICKY
    }

    private fun startFg() {
        val n: Notification = NotificationCompat.Builder(this, CH_FG)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("[SYSTEM]")
            .setContentText("Notification")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FG_ID, n)
        }
    }

    private fun pump() {
        if (view != null) return
        val now = System.currentTimeMillis()
        if (AlarmState.current.value != null || now < SysPopup.busyUntil) {
            // Wait until the warning alarm / previous lock-screen popup is over.
            handler.removeCallbacks(pumpRun)
            handler.postDelayed(pumpRun, 500)
            return
        }
        val m = SysPopup.queue.poll()
        if (m == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val dark = !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (dark) {
            // Screen off or locked: wake it and show the popup over the lock screen.
            SysPopup.notifyFallback(this, m, true)
            SysPopup.busyUntil = now + SHOW_MS + 800
            handler.removeCallbacks(pumpRun)
            handler.postDelayed(pumpRun, SHOW_MS + 800)
        } else if (Settings.canDrawOverlays(this) && show(m)) {
            // shown as an overlay over every app
        } else {
            SysPopup.notifyFallback(this, m, false)
            pump()
        }
    }

    private fun show(m: PopupMsg): Boolean {
        return try {
            val v = build(m)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            getSystemService(WindowManager::class.java).addView(v, params)
            view = v
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(200).start()
            runCatching {
                getSystemService(Vibrator::class.java)
                    .vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            val r = Runnable { dismiss() }
            autoHide = r
            handler.postDelayed(r, SHOW_MS)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun dismiss() {
        autoHide?.let { handler.removeCallbacks(it) }
        autoHide = null
        val v = view ?: return
        view = null
        runCatching { getSystemService(WindowManager::class.java).removeView(v) }
        handler.postDelayed(pumpRun, 250)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun outline(stroke: Int, fill: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun glow(t: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(16f, 0f, 0f, 0xFF3FA9FF.toInt())
        }

    private fun rail(): View {
        val box = FrameLayout(this)
        val halo = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x002F8BFF, 0x992F8BFF.toInt(), 0x002F8BFF)
            )
        }
        val core = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x002F8BFF, 0xFFB8E4FF.toInt(), 0x002F8BFF)
            )
        }
        box.addView(halo, FrameLayout.LayoutParams(MATCH, dp(12), Gravity.CENTER))
        box.addView(core, FrameLayout.LayoutParams(MATCH, dp(3), Gravity.CENTER))
        return box
    }

    private fun build(m: PopupMsg): View {
        val pale = 0xFFB9D9FF.toInt()
        val accent = when (m.kind) {
            2 -> 0xFFFF3B4D.toInt()
            1 -> 0xFF7FE8FF.toInt()
            else -> Color.WHITE
        }

        val icon = FrameLayout(this).apply { background = outline(pale) }
        val bang = TextView(this).apply {
            text = "!"
            textSize = 16f
            setTextColor(pale)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), pale)
            }
        }
        icon.addView(bang, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))

        val titleBox = FrameLayout(this).apply { background = outline(pale) }
        val title = glow(m.title, 20f, Color.WHITE, true).apply { letterSpacing = 0.15f }
        titleBox.addView(title, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)))
        header.addView(
            titleBox,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(8) }
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
            background = outline(pale, 0xE6050C1A.toInt())
            isClickable = true
            setOnClickListener { dismiss() }
        }
        panel.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
        val body = glow(m.body, 16f, accent).apply { setLineSpacing(0f, 1.3f) }
        panel.addView(body, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(26) })
        val bigText = m.big
        if (bigText != null) {
            val big = glow(bigText, 30f, accent, true)
            panel.addView(big, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) })
        }
        val ok = TextView(this).apply {
            text = "OK"
            textSize = 14f
            setTextColor(pale)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(10), dp(30), dp(10))
            background = outline(pale)
            setOnClickListener { dismiss() }
        }
        panel.addView(
            ok,
            LinearLayout.LayoutParams(WRAP, WRAP).apply {
                topMargin = dp(24)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(rail(), LinearLayout.LayoutParams(MATCH, dp(12)))
        column.addView(
            panel,
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        )
        column.addView(rail(), LinearLayout.LayoutParams(MATCH, dp(12)))

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xCC000812.toInt())
            isClickable = true
            setPadding(dp(20), 0, dp(20), 0)
        }
        root.addView(column, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        return root
    }

    override fun onDestroy() {
        autoHide?.let { handler.removeCallbacks(it) }
        handler.removeCallbacks(pumpRun)
        view?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        view = null
        super.onDestroy()
    }
}