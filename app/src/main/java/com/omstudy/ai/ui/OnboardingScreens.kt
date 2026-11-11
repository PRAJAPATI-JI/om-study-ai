package com.omstudy.ai.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omstudy.ai.data.Keys
import com.omstudy.ai.data.Onboarding
import com.omstudy.ai.data.Question
import com.omstudy.ai.data.QType
import com.omstudy.ai.data.StudyPlanner
import java.time.LocalDate

/** जवाब का पढ़ने लायक रूप (जैसे "10" की जगह "कक्षा 10") */
fun answerText(q: Question, raw: String?): String {
    if (raw.isNullOrEmpty()) return "-"
    if (raw == Onboarding.SKIP) return "छोड़ा गया"
    val values = if (q.type == QType.MULTI) Onboarding.splitMulti(raw) else listOf(raw)
    return values.joinToString(", ") { v -> q.options.firstOrNull { it.value == v }?.label ?: v }
}

/** onboarding: बिना-जवाब वाला पहला सवाल, या सारे जवाब भर जाने पर समीक्षा स्क्रीन */
@Composable
fun OnboardingFlow(vm: MainViewModel, answers: Map<String, String>) {
    Surface(Modifier.fillMaxSize()) {
        val q = Onboarding.firstUnanswered(answers)
        if (q != null) QuestionScreen(vm, answers, q) else ReviewScreen(vm, answers)
    }
}

@Composable
private fun QuestionScreen(vm: MainViewModel, answers: Map<String, String>, q: Question) {
    val (done, total) = Onboarding.progress(answers)
    val all = Onboarding.questionsFor(answers)
    val index = all.indexOfFirst { it.key == q.key }
    // हर सवाल के लिए अलग चुनाव-स्थिति (सवाल बदलने पर दोबारा शुरू)
    var picked by remember(q.key) { mutableStateOf(Onboarding.splitMulti(answers[q.key]).toSet()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("सवाल ${done + 1} / $total", fontSize = 13.sp)
        LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(q.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (q.hint.isNotBlank()) Text(q.hint, fontSize = 14.sp)

        if (q.type == QType.SINGLE) {
            q.options.forEach { o ->
                Card(Modifier.fillMaxWidth().clickable { vm.saveAnswer(q.key, o.value) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = false, onClick = { vm.saveAnswer(q.key, o.value) })
                        Text(o.label, fontSize = 17.sp)
                    }
                }
            }
        } else {
            q.options.forEach { o ->
                val on = o.value in picked
                val toggle = { picked = if (on) picked - o.value else picked + o.value }
                Card(Modifier.fillMaxWidth().clickable { toggle() }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = { toggle() })
                        Text(o.label, fontSize = 17.sp)
                    }
                }
            }
            Button(
                enabled = picked.isNotEmpty(),
                onClick = {
                    // विकल्पों के क्रम में सेव, ताकि क्रम हमेशा एक जैसा रहे
                    val ordered = q.options.map { it.value }.filter { it in picked }
                    vm.saveAnswer(q.key, Onboarding.joinMulti(ordered))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("आगे", fontSize = 17.sp) }
        }

        if (q.optional) {
            TextButton(onClick = { vm.saveAnswer(q.key, Onboarding.SKIP) }) { Text("यह सवाल छोड़ें") }
        }
        if (index > 0) {
            TextButton(onClick = { vm.clearAnswer(all[index - 1].key) }) { Text("← पिछला सवाल") }
        }
        Text(
            "आपका जवाब तुरंत इस फोन में आपके अकाउंट के नाम से सेव हो जाता है। ऐप बंद करके खोलेंगे तो यहीं से आगे बढ़ेंगे।",
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ReviewScreen(vm: MainViewModel, answers: Map<String, String>) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val plan = remember(answers) {
        StudyPlanner.generate(answers, today.toEpochDay(), today.dayOfWeek.value - 1, 7)
    }
    val qs = Onboarding.questionsFor(answers)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("आपकी जानकारी", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("सब ठीक है? कुछ गलत हो तो \"बदलें\" दबाइए।", fontSize = 14.sp)

        qs.forEach { q ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(q.title, fontSize = 13.sp)
                        Text(answerText(q, answers[q.key]), fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { vm.clearAnswer(q.key) }) { Text("बदलें") }
                }
            }
        }

        Text("सुझाई गई पहले 7 दिन की योजना", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (plan.isEmpty()) {
            Text("आपके जवाबों से अभी कोई योजना नहीं बन पाई। कृपया दिन और समय वाले जवाब जाँचिए।")
        } else {
            val byDay = plan.groupBy { it.epochDay }
            byDay.entries.sortedBy { it.key }.forEach { (day, items) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(fmtDate(day), fontWeight = FontWeight.SemiBold)
                        items.forEach {
                            Text(
                                "${fmtMinutes(it.startMinutes)} · ${it.durationMin} मिनट · ${it.subject}" +
                                    if (it.isRevision) "" else " (${it.goal})",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
        Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ज़रूरी बात", fontWeight = FontWeight.SemiBold)
                Text(
                    "• यह सिर्फ एक सुझाव है, किसी बोर्ड या परीक्षा का आधिकारिक सिलेबस नहीं। चैप्टर-वार योजना तब बनेगी जब जाँचा हुआ सिलेबस ऐप में जुड़ेगा।\n" +
                        "• योजना से अंक, रैंक या नौकरी की कोई गारंटी नहीं है।\n" +
                        "• योजना चालू करने पर ये काम प्लानर में जुड़ जाएँगे। अलार्म अपने-आप नहीं लगेंगे। चाहें तो अलार्म टैब से लगाइए।\n" +
                        "• आप योजना बाद में फिर से बना सकते हैं।",
                    fontSize = 13.sp
                )
            }
        }

        Button(
            enabled = plan.isNotEmpty(),
            onClick = {
                vm.activatePlan { n -> Toast.makeText(ctx, "$n काम प्लानर में जुड़ गए", Toast.LENGTH_LONG).show() }
                vm.confirmOnboarding()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("योजना चालू करें", fontSize = 17.sp) }
        OutlinedButton(
            onClick = { vm.confirmOnboarding() },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("अभी योजना नहीं चाहिए, सीधे ऐप खोलें", fontSize = 15.sp) }
        Spacer(Modifier.height(12.dp))
    }
}
