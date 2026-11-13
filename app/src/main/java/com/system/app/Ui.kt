package com.system.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pale = Color(0xFFB9D9FF)
private val PanelDark = Color(0xFA050C1A)
private val Soft2 = Color(0xFFB9D9FF)
private val Dim3 = Color(0xFF5E7FA8)

@Composable
fun NeonRail() {
    Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(12.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, SysBlue.copy(alpha = 0.6f), Color.Transparent))
            )
        )
        Box(
            Modifier.fillMaxWidth().height(3.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, Ice, Color.Transparent))
            )
        )
    }
}

// Dim background + centered popup. onDismiss = null means tapping outside does nothing.
@Composable
fun Modal(onDismiss: (() -> Unit)?, content: @Composable () -> Unit) {
    val none = remember { MutableInteractionSource() }
    val none2 = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color(0xD9000812))
            .clickable(interactionSource = none, indication = null) { onDismiss?.invoke() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.clickable(interactionSource = none2, indication = null) { }) { content() }
    }
}

// Notification-style popup window: neon rails, "!" icon, title box, scrollable content.
@Composable
fun PopupFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        NeonRail()
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth()
                .background(PanelDark).border(1.dp, Pale).padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).border(1.dp, Pale), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(28.dp).border(2.dp, Pale, CircleShape), contentAlignment = Alignment.Center) {
                        Text("!", color = Pale, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.weight(1f).height(46.dp).border(1.dp, Pale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp, style = GlowStyle
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                content = content
            )
        }
        Spacer(Modifier.height(10.dp))
        NeonRail()
    }
}

@Composable
fun NotificationCard(title: String, body: String, kind: Int, big: String?, onOk: () -> Unit) {
    val accent = when (kind) {
        2 -> SysRed
        1 -> Color(0xFF7FE8FF)
        else -> Color.White
    }
    PopupFrame(title) {
        Text(
            body, color = accent, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(), style = GlowStyle
        )
        if (big != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                big, color = accent, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = GlowStyle
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.align(Alignment.CenterHorizontally).border(1.dp, Pale)
                .clickable { onOk() }.padding(horizontal = 30.dp, vertical = 10.dp)
        ) {
            Text("OK", color = Pale, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NavBar(onMenu: () -> Unit, onStatus: () -> Unit, onProfile: () -> Unit) {
    val ctx = LocalContext.current
    var p by remember { mutableStateOf(PlayerStore.read(ctx)) }
    DisposableEffect(Unit) {
        val prefs = ctx.getSharedPreferences("system_player", Context.MODE_PRIVATE)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> p = PlayerStore.read(ctx) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    Row(
        Modifier.fillMaxWidth().border(1.dp, SysBlue.copy(alpha = 0.6f)).background(PanelDark).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).border(1.dp, Pale).clickable { onMenu() },
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { Box(Modifier.width(20.dp).height(2.dp).background(Ice)) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { onStatus() }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("LV ${p.level}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = GlowStyle)
                Spacer(Modifier.width(10.dp))
                Text("RANK ${p.rank}", color = Ice, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${p.xp} XP", color = Color(0xFF7FE8FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).border(1.dp, SysBlue)) {
                Box(
                    Modifier.fillMaxWidth((p.xpInLevel / XP_PER_LEVEL.toFloat()).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(SysBlue, Color(0xFF7FE8FF))))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(44.dp).border(1.dp, Pale, CircleShape).clickable { onProfile() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(Modifier.size(10.dp).background(Ice, CircleShape))
                Box(Modifier.size(20.dp, 9.dp).background(Ice, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)))
            }
        }
    }
}

@Composable
fun StatusPopupContent() {
    val p = PlayerStore.read(LocalContext.current)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LEVEL", color = Soft2, fontSize = 12.sp, letterSpacing = 3.sp)
                Text("${p.level}", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold, style = GlowStyle)
            }
            Box(Modifier.size(76.dp).border(3.dp, SysBlue), contentAlignment = Alignment.Center) {
                Text(p.rank, color = SysBlue, fontSize = 42.sp, fontWeight = FontWeight.Bold, style = GlowStyle)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("RANK ${p.rank}  •  ${p.title}", color = Soft2, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).border(1.dp, SysBlue)) {
            Box(
                Modifier.fillMaxWidth((p.xpInLevel / XP_PER_LEVEL.toFloat()).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(SysBlue, Color(0xFF7FE8FF))))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${p.xpInLevel} / $XP_PER_LEVEL XP", color = Soft2, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(4.dp))
        Text("TOTAL XP: ${p.xp}", color = Dim3, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
