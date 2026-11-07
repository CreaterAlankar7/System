package com.system.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val SysBlue = Color(0xFF3FA9FF)
val SysRed = Color(0xFFFF3B4D)

@Composable
fun PermissionFlow(tick: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val perms = Perm.values()
    var refresh by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Perm?>(null) }
    var requestedAny by remember { mutableStateOf(false) }
    val denied = remember { mutableStateListOf<Perm>() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pending
        if (!ok && p != null && p !in denied) denied.add(p)
        refresh++
    }

    val current = remember(tick, refresh) { perms.firstOrNull { !it.granted(ctx) } }
    val reason = current?.why ?: "All permissions granted. Welcome, Player."
    var shown by remember { mutableIntStateOf(0) }

    LaunchedEffect(current) {
        if (current != null) requestedAny = true
        shown = 0
        while (shown < reason.length) { delay(25); shown++ }
        if (current == null) {
            if (requestedAny) delay(1200)
            onDone()
        }
    }

    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02050D), Color(0xFF0B2347)))),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "[ SYSTEM ]", color = SysBlue, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 4.sp,
                style = TextStyle(shadow = Shadow(SysBlue, Offset.Zero, 28f))
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                perms.forEach { p ->
                    val done = p.granted(ctx)
                    Box(
                        Modifier.size(12.dp)
                            .border(1.dp, SysBlue, CircleShape)
                            .background(if (done) SysBlue else Color.Transparent, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.fillMaxWidth()
                    .shadow(20.dp, RectangleShape, ambientColor = SysBlue, spotColor = SysBlue)
                    .background(Color(0xE60A1730))
                    .border(2.dp, SysBlue)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (current != null) "▶ PERMISSION REQUIRED" else "▶ SETUP COMPLETE",
                    color = if (current != null) SysRed else SysBlue,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    modifier = Modifier.alpha(blink)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    current?.title ?: "WELCOME, PLAYER", color = Color.White,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    reason.take(shown), color = Color(0xFFB9D9FF), fontSize = 15.sp,
                    textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.heightIn(min = 64.dp)
                )
                if (current != null) {
                    val isDenied = current in denied
                    val tone = if (isDenied) SysRed else SysBlue
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .border(2.dp, tone)
                            .clickable {
                                val rt = current.runtime
                                if (rt != null && !isDenied) {
                                    pending = current
                                    launcher.launch(rt)
                                } else {
                                    current.openSettings(ctx)
                                }
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isDenied) "[ OPEN SETTINGS ]" else "[ ALLOW ]",
                            color = tone, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                        )
                    }
                    if (isDenied) {
                        Spacer(Modifier.height(10.dp))
                        Text("You cannot proceed without this.", color = SysRed, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}