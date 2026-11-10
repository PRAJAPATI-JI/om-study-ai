package com.omstudy.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omstudy.ai.tools.Calc
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen() {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = page == 0, onClick = { page = 0 }, label = { Text("कैलकुलेटर") })
            FilterChip(selected = page == 1, onClick = { page = 1 }, label = { Text("फोकस टाइमर") })
        }
        Spacer(Modifier.height(12.dp))
        if (page == 0) CalculatorPane() else FocusTimerPane()
    }
}

@Composable
private fun CalculatorPane() {
    var expr by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun press(key: String) {
        error = null
        when (key) {
            "C" -> expr = ""
            "⌫" -> expr = expr.dropLast(1)
            "=" -> {
                try {
                    expr = Calc.evaluate(expr)
                } catch (e: Calc.CalcException) {
                    error = e.message
                }
            }
            else -> if (expr.length < 80) expr += key
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            expr.ifEmpty { "0" },
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val rows = listOf(
            listOf("C", "(", ")", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "%", "⌫")
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    OutlinedButton(onClick = { press(k) }, modifier = Modifier.weight(1f).height(58.dp)) {
                        Text(k, fontSize = 20.sp)
                    }
                }
            }
        }
        Button(onClick = { press("=") }, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("=", fontSize = 22.sp) }
        Text("नोट: 50% का मतलब 0.5 (संख्या ÷ 100) है।", fontSize = 12.sp)
    }
}

private fun mmss(sec: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", sec / 60, sec % 60)

@Composable
private fun FocusTimerPane() {
    var workMin by rememberSaveable { mutableIntStateOf(25) }
    var breakMin by rememberSaveable { mutableIntStateOf(5) }
    var isBreak by rememberSaveable { mutableStateOf(false) }
    var remaining by rememberSaveable { mutableIntStateOf(25 * 60) }
    var running by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (running && remaining <= 0) {
            running = false
            isBreak = !isBreak
            message = if (isBreak) "शाबाश! सेशन पूरा हुआ। अब ब्रेक लीजिए।" else "ब्रेक खत्म। अब फिर से पढ़ाई शुरू करें।"
            remaining = (if (isBreak) breakMin else workMin) * 60
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (isBreak) "ब्रेक का समय" else "पढ़ाई का समय", fontSize = 18.sp)
        Text(mmss(remaining), fontSize = 64.sp, fontWeight = FontWeight.Bold)
        message?.let { Text(it, textAlign = TextAlign.Center) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                message = null
                running = !running
            }, modifier = Modifier.height(52.dp)) {
                Text(if (running) "रोकें" else if (remaining < (if (isBreak) breakMin else workMin) * 60) "जारी रखें" else "शुरू करें")
            }
            OutlinedButton(onClick = {
                running = false
                isBreak = false
                message = null
                remaining = workMin * 60
            }, modifier = Modifier.height(52.dp)) { Text("रीसेट") }
        }
        Text("पढ़ाई: $workMin मिनट", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !running && workMin > 5, onClick = {
                workMin -= 5
                if (!isBreak) remaining = workMin * 60
            }) { Text("− 5") }
            OutlinedButton(enabled = !running && workMin < 90, onClick = {
                workMin += 5
                if (!isBreak) remaining = workMin * 60
            }) { Text("+ 5") }
        }
        Text("ब्रेक: $breakMin मिनट", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !running && breakMin > 1, onClick = {
                breakMin -= 1
                if (isBreak) remaining = breakMin * 60
            }) { Text("− 1") }
            OutlinedButton(enabled = !running && breakMin < 30, onClick = {
                breakMin += 1
                if (isBreak) remaining = breakMin * 60
            }) { Text("+ 1") }
        }
        Text(
            "ध्यान दें: यह टाइमर तभी चलता है जब ऐप खुला हो। ऐप बंद करने पर रुक जाता है। बैकग्राउंड नोटिफिकेशन अभी नहीं है।",
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
