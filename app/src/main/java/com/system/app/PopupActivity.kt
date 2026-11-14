package com.system.app

import android.app.NotificationManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class PopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        SysSound.play(this)
        val title = intent.getStringExtra("title") ?: "NOTIFICATION"
        val body = intent.getStringExtra("body") ?: ""
        val kind = intent.getIntExtra("kind", 0)
        val big = intent.getStringExtra("big")
        runCatching {
            getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("nid", -1))
        }
        setContent {
            LaunchedEffect(Unit) {
                delay(7000)
                finish()
            }
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF02050D), Color(0xFF0B2347))))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                NotificationCard(title, body, kind, big) { finish() }
            }
        }
    }
}
