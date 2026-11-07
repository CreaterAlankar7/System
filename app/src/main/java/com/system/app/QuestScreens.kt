package com.system.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val PanelBg = Color(0xE60A1730)
private val Dim = Color(0xFF5E7FA8)
private val Soft = Color(0xFFB9D9FF)

@Composable
fun SysBtn(
    text: String,
    color: Color = SysBlue,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier.border(2.dp, color).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun Stepper(label: String, value: Int, count: Int, big: Int, onChange: (Int) -> Unit) {
    fun wrap(v: Int) = ((v % count) + count) % count
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Soft, fontSize = 12.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text("%02d".format(value), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SysBtn("-$big") { onChange(wrap(value - big)) }
            SysBtn("-1") { onChange(wrap(value - 1)) }
            SysBtn("+1") { onChange(wrap(value + 1)) }
            SysBtn("+$big") { onChange(wrap(value + big)) }
        }
    }
}

@Composable
fun AddQuest(onSave: (String, Int, Int) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("[ NEW QUEST ]", color = SysBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().border(2.dp, SysBlue).background(PanelBg).padding(14.dp)) {
            if (title.isEmpty()) Text("Quest name (e.g. 10 Pushups)", color = Dim, fontSize = 16.sp)
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(SysBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
        Stepper("HOUR", hour, 24, 3) { hour = it }
        Spacer(Modifier.height(16.dp))
        Stepper("MINUTE", minute, 60, 5) { minute = it }
        Spacer(Modifier.height(20.dp))
        Text(
            "[WARNING] Time is LOCKED after registering. It cannot be changed.",
            color = SysRed, fontSize = 12.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SysBtn("[ CANCEL ]", Dim) { onCancel() }
            SysBtn("[ REGISTER ]", if (title.isBlank()) Dim else SysBlue) {
                if (title.isNotBlank()) onSave(title.trim(), hour, minute)
            }
        }
    }
}

@Composable
fun QuestCard(q: Quest) {
    Row(
        Modifier.fillMaxWidth().border(2.dp, SysBlue).background(PanelBg).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d:%02d".format(q.hour, q.minute), color = SysBlue, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(q.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${q.type}  •  ${q.windowMin} MIN WINDOW", color = Soft, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Text("🔒", fontSize = 18.sp)
    }
}

@Composable
fun QuestHome() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val quests by dao.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02050D), Color(0xFF0B2347))))
    ) {
        if (adding) {
            AddQuest(
                onSave = { t, h, m ->
                    scope.launch { dao.insert(Quest(title = t, hour = h, minute = m)) }
                    adding = false
                },
                onCancel = { adding = false }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("[ SYSTEM ]", color = SysBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("DAILY QUESTS", color = Soft, fontSize = 13.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(16.dp))
                if (quests.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("[SYSTEM]: No quests registered.", color = Dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(quests, key = { it.id }) { QuestCard(it) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SysBtn("[ + NEW QUEST ]", modifier = Modifier.fillMaxWidth()) { adding = true }
            }
        }
    }
}