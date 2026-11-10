@file:OptIn(ExperimentalMaterial3Api::class)

package com.omstudy.ai.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.omstudy.ai.alarm.RepeatRule
import com.omstudy.ai.data.AlarmEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private fun hasNotifPermission(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
fun AlarmsScreen(vm: MainViewModel) {
    val alarms by vm.alarms.collectAsState()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<AlarmEntity?>(null) }
    var deleting by remember { mutableStateOf<AlarmEntity?>(null) }
    var notifGranted by remember { mutableStateOf(hasNotifPermission(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!notifGranted) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("नोटिफिकेशन की अनुमति चाहिए", fontWeight = FontWeight.SemiBold)
                            Text("अलार्म की स्क्रीन और बटन नोटिफिकेशन के ज़रिए दिखते हैं। अनुमति न देने पर अलार्म की आवाज़ बजेगी, पर स्क्रीन नहीं दिखेगी।")
                            Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("अनुमति दें") }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("अलार्म की जाँच", fontWeight = FontWeight.SemiBold)
                        Text("बटन दबाकर फोन लॉक कर दीजिए। 10 सेकंड बाद टेस्ट अलार्म बजना चाहिए।")
                        OutlinedButton(onClick = {
                            vm.testAlarm()
                            Toast.makeText(ctx, "10 सेकंड बाद टेस्ट अलार्म बजेगा", Toast.LENGTH_LONG).show()
                        }) { Text("10 सेकंड का टेस्ट अलार्म") }
                    }
                }
            }
            if (alarms.isEmpty()) {
                item {
                    Text(
                        "अभी कोई अलार्म नहीं है।\nनीचे + बटन दबाकर पहला अलार्म जोड़िए।",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
            }
            items(alarms, key = { it.id }) { a ->
                AlarmCard(
                    a,
                    onToggle = { vm.toggleAlarm(a, it) },
                    onEdit = { editing = a },
                    onDelete = { deleting = a }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
        FloatingActionButton(
            onClick = { editing = AlarmEntity(title = "", subject = SUBJECTS[0], hour = 6, minute = 0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "नया अलार्म") }
    }

    editing?.let { initial ->
        AlarmEditor(
            initial = initial,
            onSave = {
                vm.saveAlarm(it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("अलार्म हटाएँ?") },
            text = { Text("\"${a.title.ifBlank { a.subject }}\" हमेशा के लिए हट जाएगा।") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAlarm(a)
                    deleting = null
                }) { Text("हटाएँ") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("रहने दें") } }
        )
    }
}

@Composable
private fun AlarmCard(a: AlarmEntity, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fmtTime(a.hour, a.minute), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(a.title.ifBlank { a.subject }, fontWeight = FontWeight.SemiBold)
                    Text("${a.subject} · ${daysText(a.daysMask, a.oneTimeEpochDay)}", fontSize = 13.sp)
                    Text("स्नूज़ ${a.snoozeMinutes} मिनट" + if (a.vibrate) " · वाइब्रेशन" else "", fontSize = 12.sp)
                }
                Switch(checked = a.enabled, onCheckedChange = onToggle)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "बदलें") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "हटाएँ") }
            }
        }
    }
}

@Composable
private fun DayToggle(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlarmEditor(initial: AlarmEntity, onSave: (AlarmEntity) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var subject by remember { mutableStateOf(initial.subject) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var mask by remember { mutableIntStateOf(initial.daysMask) }
    var date by remember { mutableStateOf(initial.oneTimeEpochDay) }
    var snooze by remember { mutableIntStateOf(initial.snoozeMinutes) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "नया अलार्म" else "अलार्म बदलें") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("अलार्म का नाम (जैसे: सुबह का गणित)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SubjectPicker(subject) { subject = it }
                OutlinedButton(onClick = { showTime = true }) { Text("समय: ${fmtTime(hour, minute)}") }

                Text("दोहराएँ (कोई दिन न चुनें = एक बार)", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        DayToggle(label, mask and (1 shl i) != 0, Modifier.weight(1f)) {
                            mask = mask xor (1 shl i)
                            error = null
                        }
                    }
                }
                Row {
                    TextButton(onClick = { mask = 127 }) { Text("रोज़") }
                    TextButton(onClick = { mask = 0 }) { Text("सिर्फ एक बार") }
                }
                if (mask == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showDate = true }) {
                            Text(date?.let { "तारीख़: ${fmtDate(it)}" } ?: "तारीख़ चुनें (वैकल्पिक)")
                        }
                        if (date != null) TextButton(onClick = { date = null }) { Text("हटाएँ") }
                    }
                }

                Text("स्नूज़ का समय", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { m ->
                        FilterChip(selected = snooze == m, onClick = { snooze = m }, label = { Text("$m मिनट") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("वाइब्रेशन", modifier = Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val oneTime = if (mask == 0) date else null
                val next = RepeatRule.nextTrigger(hour, minute, mask, oneTime, LocalDateTime.now())
                if (next == null) {
                    error = "यह तारीख़ और समय बीत चुका है। कोई आगे की तारीख़ चुनिए।"
                } else {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            subject = subject,
                            hour = hour,
                            minute = minute,
                            daysMask = mask,
                            oneTimeEpochDay = oneTime,
                            snoozeMinutes = snooze,
                            vibrate = vibrate,
                            enabled = true
                        )
                    )
                }
            }) { Text("सेव करें") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("रद्द करें") } }
    )

    if (showTime) {
        TimeDialog(hour, minute, onDismiss = { showTime = false }) { h, m ->
            hour = h
            minute = m
            showTime = false
            error = null
        }
    }
    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDate = false
                    error = null
                }) { Text("ठीक है") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("रद्द करें") } }
        ) { DatePicker(state = state) }
    }
}
