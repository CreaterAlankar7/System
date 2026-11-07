package com.system.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SystemScreen() }
    }
}

@Composable
fun SystemScreen() {
    val blue = Color(0xFF3FA9FF)
    Box(
        Modifier.fillMaxSize().background(Color(0xFF050B18)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp)
                .border(2.dp, blue)
                .background(Color(0xFF0A1730))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("[ SYSTEM ]", color = blue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Player registered.", color = Color.White, fontSize = 16.sp)
        }
    }
}