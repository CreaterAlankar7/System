package com.system.app

import android.content.Context
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

// Quest registration shown as a popup window. All values live in `draft`
// so that both touch and voice can fill the same form.
@Composable
fun AddQuest(draft: QuestDraft, onSave: () -> Unit, onCancel: () -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    val validTime = draft.eh * 60 + draft.em > draft.sh * 60 + draft.sm
    val daysOk = draft.rtype != "DAYS" || draft.mask != 0
    val ok = draft.title.isNotBlank() && validTime && daysOk
    val reward = ((draft.eh * 60 + draft.em) - (draft.sh * 60 + draft.sm)).coerceAtLeast(0) * 2

    Modal(null) {
        PopupFrame("NEW QUEST") {
            Text(
                "[New Quest: registration]", color = Soft, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().border(1.dp, SysBlue).background(PanelBg).padding(14.dp)) {
                if (draft.title.isEmpty()) Text("Quest name (e.g. 10 Pushups)", color = Dim, fontSize = 16.sp)
                BasicTextField(
                    value = draft.title,
                    onValueChange = { draft.title = it },
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
                    SysBtn("[ $s ]", if (draft.stat == s) SysBlue else Dim, Modifier.weight(1f)) { draft.stat = s }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("STR: fitness  •  INT: study  •  VIT: habits", color = Dim, fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            Text("REPEAT", color = SysBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DAILY", "DAYS", "DATE").forEach { k ->
                    SysBtn("[ $k ]", if (draft.rtype == k) SysBlue else Dim, Modifier.weight(1f)) { draft.rtype = k }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("DAILY: every day  •  DAYS: pick weekdays  •  DATE: one date", color = Dim, fontSize = 11.sp)
            if (draft.rtype == "DAYS") {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEachIndexed { i, d ->
                        val on = ((draft.mask shr i) and 1) == 1
                        Box(
                            Modifier.weight(1f)
                                .border(1.dp, if (on) SysBlue else Dim)
                                .background(if (on) SysBlue.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { draft.mask = draft.mask xor (1 shl i) }
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
                if (draft.mask == 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Pick at least one day.", color = SysRed, fontSize = 12.sp)
                }
            }
            if (draft.rtype == "DATE") {
                Spacer(Modifier.height(10.dp))
                TimeRow("DATE", fmtDate(draft.date), Cyan) { pickingDate = true }
            }
            Spacer(Modifier.height(16.dp))
            Text("TIME  (tap to change)", color = SysBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            TimeRow("START", fmt12(draft.sh, draft.sm)) { picking = "start" }
            Spacer(Modifier.height(8.dp))
            TimeRow("DEADLINE", fmt12(draft.eh, draft.em), SysRed) { picking = "end" }
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
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SysBtn("[ CANCEL ]", Dim, Modifier.weight(1f)) { onCancel() }
                SysBtn("[ REGISTER ]", if (ok) SysBlue else Dim, Modifier.weight(1f)) {
                    if (ok) onSave()
                }
            }
        }
    }
    if (pickingDate) {
        Modal(null) {
            DatePickerPopup(draft.date, { d ->
                draft.date = d
                pickingDate = false
            }) { pickingDate = false }
        }
    }
    val which = picking
    if (which != null) {
        Modal(null) {
            if (which == "start") {
                TimePickerPopup("START TIME", draft.sh, draft.sm, { h, m ->
                    draft.sh = h
                    draft.sm = m
                    picking = null
                }) { picking = null }
            } else {
                TimePickerPopup("DEADLINE", draft.eh, draft.em, { h, m ->
                    draft.eh = h
                    draft.em = m
                    picking = null
                }) { picking = null }
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
    var adding by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var photoVersion by remember { mutableIntStateOf(0) }
    var proofFor by remember { mutableStateOf<Quest?>(null) }
    var nowM by remember { mutableIntStateOf(nowMin()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowM = nowMin()
            delay(10_000)
        }
    }
    val today = LocalDate.now()

    val draft = remember { QuestDraft() }
    val doSave: () -> Unit = {
        val d = draft
        val valid = d.title.isNotBlank() &&
            d.eh * 60 + d.em > d.sh * 60 + d.sm &&
            (d.rtype != "DAYS" || d.mask != 0)
        if (valid) {
            val name = d.title.trim()
            scope.launch {
                dao.insert(
                    Quest(
                        title = name, hour = d.sh, minute = d.sm, endHour = d.eh, endMinute = d.em,
                        stat = d.stat, repeatType = d.rtype,
                        daysMask = if (d.rtype == "DAYS") d.mask else 127,
                        dateDay = if (d.rtype == "DATE") d.date.toEpochDay() else -1L
                    )
                )
            }
            SysPopup.post(ctx, "NOTIFICATION", "[Quest registered: $name]", 0, null)
            adding = false
        }
    }

    // ---- Voice ----
    val prefs = remember { ctx.getSharedPreferences("system_prefs", Context.MODE_PRIVATE) }
    val voice = remember { VoiceController(ctx, draft) }
    voice.onEvent = { e ->
        when (e) {
            VoiceEvent.OPEN_NEW -> {
                menuOpen = false
                statusOpen = false
                profileOpen = false
                adding = true
            }
            VoiceEvent.OPEN_STATUS -> {
                menuOpen = false
                profileOpen = false
                adding = false
                statusOpen = true
            }
            VoiceEvent.OPEN_PROFILE -> {
                menuOpen = false
                statusOpen = false
                adding = false
                profileOpen = true
            }
            VoiceEvent.REGISTER -> doSave()
            VoiceEvent.CANCEL_FORM -> adding = false
        }
    }
    val lifecycle = (ctx as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (prefs.getBoolean("voice_on", false)) voice.start()
                Lifecycle.Event.ON_STOP -> voice.stop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && prefs.getBoolean("voice_on", false)) {
            voice.start()
        }
        onDispose {
            lifecycle.removeObserver(obs)
            voice.release()
        }
    }
    val toggleVoice: () -> Unit = {
        val on = !prefs.getBoolean("voice_on", false)
        prefs.edit().putBoolean("voice_on", on).apply()
        if (on) {
            voice.start()
            voice.say("वॉइस मोड चालू।")
        } else {
            voice.say("वॉइस मोड बंद।")
            prefs.edit().putBoolean("voice_on", false).apply()
        }
    }

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
        if (pq != null) {
            CameraProof(
                pq.id,
                onCaptured = { _ ->
                    scope.launch { dao.markDone(pq.id, LocalDate.now().toEpochDay()) }
                    Rewards.gain(ctx, pq.xpReward(), pq.stat, 1, "Quest cleared: ${pq.title}")
                    proofFor = null
                },
                onCancel = { proofFor = null }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TopBar(
                            photoVersion = photoVersion,
                            onMenu = { menuOpen = true },
                            onStatus = { statusOpen = true },
                            onProfile = { profileOpen = true }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    MicButton(on = voice.enabled, listening = voice.listening, onClick = toggleVoice)
                }
                Spacer(Modifier.height(12.dp))
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
                SysBtn("[ + NEW QUEST ]", modifier = Modifier.fillMaxWidth()) {
                    draft.reset()
                    adding = true
                }
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
            if (adding) {
                AddQuest(
                    draft = draft,
                    onSave = { doSave() },
                    onCancel = { adding = false }
                )
            }
        }
    }
}
