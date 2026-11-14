package com.system.app

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    private var downX = 0f
    private var downY = 0f
    private var downT = 0L

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

    // System sound on every tap (not while scrolling).
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downT = ev.eventTime
            }
            MotionEvent.ACTION_UP -> {
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (ev.eventTime - downT < 300 &&
                    abs(ev.x - downX) < slop && abs(ev.y - downY) < slop
                ) {
                    SysSound.play(this)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

@Composable
fun SystemRoot(tick: Int) {
    var ready by remember { mutableStateOf(false) }
    if (ready) QuestHome() else PermissionFlow(tick) { ready = true }
}
