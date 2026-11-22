package com.system.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SysSound.init(this)
        setContent { SystemRoot(tick) }
    }

    override fun onResume() {
        super.onResume()
        tick++
        if (AlarmState.current.value != null) {
            startActivity(Intent(this, AlarmActivity::class.java))
        }
    }

    // While the camera screen is open, volume / Bluetooth shutter keys act as the shutter.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = CameraKeys.onKey
        val shutter = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> true
            else -> false
        }
        if (handler != null && shutter) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handler()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun SystemRoot(tick: Int) {
    var ready by remember { mutableStateOf(false) }
    if (ready) QuestHome() else PermissionFlow(tick) { ready = true }
}
