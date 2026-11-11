package com.system.app

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AlarmScreen { finish() } }
    }

    override fun onStart() {
        super.onStart()
        AlarmState.activityVisible = true
    }

    override fun onStop() {
        AlarmState.activityVisible = false
        super.onStop()
    }
}

@Composable
fun AlarmScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val active by AlarmState.current.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    LaunchedEffect(active) { if (active == null) onClose() }
    val a = active
    val left = if (a == null) 0L else maxOf(0L, a.endAt - now) / 1000
    val blink by rememberInfiniteTransition(label = "b").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "a"
    )

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0004), Color(0xFF4A0010)))),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "[ WARNING ]", color = SysRed, fontSize = 32.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 4.sp,
                modifier = Modifier.alpha(blink)
            )
            Spacer(Modifier.height(10.dp))
            Text("QUEST DEADLINE PASSED", color = Color.White, fontSize = 14.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.fillMaxWidth()
                    .border(2.dp, SysRed)
                    .background(Color(0xE6200008))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    a?.title ?: "", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "%02d:%02d".format(left / 60, left % 60), color = SysRed,
                    fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                Text("Deadline missed. Penalty incoming.", color = Color(0xFFFFB3BA), fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
            SysBtn("[ PENALTY QUEST ]", SysRed, Modifier.fillMaxWidth()) {
                Toast.makeText(ctx, "Penalty quest: coming soon", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
