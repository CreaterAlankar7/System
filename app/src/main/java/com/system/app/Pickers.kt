package com.system.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

val Ice = Color(0xFF9FD4FF)
val GlowStyle = TextStyle(shadow = Shadow(SysBlue, Offset.Zero, 24f))
private val Muted = Color(0xFF5E7FA8)

fun fmt12(h: Int, m: Int): String {
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%02d:%02d %s".format(h12, m, if (h >= 12) "PM" else "AM")
}

// Glowing blue "QUEST INFO" style window with corner brackets.
@Composable
fun SysFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .shadow(24.dp, RectangleShape, ambientColor = SysBlue, spotColor = SysBlue)
            .background(Color(0xF005101F))
            .border(2.dp, SysBlue)
            .drawBehind {
                val len = 28.dp.toPx()
                val sw = 4.dp.toPx()
                val w = size.width
                val h = size.height
                drawLine(Ice, Offset(0f, 0f), Offset(len, 0f), sw)
                drawLine(Ice, Offset(0f, 0f), Offset(0f, len), sw)
                drawLine(Ice, Offset(w, 0f), Offset(w - len, 0f), sw)
                drawLine(Ice, Offset(w, 0f), Offset(w, len), sw)
                drawLine(Ice, Offset(0f, h), Offset(len, h), sw)
                drawLine(Ice, Offset(0f, h), Offset(0f, h - len), sw)
                drawLine(Ice, Offset(w, h), Offset(w - len, h), sw)
                drawLine(Ice, Offset(w, h), Offset(w, h - len), sw)
            }
            .padding(8.dp)
    ) {
        Column(
            Modifier.fillMaxSize().border(1.dp, SysBlue.copy(alpha = 0.35f)).padding(16.dp)
        ) { content() }
    }
}

@Composable
fun QuestInfoHeader(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).border(1.dp, Ice.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp).border(2.dp, Ice, CircleShape), contentAlignment = Alignment.Center) {
                Text("!", color = Ice, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = GlowStyle)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).height(52.dp).border(1.dp, Ice.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp, style = GlowStyle
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Wheel(items: List<String>, initial: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val itemH = 40.dp
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val fling = rememberSnapFlingBehavior(state)
    val cb by rememberUpdatedState(onSelect)
    val center by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving ->
            if (!moving) cb(state.firstVisibleItemIndex.coerceIn(0, items.lastIndex))
        }
    }
    Box(modifier.height(itemH * 5)) {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemH * 2),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items) { i, s ->
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        s,
                        color = if (i == center) Color.White else Color(0xFF4F6C93),
                        fontSize = if (i == center) 26.sp else 18.sp,
                        fontWeight = if (i == center) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(itemH)
                .border(1.dp, SysBlue.copy(alpha = 0.7f))
        )
    }
}

@Composable
fun TimePickerPanel(
    title: String,
    h24: Int,
    minute: Int,
    onApply: (Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    val hours = remember { (1..12).map { "%02d".format(it) } }
    val mins = remember { (0..59).map { "%02d".format(it) } }
    val ampm = remember { listOf("AM", "PM") }
    var hIdx by remember { mutableIntStateOf((if (h24 % 12 == 0) 12 else h24 % 12) - 1) }
    var mIdx by remember { mutableIntStateOf(minute) }
    var pIdx by remember { mutableIntStateOf(if (h24 >= 12) 1 else 0) }
    SysFrame(Modifier.fillMaxWidth()) {
        Text(
            title, color = Ice, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Wheel(hours, hIdx, { hIdx = it }, Modifier.width(66.dp))
            Text(":", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Wheel(mins, mIdx, { mIdx = it }, Modifier.width(66.dp))
            Wheel(ampm, pIdx, { pIdx = it }, Modifier.width(66.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SysBtn("[ CANCEL ]", Muted, Modifier.weight(1f)) { onCancel() }
            SysBtn("[ APPLY ]", SysBlue, Modifier.weight(1f)) {
                onApply(((hIdx + 1) % 12) + if (pIdx == 1) 12 else 0, mIdx)
            }
        }
    }
}

@Composable
fun CalendarPicker(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val monthName = month.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, SysBlue.copy(alpha = 0.6f))
            .background(Color(0xFF07122A))
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SysBtn("<") { month = month.minusMonths(1) }
            Text("$monthName ${month.year}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            SysBtn(">") { month = month.plusMonths(1) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEach {
                Text(
                    it, color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (r in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val d = start.plusDays((r * 7 + c).toLong())
                    val inMonth = d.month == month.month
                    val past = d.isBefore(today)
                    val sel = d == selected
                    Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(36.dp)
                                .then(
                                    if (sel) {
                                        Modifier
                                            .shadow(10.dp, CircleShape, ambientColor = SysBlue, spotColor = SysBlue)
                                            .background(SysBlue, CircleShape)
                                    } else if (d == today) {
                                        Modifier.border(1.dp, SysBlue, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clip(CircleShape)
                                .then(
                                    if (!past) {
                                        Modifier.clickable {
                                            onSelect(d)
                                            month = YearMonth.from(d)
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${d.dayOfMonth}",
                                color = when {
                                    sel -> Color.White
                                    past -> Color(0xFF2E415E)
                                    inMonth -> Color.White
                                    else -> Muted
                                },
                                fontSize = 14.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}