@file:OptIn(ExperimentalMaterial3Api::class)

package com.omstudy.ai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val SUBJECTS = listOf(
    "बेसिक गणित",
    "JAC 12 गणित",
    "भौतिकी",
    "रसायन",
    "अंग्रेज़ी",
    "अर्थशास्त्र",
    "सरकारी परीक्षा",
    "इलेक्ट्रीशियन",
    "रिवीज़न / क्विज़",
    "अन्य"
)

val DAY_LABELS = listOf("सो", "मं", "बु", "गु", "शु", "श", "र")
val DAY_FULL = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")

fun fmtTime(h: Int, m: Int): String {
    val ampm = if (h < 12) "AM" else "PM"
    val hh = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.ENGLISH, "%d:%02d %s", hh, m, ampm)
}

fun fmtMinutes(total: Int): String = fmtTime(total / 60, total % 60)

fun fmtDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy, EEEE", Locale("hi", "IN")))

fun daysText(mask: Int, oneTimeEpochDay: Long?): String = when {
    mask == 0 -> if (oneTimeEpochDay != null) "एक बार: ${fmtDate(oneTimeEpochDay)}" else "एक बार (अगली बार)"
    mask == 127 -> "रोज़"
    else -> DAY_LABELS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(" ")
}

@Composable
fun SubjectPicker(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("विषय: $value") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SUBJECTS.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s) },
                    onClick = {
                        onChange(s)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun TimeDialog(initHour: Int, initMinute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initHour, initialMinute = initMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("ठीक है") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("रद्द करें") } },
        text = { TimePicker(state = state, modifier = Modifier.padding(top = 8.dp)) }
    )
}
