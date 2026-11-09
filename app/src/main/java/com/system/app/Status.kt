package com.system.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val XP_PER_LEVEL = 300

data class PlayerData(val xp: Int, val str: Int, val intel: Int, val vit: Int) {
    val level: Int get() = xp / XP_PER_LEVEL + 1
    val xpInLevel: Int get() = xp % XP_PER_LEVEL
    val rank: String
        get() = when {
            level >= 50 -> "S"
            level >= 35 -> "A"
            level >= 20 -> "B"
            level >= 10 -> "C"
            level >= 5 -> "D"
            else -> "E"
        }
    val title: String
        get() = when (rank) {
            "S" -> "MONARCH"
            "A" -> "TOP HUNTER"
            "B" -> "ELITE HUNTER"
            "C" -> "SKILLED HUNTER"
            "D" -> "RISING HUNTER"
            else -> "WEAKEST HUNTER"
        }
}

object PlayerStore {
    private fun prefs(c: Context) = c.getSharedPreferences("system_player", Context.MODE_PRIVATE)

    fun read(c: Context): PlayerData {
        val p = prefs(c)
        return PlayerData(p.getInt("xp", 0), p.getInt("str", 0), p.getInt("intel", 0), p.getInt("vit", 0))
    }

    fun award(c: Context, xp: Int, stat: String, pts: Int): Int {
        val p = prefs(c)
        val e = p.edit().putInt("xp", p.getInt("xp", 0) + xp)
        val key = when (stat) {
            "STR" -> "str"
            "INT" -> "intel"
            "VIT" -> "vit"
            else -> null
        }
        if (key != null && pts > 0) e.putInt(key, p.getInt(key, 0) + pts)
        e.apply()
        return xp
    }

    fun penalty(c: Context, xp: Int): Int {
        val p = prefs(c)
        val cur = p.getInt("xp", 0)
        val lost = minOf(xp, cur)
        p.edit().putInt("xp", cur - lost).apply()
        return -lost
    }
}

private val Panel = Color(0xE60A1730)
private val SoftBlue = Color(0xFFB9D9FF)

@Composable
fun StatusScreen() {
    val p = PlayerStore.read(LocalContext.current)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.fillMaxWidth()
                .shadow(16.dp, RectangleShape, ambientColor = SysBlue, spotColor = SysBlue)
                .background(Panel).border(2.dp, SysBlue).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("[ STATUS ]", color = SysBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LEVEL", color = SoftBlue, fontSize = 12.sp, letterSpacing = 3.sp)
                    Text("${p.level}", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(76.dp).border(3.dp, SysBlue), contentAlignment = Alignment.Center) {
                    Text(p.rank, color = SysBlue, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("RANK ${p.rank}  •  ${p.title}", color = SoftBlue, fontSize = 13.sp, letterSpacing = 2.sp)
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
                "${p.xpInLevel} / $XP_PER_LEVEL XP", color = SoftBlue, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().background(Panel).border(2.dp, SysBlue).padding(20.dp)
        ) {
            Text("[ ATTRIBUTES ]", color = SysBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(12.dp))
            StatRow("STR", "Strength", p.str)
            Spacer(Modifier.height(12.dp))
            StatRow("INT", "Intelligence", p.intel)
            Spacer(Modifier.height(12.dp))
            StatRow("VIT", "Vitality", p.vit)
        }
    }
}

@Composable
private fun StatRow(code: String, name: String, value: Int) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$code  $name", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Text("$value", color = SysBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).border(1.dp, Color(0xFF2A4A78))) {
            Box(
                Modifier.fillMaxWidth((value / 30f).coerceIn(0f, 1f))
                    .fillMaxHeight().background(SysBlue)
            )
        }
    }
}