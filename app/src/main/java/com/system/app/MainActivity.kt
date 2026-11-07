package com.system.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SystemRoot(tick) }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

@Composable
fun SystemRoot(tick: Int) {
    var ready by remember { mutableStateOf(false) }
    if (ready) SystemScreen() else PermissionFlow(tick) { ready = true }
}

@Composable
fun SystemScreen() {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF050B18)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp)
                .border(2.dp, SysBlue)
                .background(Color(0xFF0A1730))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("[ SYSTEM ]", color = SysBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Player registered.", color = Color.White, fontSize = 16.sp)
        }
    }
}