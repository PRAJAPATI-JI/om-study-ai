@file:OptIn(ExperimentalMaterial3Api::class)

package com.omstudy.ai.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omstudy.ai.data.TaskEntity
import java.time.LocalDate

private val PRIORITY = listOf("कम", "सामान्य", "ज़रूरी")

@Composable
fun PlannerScreen(vm: MainViewModel) {
    val day by vm.day.collectAsState()
    val tasks by vm.tasks.collectAsState()
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var deleting by remember { mutableStateOf<TaskEntity?>(null) }

    val today = LocalDate.now().toEpochDay()
    val done = tasks.count { it.done }
    val fraction = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
    val studied = tasks.filter { it.done }.sumOf { it.durationMin }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { vm.setDay(day - 1) }) { Icon(Icons.Default.ChevronLeft, "पिछला दिन") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (day == today) "आज" else fmtDate(day).substringBefore(","), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(fmtDate(day), fontSize = 12.sp)
                    }
                    IconButton(onClick = { vm.setDay(day + 1) }) { Icon(Icons.Default.ChevronRight, "अगला दिन") }
                }
                if (day != today) TextButton(onClick = { vm.setDay(today) }) { Text("आज पर जाएँ") }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (tasks.isEmpty()) {
                            Text("इस दिन के लिए कोई काम नहीं है। नीचे + दबाकर जोड़िए।")
                        } else {
                            Text("$done / ${tasks.size} काम पूरे (${(fraction * 100).toInt()}%) · पढ़ाई $studied मिनट")
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            items(tasks, key = { it.id }) { t ->
                TaskCard(
                    t,
                    onToggle = { vm.toggleTask(t, it) },
                    onEdit = { editing = t },
                    onPostpone = { vm.postponeTask(t) },
                    onDelete = { deleting = t }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
        FloatingActionButton(
            onClick = {
                editing = TaskEntity(title = "", subject = SUBJECTS[0], epochDay = day, startMinutes = 18 * 60, durationMin = 45)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "नया काम") }
    }

    editing?.let { initial ->
        TaskEditor(
            initial,
            onSave = {
                vm.saveTask(it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("काम हटाएँ?") },
            text = { Text("\"${t.title}\" हमेशा के लिए हट जाएगा।") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTask(t)
                    deleting = null
                }) { Text("हटाएँ") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("रहने दें") } }
        )
    }
}

@Composable
private fun TaskCard(
    t: TaskEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onPostpone: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = t.done, onCheckedChange = onToggle)
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null
                    )
                    Text(
                        "${t.subject} · ${fmtMinutes(t.startMinutes)} · ${t.durationMin} मिनट · ${PRIORITY[t.priority.coerceIn(0, 2)]}",
                        fontSize = 12.sp
                    )
                    if (t.note.isNotBlank()) Text(t.note, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "बदलें") }
                if (!t.done) IconButton(onClick = onPostpone) { Icon(Icons.Default.Redo, "कल के लिए टालें") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "हटाएँ") }
            }
        }
    }
}

@Composable
private fun TaskEditor(initial: TaskEntity, onSave: (TaskEntity) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var subject by remember { mutableStateOf(initial.subject) }
    var start by remember { mutableIntStateOf(initial.startMinutes) }
    var duration by remember { mutableStateOf(initial.durationMin.toString()) }
    var priority by remember { mutableIntStateOf(initial.priority) }
    var note by remember { mutableStateOf(initial.note) }
    var showTime by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "नया काम" else "काम बदलें") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("काम (जैसे: प्रतिशत के 10 सवाल)") },
                    modifier = Modifier.fillMaxWidth()
                )
                SubjectPicker(subject) { subject = it }
                OutlinedButton(onClick = { showTime = true }) { Text("शुरू होने का समय: ${fmtMinutes(start)}") }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { v -> duration = v.filter { it.isDigit() }.take(3) },
                    label = { Text("कितने मिनट (5 से 600)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRIORITY.forEachIndexed { i, label ->
                        FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("नोट (वैकल्पिक)") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, textAlign = TextAlign.Start) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = duration.toIntOrNull()
                when {
                    title.isBlank() -> error = "काम का नाम लिखिए।"
                    d == null || d !in 5..600 -> error = "मिनट 5 से 600 के बीच लिखिए।"
                    else -> onSave(
                        initial.copy(
                            title = title.trim(), subject = subject, startMinutes = start,
                            durationMin = d, priority = priority, note = note.trim()
                        )
                    )
                }
            }) { Text("सेव करें") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("रद्द करें") } }
    )

    if (showTime) {
        TimeDialog(start / 60, start % 60, onDismiss = { showTime = false }) { h, m ->
            start = h * 60 + m
            showTime = false
        }
    }
}
