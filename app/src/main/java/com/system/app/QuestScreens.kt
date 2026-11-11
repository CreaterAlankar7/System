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
import androidx.compose.ui.text.style.TextDecoration
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

enum class QStatus { UPCOMING, ACTIVE, CLEARED, OVERDUE, OFF }

private fun nowMin(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun statusOf(q: Quest, nowM: Int, today: LocalDate): QStatus = when {
    !q.appliesOn(today) -> QStatus.OFF
    q.lastDoneDay == today.toEpochDay() -> QStatus.CLEARED
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
private fun TimeRow(label: String, value: String, tone: Color = SysBlue, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, tone).clickable { onClick() }.padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = tone, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun AddQuest(
    onSave: (String, Int, Int, Int, Int, String, String, Int, Long) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var stat by remember { mutableStateOf("STR") }
    var rtype by remember { mutableStateOf("DAILY") }
    var mask by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var sh by remember { mutableIntStateOf(7) }
    var sm by remember { mutableIntStateOf(0) }
    var eh by remember { mutableIntStateOf(7) }
    var em by remember { mutableIntStateOf(30) }
    var picking by remember { mutableStateOf<String?>(null) }
    val validTime = eh * 60 + em > sh * 60 + sm
    val daysOk = rtype != "DAYS" || mask != 0
    val ok = title.isNotBlank() && validTime && daysOk
    val reward = ((eh * 60 + em) - (sh * 60 + sm)).coerceAtLeast(0) * 2

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SysFrame(Modifier.fillMaxWidth()) {
                QuestInfoHeader("NEW QUEST")
                Spacer(Modifier.height(14.dp))
                Text(
                    "[New Quest: registration]", color = Soft, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().border(1.dp, SysBlue).background(PanelBg).padding(14.dp)) {
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
                Spacer(Modifier.height(16.dp))
                Text("TYPE", color = SysBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("STR", "INT", "VIT").forEach { s ->
                        SysBtn("[ $s ]", if (stat == s) SysBlue else Dim, Modifier.weight(1f)) { stat = s }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("STR: fitness  •  INT: study  •  VIT: habits", color = Dim, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))
                Text("REPEAT", color = SysBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DAILY", "DAYS", "DATE").forEach { k ->
                        SysBtn("[ $k ]", if (rtype == k) SysBlue else Dim, Modifier.weight(1f)) { rtype = k }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("DAILY: every day  •  DAYS: pick weekdays  •  DATE: one date", color = Dim, fontSize = 11.sp)
                if (rtype == "DAYS") {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEachIndexed { i, d ->
                            val on = ((mask shr i) and 1) == 1
                            Box(
                                Modifier.weight(1f)
                                    .border(1.dp, if (on) SysBlue else Dim)
                                    .background(if (on) SysBlue.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable { mask = mask xor (1 shl i) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    d, color = if (on) Color.White else Dim,
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (mask == 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("Pick at least one day.", color = SysRed, fontSize = 12.sp)
                    }
                }
                if (rtype == "DATE") {
                    Spacer(Modifier.height(10.dp))
                    CalendarPicker(date) { date = it }
                    Spacer(Modifier.height(4.dp))
                    Text("SELECTED: " + fmtDate(date), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text("TIME  (tap to change)", color = SysBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                TimeRow("START", fmt12(sh, sm)) { picking = "start" }
                Spacer(Modifier.height(8.dp))
                TimeRow("DEADLINE", fmt12(eh, em), SysRed) { picking = "end" }
                Spacer(Modifier.height(12.dp))
                Text(
                    "REWARD: $reward XP  (2 XP per minute)", color = Cyan,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                if (!validTime) {
                    Spacer(Modifier.height(6.dp))
                    Text("Deadline must be after start time.", color = SysRed, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "WARNING: Time and schedule are LOCKED after registering. Proof not submitted by the deadline triggers the alarm.",
                    color = SysRed, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SysBtn("[ CANCEL ]", Dim, Modifier.weight(1f)) { onCancel() }
                SysBtn("[ REGISTER ]", if (ok) SysBlue else Dim, Modifier.weight(1f)) {
                    if (ok) {
                        onSave(
                            title.trim(), sh, sm, eh, em, stat, rtype,
                            if (rtype == "DAYS") mask else 127,
                            if (rtype == "DATE") date.toEpochDay() else -1L
                        )
                    }
                }
            }
        }
        val which = picking
        if (which != null) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.padding(20.dp)) {
                    if (which == "start") {
                        TimePickerPanel("START TIME", sh, sm, { h, m ->
                            sh = h
                            sm = m
                            picking = null
                        }) { picking = null }
                    } else {
                        TimePickerPanel("DEADLINE", eh, em, { h, m ->
                            eh = h
                            em = m
                            picking = null
                        }) { picking = null }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestRow(q: Quest, st: QStatus, onProof: () -> Unit) {
    val tone = when (st) {
        QStatus.ACTIVE -> SysBlue
        QStatus.CLEARED -> Cyan
        QStatus.OVERDUE -> SysRed
        QStatus.UPCOMING -> Soft
        QStatus.OFF -> Dim
    }
    val label = when (st) {
        QStatus.ACTIVE -> "ACTIVE"
        QStatus.CLEARED -> "CLEARED"
        QStatus.OVERDUE -> "OVERDUE"
        QStatus.UPCOMING -> "UPCOMING"
        QStatus.OFF -> "NOT TODAY"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    q.title, color = if (st == QStatus.OFF) Dim else Color.White,
                    fontSize = 17.sp, style = GlowStyle
                )
                Text(
                    "[" + fmt12(q.hour, q.minute) + " - " + fmt12(q.endHour, q.endMinute) + "]",
                    color = Soft, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (st == QStatus.CLEARED) "[1/1]" else "[0/1]",
                color = tone, fontSize = 15.sp, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(20.dp).border(2.dp, tone), contentAlignment = Alignment.Center) {
                if (st == QStatus.CLEARED) Text("✓", color = tone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${q.scheduleLabel()}  •  ${q.stat}  •  $label  •  FAILS ${q.failStreak}  •  +${q.xpReward()} XP",
            color = tone, fontSize = 10.sp, letterSpacing = 1.sp
        )
        if (st == QStatus.ACTIVE) {
            Spacer(Modifier.height(8.dp))
            SysBtn("[ SUBMIT PROOF ]", modifier = Modifier.fillMaxWidth()) { onProof() }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SysBlue.copy(alpha = 0.25f)))
}

@Composable
fun QuestHome() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val quests by dao.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(quests) { quests.forEach { AlarmScheduler.schedule(ctx, it) } }
    var tab by remember { mutableIntStateOf(0) }
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
            delay(4000)
            msg = null
        }
    }
    val today = LocalDate.now()

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
                    val before = PlayerStore.read(ctx).level
                    PlayerStore.award(ctx, pq.xpReward(), pq.stat, 1)
                    val after = PlayerStore.read(ctx).level
                    proofFor = null
                    msg = "[SYSTEM]: Quest cleared. +${pq.xpReward()} XP, +1 ${pq.stat}." +
                        (if (after > before) "  LEVEL UP! Lv.$after" else "")
                },
                onCancel = { proofFor = null }
            )
        } else if (adding) {
            AddQuest(
                onSave = { t, a, b, c, d, s, rt, dm, dd ->
                    scope.launch {
                        dao.insert(
                            Quest(
                                title = t, hour = a, minute = b, endHour = c, endMinute = d,
                                stat = s, repeatType = rt, daysMask = dm, dateDay = dd
                            )
                        )
                    }
                    adding = false
                },
                onCancel = { adding = false }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SysBtn("[ QUESTS ]", if (tab == 0) SysBlue else Dim, Modifier.weight(1f)) { tab = 0 }
                    SysBtn("[ STATUS ]", if (tab == 1) SysBlue else Dim, Modifier.weight(1f)) { tab = 1 }
                }
                Spacer(Modifier.height(12.dp))
                if (msg != null) {
                    Text(
                        msg ?: "", color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().border(2.dp, Cyan).background(PanelBg).padding(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (tab == 1) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { StatusScreen() }
                } else {
                    SysFrame(Modifier.weight(1f).fillMaxWidth()) {
                        QuestInfoHeader("QUEST INFO")
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (quests.isEmpty()) "[No quests registered.]" else "[Daily Quests have arrived.]",
                            color = Soft, fontSize = 13.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(), style = GlowStyle
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "GOAL", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, textDecoration = TextDecoration.Underline,
                            modifier = Modifier.fillMaxWidth(), style = GlowStyle
                        )
                        Spacer(Modifier.height(4.dp))
                        if (quests.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Register a quest to begin.", color = Dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            LazyColumn(Modifier.weight(1f)) {
                                items(quests, key = { it.id }) { q ->
                                    QuestRow(q, statusOf(q, nowM, today)) { proofFor = q }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "WARNING: Failure to complete the daily quest will result in an appropriate penalty.",
                            color = Ice, fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SysBtn("[ + NEW QUEST ]", modifier = Modifier.fillMaxWidth()) { adding = true }
                }
            }
        }
    }
}
