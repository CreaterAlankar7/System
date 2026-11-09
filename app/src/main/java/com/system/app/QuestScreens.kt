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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

private val PanelBg = Color(0xE60A1730)
private val Dim = Color(0xFF5E7FA8)
private val Soft = Color(0xFFB9D9FF)
private val Cyan = Color(0xFF7FE8FF)

enum class QStatus { UPCOMING, ACTIVE, CLEARED, OVERDUE }

private fun fmt(h: Int, m: Int) = "%02d:%02d".format(h, m)

private fun nowMin(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun statusOf(q: Quest, nowM: Int, today: Long): QStatus = when {
    q.lastDoneDay == today -> QStatus.CLEARED
    nowM < q.hour * 60 + q.minute -> QStatus.UPCOMING
    nowM < q.endHour * 60 + q.endMinute -> QStatus.ACTIVE
    else -> QStatus.OVERDUE
}

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
        Text("%02d".format(value), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
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
fun AddQuest(onSave: (String, Int, Int, Int, Int) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var sh by remember { mutableIntStateOf(7) }
    var sm by remember { mutableIntStateOf(0) }
    var eh by remember { mutableIntStateOf(7) }
    var em by remember { mutableIntStateOf(30) }
    val validTime = eh * 60 + em > sh * 60 + sm
    val ok = title.isNotBlank() && validTime
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
        Spacer(Modifier.height(20.dp))
        Text("[ START TIME ]", color = SysBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Stepper("HOUR", sh, 24, 3) { sh = it }
        Spacer(Modifier.height(10.dp))
        Stepper("MINUTE", sm, 60, 5) { sm = it }
        Spacer(Modifier.height(20.dp))
        Text("[ END TIME / DEADLINE ]", color = SysRed, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Stepper("HOUR", eh, 24, 3) { eh = it }
        Spacer(Modifier.height(10.dp))
        Stepper("MINUTE", em, 60, 5) { em = it }
        Spacer(Modifier.height(20.dp))
        if (!validTime) {
            Text("End time must be after start time.", color = SysRed, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "[WARNING] Both times are LOCKED after registering. Proof not submitted by the deadline triggers the alarm.",
            color = SysRed, fontSize = 12.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SysBtn("[ CANCEL ]", Dim) { onCancel() }
            SysBtn("[ REGISTER ]", if (ok) SysBlue else Dim) {
                if (ok) onSave(title.trim(), sh, sm, eh, em)
            }
        }
    }
}

@Composable
fun QuestCard(q: Quest, st: QStatus, onProof: () -> Unit) {
    val tone = when (st) {
        QStatus.ACTIVE -> SysBlue
        QStatus.CLEARED -> Cyan
        QStatus.OVERDUE -> SysRed
        QStatus.UPCOMING -> Dim
    }
    val label = when (st) {
        QStatus.ACTIVE -> "ACTIVE"
        QStatus.CLEARED -> "CLEARED"
        QStatus.OVERDUE -> "OVERDUE"
        QStatus.UPCOMING -> "UPCOMING"
    }
    Column(Modifier.fillMaxWidth().border(2.dp, tone).background(PanelBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(fmt(q.hour, q.minute), color = tone, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("→ " + fmt(q.endHour, q.endMinute), color = Soft, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(q.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${q.type}  •  $label  •  FAILS ${q.failStreak}", color = tone,
                    fontSize = 11.sp, letterSpacing = 1.sp)
                Text("ALARM WINDOW ${q.windowMin} MIN", color = Dim, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Text("🔒", fontSize = 18.sp)
        }
        if (st == QStatus.ACTIVE) {
            Spacer(Modifier.height(12.dp))
            SysBtn("[ SUBMIT PROOF ]", modifier = Modifier.fillMaxWidth()) { onProof() }
        }
    }
}

@Composable
fun QuestHome() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val quests by dao.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(quests) { quests.forEach { AlarmScheduler.schedule(ctx, it) } }
    var adding by remember { mutableStateOf(false) }
    var proofFor by remember { mutableStateOf<Quest?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var nowM by remember { mutableIntStateOf(nowMin()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowM = nowMin()
            delay(10_000)
        }
    }
    LaunchedEffect(msg) {
        if (msg != null) {
            delay(3500)
            msg = null
        }
    }
    val today = LocalDate.now().toEpochDay()

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02050D), Color(0xFF0B2347))))
    ) {
        val pq = proofFor
        if (pq != null) {
            CameraProof(
                pq.id,
                onCaptured = { _ ->
                    scope.launch { dao.markDone(pq.id, LocalDate.now().toEpochDay()) }
                    proofFor = null
                    msg = "[SYSTEM]: Quest cleared. Well done, Player."
                },
                onCancel = { proofFor = null }
            )
        } else if (adding) {
            AddQuest(
                onSave = { t, a, b, c, d ->
                    scope.launch { dao.insert(Quest(title = t, hour = a, minute = b, endHour = c, endMinute = d)) }
                    adding = false
                },
                onCancel = { adding = false }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("[ SYSTEM ]", color = SysBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("DAILY QUESTS", color = Soft, fontSize = 13.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(12.dp))
                if (msg != null) {
                    Text(
                        msg ?: "", color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().border(2.dp, Cyan).background(PanelBg).padding(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (quests.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("[SYSTEM]: No quests registered.", color = Dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(quests, key = { it.id }) { q ->
                            QuestCard(q, statusOf(q, nowM, today)) { proofFor = q }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SysBtn("[ + NEW QUEST ]", modifier = Modifier.fillMaxWidth()) { adding = true }
            }
        }
    }
}