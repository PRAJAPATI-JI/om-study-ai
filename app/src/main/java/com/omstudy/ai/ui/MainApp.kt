@file:OptIn(ExperimentalMaterial3Api::class)

package com.omstudy.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omstudy.ai.alarm.RepeatRule
import com.omstudy.ai.data.Keys
import com.omstudy.ai.data.Onboarding
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalDateTime

private data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MainApp(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        NavItem("होम", Icons.Default.Home),
        NavItem("प्लानर", Icons.Default.Checklist),
        NavItem("अलार्म", Icons.Default.Alarm),
        NavItem("टूल्स", Icons.Default.Calculate),
        NavItem("सेटिंग्स", Icons.Default.Settings)
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text("OM STUDY AI", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, item ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm) { tab = it }
                1 -> PlannerScreen(vm)
                2 -> AlarmsScreen(vm)
                3 -> ToolsScreen()
                else -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, goTo: (Int) -> Unit) {
    val alarms by vm.alarms.collectAsState()
    val tasks by vm.todayTasks.collectAsState()
    val logs by vm.todayLogs.collectAsState()
    val name by vm.userName.collectAsState()
    val answers by vm.answers.collectAsState()
    val ctx = LocalContext.current

    val now = LocalDateTime.now()
    val next = alarms.filter { it.enabled }.mapNotNull { a ->
        RepeatRule.nextTrigger(a.hour, a.minute, a.daysMask, a.oneTimeEpochDay, now)?.let { a to it }
    }.minByOrNull { it.second }

    val today = LocalDate.now()
    val done = tasks.count { it.done }
    val fraction = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
    val minutes = tasks.filter { it.done }.sumOf { it.durationMin }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (name.isBlank()) "नमस्ते!" else "नमस्ते, $name!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(fmtDate(today.toEpochDay()), fontSize = 15.sp)

        answers?.let { a ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("मेरे लक्ष्य", fontWeight = FontWeight.SemiBold)
                    Onboarding.goalLabel(a).forEach { Text("• $it") }
                    a[Keys.DAILY_MIN]?.toIntOrNull()?.let { Text("रोज़ का समय: ${it / 60} घंटे", fontSize = 14.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            vm.activatePlan { n -> Toast.makeText(ctx, "$n काम प्लानर में जुड़े", Toast.LENGTH_LONG).show() }
                        }) { Text("योजना फिर बनाएँ") }
                        OutlinedButton(onClick = { vm.reopenReview() }) { Text("जवाब बदलें") }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("अगला अलार्म", fontWeight = FontWeight.SemiBold)
                if (next == null) {
                    Text("कोई चालू अलार्म नहीं है।")
                    Button(onClick = { goTo(2) }) { Text("अलार्म जोड़ें") }
                } else {
                    val (a, t) = next
                    val dayWord = when (t.toLocalDate()) {
                        today -> "आज"
                        today.plusDays(1) -> "कल"
                        else -> fmtDate(t.toLocalDate().toEpochDay())
                    }
                    Text(fmtTime(t.hour, t.minute), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${a.title.ifBlank { a.subject }} · ${a.subject} · $dayWord")
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("आज के काम", fontWeight = FontWeight.SemiBold)
                if (tasks.isEmpty()) {
                    Text("आज के लिए कोई काम नहीं जोड़ा है।")
                } else {
                    Text("$done / ${tasks.size} पूरे · पढ़ाई $minutes मिनट")
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = { goTo(1) }) { Text("प्लानर खोलें") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("आज के अलार्म का हिसाब", fontWeight = FontWeight.SemiBold)
                val fired = logs.count { it.action == "fired" }
                val started = logs.count { it.action == "started_study" }
                val missed = logs.count { it.action == "missed" }
                if (fired == 0) {
                    Text("आज अभी कोई अलार्म नहीं बजा।")
                } else {
                    Text("बजे: $fired · पढ़ाई शुरू की: $started · छूटे: $missed")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { goTo(3) }, modifier = Modifier.weight(1f)) { Text("कैलकुलेटर / टाइमर") }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("अभी बने नहीं हैं (आगे के चरणों में आएँगे)", fontWeight = FontWeight.SemiBold)
                listOf(
                    "AI शिक्षक", "JAC 12वीं पाठ्यसामग्री", "सरकारी परीक्षा अभ्यास",
                    "क्विज़ / मॉक टेस्ट", "बेसिक गणित कोर्स", "असिस्टेंट इलेक्ट्रीशियन"
                ).forEach { feature ->
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("$feature · जल्द आ रहा है")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
