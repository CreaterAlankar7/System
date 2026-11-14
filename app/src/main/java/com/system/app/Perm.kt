package com.system.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class Perm(val title: String, val why: String, val runtime: String?) {
    NOTIF("NOTIFICATIONS", "The System must send you quests and warnings.", Manifest.permission.POST_NOTIFICATIONS),
    ALARM("EXACT ALARM", "Quests must trigger at the exact second. No delay allowed.", null),
    FULLSCREEN("FULL-SCREEN ALERT", "The warning alarm must appear even on the lock screen.", null),
    OVERLAY("DISPLAY OVER APPS", "The warning must appear over everything, even while you use your phone.", null),
    CAMERA("CAMERA", "Proof of completion must be captured live.", Manifest.permission.CAMERA),
    BATTERY("UNRESTRICTED BATTERY", "The System must never be shut down in the background.", null);

    fun granted(c: Context): Boolean = when (this) {
        NOTIF -> Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, runtime!!) == PackageManager.PERMISSION_GRANTED
        ALARM -> Build.VERSION.SDK_INT < 31 ||
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        FULLSCREEN -> Build.VERSION.SDK_INT < 34 ||
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
        OVERLAY -> Settings.canDrawOverlays(c)
        CAMERA -> ContextCompat.checkSelfPermission(c, runtime!!) == PackageManager.PERMISSION_GRANTED
        BATTERY -> (c.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(c.packageName)
    }

    fun openSettings(c: Context) {
        val uri = Uri.parse("package:${c.packageName}")
        val intent = when (this) {
            ALARM -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri)
            FULLSCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, uri)
            OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
            BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri)
            NOTIF -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
            CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        }
        try {
            c.startActivity(intent)
        } catch (e: Exception) {
            c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        }
    }
}
