package com.system.app

import android.content.Intent
import android.os.Bundle
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
}

@Composable
fun SystemRoot(tick: Int) {
    var ready by remember { mutableStateOf(false) }
    if (ready) QuestHome() else PermissionFlow(tick) { ready = true }
}