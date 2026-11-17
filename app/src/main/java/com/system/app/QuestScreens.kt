package com.system.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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

data class GoalDraft(val name: String, val target: Int, val unit: String)

private class Preset(
    val label: String,
    val title: String,
    val stat: String,
    val sh: Int,
    val sm: Int,
    val eh: Int,
    val em: Int,
    val proof: String,
    val goals: List<GoalDraft>
)

private val PRESETS = listOf(
    Preset(
        "WORKOUT", "Strength Training", "STR", 6, 0, 8, 0, "AUTO",
        listOf(
            GoalDraft("Push-ups", 100, "reps"),
            GoalDraft("Sit-ups", 100, "reps"),
            GoalDraft("Squats", 100, "reps"),
            GoalDraft("Running", 10, "km")
        )
    ),
    Preset(
        "STUDY", "Study Session", "INT", 19, 0, 22, 0, "AUTO",
        listOf(
            GoalDraft("Study", 120, "min"),
            GoalDraft("Revision", 10, "pages"),
            GoalDraft("Solve problems", 20, "questions")
        )
    ),
    Preset("SLEEP", "Sleep On Time", "VIT", 22, 30, 23, 0, "ANY", emptyList())
)

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
private fun SysField(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    onChange: (String) -> Unit
) {
    Box(modifier.border(1.dp, SysBlue).background(PanelBg).padding(12.dp)) {
        if (value.isEmpty()) Text(hint, color = Dim, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(SysBlue),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (number) KeyboardType.Number else KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Quest registration shown as a popup window (not full screen).
@Composable
fun AddQuest(
    onSave: (String, Int, Int, Int, Int, String, String, Int, Long, String, List<GoalDraft>) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var stat by remember { mutableStateOf("STR") }
    var rtype by remember { mutableStateOf("DAILY") }
    var proof by remember { mutableStateOf("AUTO") }
    var mask by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var sh by remember { mutableIntStateOf(7) }
    var sm by remember { mutableIntStateOf(0) }
    var eh by remember { mutableIntStateOf(7) }
    var em by remember { mutableIntStateOf(30) }
    var picking by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    val goals = remember { mutableStateListOf<GoalDraft>() }
    var gName by remember { mutableStateOf("") }
    var gTarget by remember { mutableStateOf("") }
    var gUnit by remember { mutableStateOf("reps") }
    val validTime = eh * 60 + em > sh * 60 + sm
    val daysOk = rtype != "DAYS" || mask != 0
    val ok = title.isNotBlank() && validTime && daysOk
    val reward = ((eh * 60 + em) - (sh * 60 + sm)).coerceAtLeast(0) * 2
    val goalOk = gName.isNotBlank() && (gTarget.toIntOrNull() ?: 0) > 0

    fun applyPreset(p: Preset) {
        title = p.title
        stat = p.stat
        sh = p.sh
        sm = p.sm
        eh = p.eh
        em = p.em
        proof = p.proof
        rtype = "DAILY"
        goals.clear()
        goals.addAll(p.goals)
    }

    Modal(null) {
        PopupFrame("NEW QUEST") {
            Text(
                "[New Quest: registration]", color = Soft, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text("PRESET  (optional)", color = SysBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS.forEach { p ->
                    Box(
                        Modifier.weight(1f).border(2.dp, Cyan)
                            .clickable { applyPreset(p) }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(p.label, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().border(1.dp, SysBlue).background(PanelBg).padding(14.dp)) {
                if (title.isEmpty()) Text("Quest name (e.g. Strength Training)", color = Dim, fontSize = 16.sp)
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
            Text("GOALS  (optional)", color = SysBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(4.dp))
            Text("Counters like Push-ups 100. No goals = one photo proof.", color = Dim, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            goals.forEachIndexed { i, g ->
                Row(
                    Modifier.fillMaxWidth().border(1.dp, Dim).padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${g.name}  [0/${g.target}${if (g.unit == "reps") "" else " " + g.unit}]",
                        color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "✕", color = SysRed, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { goals.removeAt(i) }.padding(6.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            SysField(gName, "Goal name (e.g. Push-ups)") { gName = it }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysField(gTarget, "Target", Modifier.weight(1f), number = true) {
                    gTarget = it.filter(Char::isDigit).take(5)
                }
                SysField(gUnit, "Unit (reps, km, min)", Modifier.weight(1f)) { gUnit = it }
            }
            Spacer(Modifier.height(6.dp))
            SysBtn("[ + ADD GOAL ]", if (goalOk) SysBlue else Dim, Modifier.fillMaxWidth()) {
                if (goalOk) {
                    goals.add(GoalDraft(gName.trim(), gTarget.toInt(), gUnit.trim().ifEmpty { "reps" }))
                    gName = ""
                    gTarget = ""
                    gUnit = "reps"
                }
            }

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
                TimeRow("DATE", fmtDate(date), Cyan) { pickingDate = true }
            }

            Spacer(Modifier.height(16.dp))
            Text("PROOF", color = SysBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            if (goals.isNotEmpty()) {
                Text(
                    "Each goal is proven by a photo when it reaches its target (type auto from the goal name).",
                    color = Cyan, fontSize = 12.sp
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("AUTO", "ANY", "POSE", "TEXT", "QR").forEach { k ->
                        val on = proof == k
                        Box(
                            Modifier.weight(1f)
                                .border(2.dp, if (on) SysBlue else Dim)
                                .background(if (on) SysBlue.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { proof = k }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                k, color = if (on) Color.White else Dim,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (proof == "AUTO") "AUTO picks: ${ProofRules.guess(title)}  (from quest name)"
                    else "Manual: $proof",
                    color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Text("ANY: any photo  •  POSE: body position  •  TEXT: written text  •  QR: scan code", color = Dim, fontSize = 11.sp)
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
                "WARNING: Time, schedule and goals are LOCKED after registering. Anything not completed by the deadline triggers the alarm.",
                color = SysRed, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SysBtn("[ CANCEL ]", Dim, Modifier.weight(1f)) { onCancel() }
                SysBtn("[ REGISTER ]", if (ok) SysBlue else Dim, Modifier.weight(1f)) {
                    if (ok) {
                        onSave(
                            title.trim(), sh, sm, eh, em, stat, rtype,
                            if (rtype == "DAYS") mask else 127,
                            if (rtype == "DATE") date.toEpochDay() else -1L,
                            proof, goals.toList()
                        )
                    }
                }
            }
        }
    }
    if (pickingDate) {
        Modal(null) {
            DatePickerPopup(date, { d ->
                date = d
                pickingDate = false
            }) { pickingDate = false }
        }
    }
    val which = picking
    if (which != null) {
        Modal(null) {
            if (which == "start") {
                TimePickerPopup("START TIME", sh, sm, { h, m ->
                    sh = h
                    sm = m
                    picking = null
                }) { picking = null }
            } else {
                TimePickerPopup("DEADLINE", eh, em, { h, m ->
                    eh = h
                    em = m
                    picking = null
                }) { picking = null }
            }
        }
    }
}

// Popup to add reps / distance to a goal.
@Composable
fun LogGoalPopup(g: Goal, today: Long, onApply: (Int) -> Unit, onCancel: () -> Unit) {
    val cur = g.progressOn(today)
    val left = (g.target - cur).coerceAtLeast(1)
    var add by remember { mutableIntStateOf(minOf(10, left)) }
    PopupFrame("LOG PROGRESS") {
        Text(
            g.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = GlowStyle
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "[$cur/${g.target}${g.unitSuffix()}]", color = Soft, fontSize = 14.sp,
            fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Text("ADD", color = Soft, fontSize = 12.sp, letterSpacing = 3.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "$add${g.unitSuffix()}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = GlowStyle
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SysBtn("-10", modifier = Modifier.weight(1f)) { add = (add - 10).coerceAtLeast(1) }
            SysBtn("-1", modifier = Modifier.weight(1f)) { add = (add - 1).coerceAtLeast(1) }
            SysBtn("+1", modifier = Modifier.weight(1f)) { add = minOf(left, add + 1) }
            SysBtn("+10", modifier = Modifier.weight(1f)) { add = minOf(left, add + 10) }
        }
        Spacer(Modifier.height(6.dp))
        SysBtn("[ FILL TO TARGET ]", Cyan, Modifier.fillMaxWidth()) { add = left }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SysBtn("[ CANCEL ]", Dim, Modifier.weight(1f)) { onCancel() }
            SysBtn("[ APPLY ]", SysBlue, Modifier.weight(1f)) { onApply(add) }
        }
    }
}

@Composable
private fun GoalRow(
    g: Goal,
    today: Long,
    active: Boolean,
    onLog: () -> Unit,
    onProof: () -> Unit
) {
    val prog = g.progressOn(today)
    val done = g.doneOn(today)
    val tone = if (done) Cyan else if (prog >= g.target) Soft else Color.White
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.name, color = tone, fontSize = 15.sp, modifier = Modifier.weight(1f), style = GlowStyle)
            Text(
                "[$prog/${g.target}${g.unitSuffix()}]", color = tone, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(18.dp).border(2.dp, tone), contentAlignment = Alignment.Center) {
                if (done) Text("✓", color = tone, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (active && !done) {
            Spacer(Modifier.height(6.dp))
            if (prog < g.target) {
                SysBtn("[ + LOG ]", modifier = Modifier.fillMaxWidth()) { onLog() }
            } else {
                SysBtn("[ SUBMIT PROOF ]", Cyan, Modifier.fillMaxWidth()) { onProof() }
            }
        }
    }
}

@Composable
fun QuestRow(
    q: Quest,
    st: QStatus,
    goals: List<Goal>,
    today: Long,
    onProof: () -> Unit,
    onLog: (Goal) -> Unit,
    onGoalProof: (Goal) -> Unit
) {
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
            if (goals.isEmpty()) {
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
        }
        if (goals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "GOAL", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline, letterSpacing = 2.sp, style = GlowStyle
            )
            goals.forEach { g ->
                GoalRow(g, today, st == QStatus.ACTIVE, { onLog(g) }, { onGoalProof(g) })
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(4.dp))
        }
        Text(
            "${q.scheduleLabel()}  •  ${q.stat}  •  $label  •  FAILS ${q.failStreak}  •  +${q.xpReward()} XP" +
                if (goals.isEmpty()) "  •  PROOF ${q.effectiveProof()}" else "",
            color = tone, fontSize = 10.sp, letterSpacing = 1.sp
        )
        if (goals.isEmpty() && st == QStatus.ACTIVE) {
            Spacer(Modifier.height(8.dp))
            SysBtn("[ SUBMIT PROOF ]", modifier = Modifier.fillMaxWidth()) { onProof() }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SysBlue.copy(alpha = 0.25f)))
}

@Composable
private fun AnalyzingModal() {
    Modal(null) {
        PopupFrame("ANALYZING") {
            Text(
                "[Verifying proof...]", color = Soft, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                style = GlowStyle
            )
        }
    }
}

@Composable
fun QuestHome() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val quests by dao.all().collectAsState(initial = emptyList())
    val allGoals by dao.allGoals().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(quests) { quests.forEach { AlarmScheduler.schedule(ctx, it) } }
    var adding by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var photoVersion by remember { mutableIntStateOf(0) }
    var proofFor by remember { mutableStateOf<Quest?>(null) }
    var logFor by remember { mutableStateOf<Pair<Quest, Goal>?>(null) }
    var goalProof by remember { mutableStateOf<Pair<Quest, Goal>?>(null) }
    var nowM by remember { mutableIntStateOf(nowMin()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowM = nowMin()
            delay(10_000)
        }
    }
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (ProfileStore.saveFrom(ctx, uri)) {
                photoVersion++
                SysPopup.post(ctx, "NOTIFICATION", "[Profile photo updated.]", 0, null)
            } else {
                SysPopup.post(ctx, "NOTIFICATION", "Could not load that photo.", 2, null)
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02050D), Color(0xFF0B2347))))
    ) {
        val pq = proofFor
        val pg = goalProof
        if (pq != null) {
            CameraProof(
                pq.id,
                onCaptured = { file ->
                    if (!analyzing) {
                        analyzing = true
                        scope.launch {
                            val r = ProofCheck.verify(file, pq)
                            analyzing = false
                            if (r.ok) {
                                dao.markDone(pq.id, LocalDate.now().toEpochDay())
                                Rewards.gain(
                                    ctx, pq.xpReward(), pq.stat, 1,
                                    "Quest cleared: ${pq.title}\n${r.message}"
                                )
                                proofFor = null
                            } else {
                                file.delete()
                                SysPopup.post(ctx, "PROOF REJECTED", r.message, 2, "TRY AGAIN")
                            }
                        }
                    }
                },
                onCancel = { proofFor = null }
            )
            if (analyzing) AnalyzingModal()
        } else if (pg != null) {
            val gq = pg.first
            val gg = pg.second
            CameraProof(
                gg.id,
                onCaptured = { file ->
                    if (!analyzing) {
                        analyzing = true
                        scope.launch {
                            val probe = Quest(
                                title = gg.name, hour = 0, minute = 0, endHour = 0, endMinute = 0,
                                proofType = gg.proofType
                            )
                            val r = ProofCheck.verify(file, probe)
                            analyzing = false
                            if (r.ok) {
                                val day = LocalDate.now().toEpochDay()
                                dao.markGoalDone(gg.id, day)
                                val all = dao.goalsFor(gq.id)
                                val allDone = all.all { it.id == gg.id || it.doneOn(day) }
                                goalProof = null
                                if (allDone) {
                                    dao.markDone(gq.id, day)
                                    Rewards.gain(
                                        ctx, gq.xpReward(), gq.stat, 1,
                                        "Quest cleared: ${gq.title}\nAll goals complete."
                                    )
                                } else {
                                    SysPopup.post(
                                        ctx, "NOTIFICATION",
                                        "[Goal complete: ${gg.name}]\n${r.message}", 1, null
                                    )
                                }
                            } else {
                                file.delete()
                                SysPopup.post(ctx, "PROOF REJECTED", r.message, 2, "TRY AGAIN")
                            }
                        }
                    }
                },
                onCancel = { goalProof = null }
            )
            if (analyzing) AnalyzingModal()
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                TopBar(
                    photoVersion = photoVersion,
                    onMenu = { menuOpen = true },
                    onStatus = { statusOpen = true },
                    onProfile = { profileOpen = true }
                )
                Spacer(Modifier.height(12.dp))
                SysFrame(Modifier.weight(1f).fillMaxWidth()) {
                    QuestInfoHeader("QUEST INFO")
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (quests.isEmpty()) "[No quests registered.]" else "[Daily Quests have arrived.]",
                        color = Soft, fontSize = 13.sp, textAlign = TextAlign.Center,
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
                                QuestRow(
                                    q = q,
                                    st = statusOf(q, nowM, today),
                                    goals = allGoals.filter { it.questId == q.id },
                                    today = todayDay,
                                    onProof = { proofFor = q },
                                    onLog = { g -> logFor = q to g },
                                    onGoalProof = { g -> goalProof = q to g }
                                )
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

            if (menuOpen) {
                Modal({ menuOpen = false }) {
                    PopupFrame("MENU") {
                        SysBtn("[ STATUS ]", modifier = Modifier.fillMaxWidth()) {
                            menuOpen = false
                            statusOpen = true
                        }
                        Spacer(Modifier.height(10.dp))
                        SysBtn("[ PROFILE ]", modifier = Modifier.fillMaxWidth()) {
                            menuOpen = false
                            profileOpen = true
                        }
                        Spacer(Modifier.height(10.dp))
                        SysBtn("[ CLOSE ]", Dim, Modifier.fillMaxWidth()) { menuOpen = false }
                    }
                }
            }
            if (statusOpen) {
                Modal({ statusOpen = false }) {
                    PopupFrame("STATUS") {
                        StatusPopupContent()
                        Spacer(Modifier.height(16.dp))
                        SysBtn("[ OK ]", modifier = Modifier.fillMaxWidth()) { statusOpen = false }
                    }
                }
            }
            if (profileOpen) {
                Modal({ profileOpen = false }) {
                    PopupFrame("PROFILE") {
                        ProfileContent(
                            photoVersion = photoVersion,
                            onChange = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onClose = { profileOpen = false }
                        )
                    }
                }
            }
            val lf = logFor
            if (lf != null) {
                Modal(null) {
                    LogGoalPopup(lf.second, todayDay, { add ->
                        val lq = lf.first
                        val lg = lf.second
                        scope.launch {
                            val day = LocalDate.now().toEpochDay()
                            val np = minOf(lg.target, lg.progressOn(day) + add)
                            dao.setProgress(lg.id, np, day)
                            logFor = null
                            if (np >= lg.target && !lg.doneOn(day)) {
                                goalProof = lq to lg.copy(progress = np, progressDay = day)
                            }
                        }
                    }) { logFor = null }
                }
            }
            if (adding) {
                AddQuest(
                    onSave = { t, a, b, c, d, s, rt, dm, dd, pf, gl ->
                        scope.launch {
                            val qid = dao.insert(
                                Quest(
                                    title = t, hour = a, minute = b, endHour = c, endMinute = d,
                                    stat = s, repeatType = rt, daysMask = dm, dateDay = dd,
                                    proofType = pf
                                )
                            )
                            gl.forEach { g ->
                                dao.insertGoal(Goal(questId = qid, name = g.name, target = g.target, unit = g.unit))
                            }
                        }
                        SysPopup.post(ctx, "NOTIFICATION", "[Quest registered: $t]", 0, null)
                        adding = false
                    },
                    onCancel = { adding = false }
                )
            }
        }
    }
}
