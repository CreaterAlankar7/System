package com.system.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val CancelTone = Color(0xFF5E7FA8)

@Composable
fun TimePickerPopup(
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
    PopupFrame(title) {
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
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SysBtn("[ CANCEL ]", CancelTone, Modifier.weight(1f)) { onCancel() }
            SysBtn("[ APPLY ]", SysBlue, Modifier.weight(1f)) {
                onApply(((hIdx + 1) % 12) + if (pIdx == 1) 12 else 0, mIdx)
            }
        }
    }
}

@Composable
fun DatePickerPopup(selected: LocalDate, onApply: (LocalDate) -> Unit, onCancel: () -> Unit) {
    var temp by remember { mutableStateOf(selected) }
    PopupFrame("SELECT DATE") {
        CalendarPicker(temp) { temp = it }
        Spacer(Modifier.height(8.dp))
        Text(
            "SELECTED: " + fmtDate(temp), color = Color(0xFF7FE8FF),
            fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SysBtn("[ CANCEL ]", CancelTone, Modifier.weight(1f)) { onCancel() }
            SysBtn("[ APPLY ]", SysBlue, Modifier.weight(1f)) { onApply(temp) }
        }
    }
}