@file:OptIn(ExperimentalMaterial3Api::class)

package com.omstudy.ai.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omstudy.ai.data.Learning
import com.omstudy.ai.data.SavedVideoEntity
import com.omstudy.ai.data.StudyPlanner
import java.time.LocalDate

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "लिंक नहीं खुल सका।", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "वापस") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** विषय का नाम लिखने की जगह, नीचे आपके विषयों के सुझाव (चिप) */
@Composable
fun SubjectField(value: String, onChange: (String) -> Unit, suggestions: List<String>, label: String = "विषय") {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = { onChange(it.take(60)) }, label = { Text(label) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (suggestions.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                suggestions.forEach { s ->
                    FilterChip(selected = value == s, onClick = { onChange(s) }, label = { Text(s) })
                }
            }
        }
    }
}

@Composable
fun rememberSubjectSuggestions(vm: MainViewModel): List<String> {
    val answers by vm.answers.collectAsState()
    val chapters by vm.chapters.collectAsState()
    return remember(answers, chapters) {
        val fromProfile = answers?.let { a -> StudyPlanner.buckets(a).flatMap { b -> b.subjects.map { s -> s.first } } }
            ?: emptyList()
        (fromProfile + chapters.map { c -> c.subject }).distinct()
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("रहने दें") } }
    )
}

/** "सीखें" टैब: सिलेबस, नोट्स, क्विज़, वीडियो, प्रगति और टूल्स */
@Composable
fun LearnHub(vm: MainViewModel) {
    var page by rememberSaveable { mutableStateOf("hub") }
    BackHandler(enabled = page != "hub") { page = "hub" }
    val back = { page = "hub" }
    when (page) {
        "syllabus" -> SyllabusScreen(vm, back)
        "notes" -> NotesScreen(vm, back)
        "quiz" -> QuizScreen(vm, back)
        "videos" -> VideosScreen(vm, back)
        "progress" -> ProgressScreen(vm, back)
        "tools" -> Column(Modifier.fillMaxSize()) {
            PageHeader("कैलकुलेटर और टाइमर", back)
            ToolsScreen()
        }
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("सीखें", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            listOf(
                Triple("syllabus", "मेरा सिलेबस", "चैप्टर जोड़ें और पूरे हुए चैप्टर चिह्नित करें"),
                Triple("notes", "मेरे नोट्स", "नोट लिखें, खोजें, तारा लगाएँ"),
                Triple("quiz", "क्विज़ और दोहराई", "अपने प्रश्न बनाकर अभ्यास करें"),
                Triple("videos", "वीडियो", "YouTube खोज और अपने सहेजे लिंक"),
                Triple("progress", "मेरी प्रगति", "पढ़ाई का समय, चैप्टर, क्विज़ का हाल"),
                Triple("tools", "कैलकुलेटर और टाइमर", "हिसाब और फोकस टाइमर")
            ).forEach { (key, title, sub) ->
                Card(Modifier.fillMaxWidth().clickable { page = key }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(sub, fontSize = 13.sp)
                    }
                }
            }
            Text(
                "ध्यान दें: इन सब में जो चैप्टर, नोट्स और प्रश्न हैं, वे आपने खुद जोड़े हैं। ये आधिकारिक सिलेबस या पिछले साल के प्रश्न नहीं हैं।",
                fontSize = 12.sp
            )
        }
    }
}

// ---------------- वीडियो ----------------

@Composable
fun VideosScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val videos by vm.videos.collectAsState()
    val suggestions = rememberSubjectSuggestions(vm)
    var subject by rememberSaveable { mutableStateOf("") }
    var chapter by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<SavedVideoEntity?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader("वीडियो", onBack)
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("YouTube पर खोजें", fontWeight = FontWeight.SemiBold)
                SubjectField(subject, { subject = it }, suggestions)
                OutlinedTextField(
                    value = chapter, onValueChange = { chapter = it.take(100) }, label = { Text("चैप्टर / टॉपिक") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = subject.isNotBlank() || chapter.isNotBlank(),
                    onClick = { openUrl(ctx, Learning.youtubeSearchUrl(subject, chapter)) }
                ) { Text("खोज खोलें") }
                Text("यह सिर्फ खोज का लिंक है। मैंने कोई वीडियो जाँचा नहीं है, इसलिए अच्छा वीडियो आप खुद चुनिए और नीचे सहेज लीजिए।", fontSize = 12.sp)
            }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("अपना वीडियो लिंक सहेजें") }
        if (videos.isEmpty()) Text("अभी कोई लिंक सहेजा नहीं है।")
        videos.forEach { v ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = v.watched, onCheckedChange = { vm.saveVideo(v.copy(watched = it)) })
                    Column(Modifier.weight(1f)) {
                        Text(v.title, fontWeight = FontWeight.SemiBold)
                        val tag = listOf(v.subject, v.chapter).filter { it.isNotBlank() }.joinToString(" · ")
                        if (tag.isNotBlank()) Text(tag, fontSize = 12.sp)
                    }
                    IconButton(onClick = { openUrl(ctx, v.url) }) { Icon(Icons.Default.PlayCircleOutline, "खोलें") }
                    IconButton(onClick = { toDelete = v }) { Icon(Icons.Default.Delete, "हटाएँ") }
                }
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var sub by remember { mutableStateOf(subject) }
        var chap by remember { mutableStateOf(chapter) }
        val valid = title.isNotBlank() && Learning.isValidUrl(url)
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("वीडियो लिंक सहेजें") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it.take(120) }, label = { Text("शीर्षक") }, singleLine = true)
                    OutlinedTextField(url, { url = it.trim() }, label = { Text("लिंक (https://...)") }, singleLine = true)
                    if (url.isNotEmpty() && !Learning.isValidUrl(url)) Text("लिंक https:// से शुरू होना चाहिए।", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    SubjectField(sub, { sub = it }, suggestions)
                    OutlinedTextField(chap, { chap = it.take(100) }, label = { Text("चैप्टर (वैकल्पिक)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    vm.saveVideo(SavedVideoEntity(ownerUid = "", title = title.trim(), url = url.trim(), subject = sub.trim(), chapter = chap.trim()))
                    showAdd = false
                }) { Text("सहेजें") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("रद्द") } }
        )
    }
    toDelete?.let { v ->
        ConfirmDialog("लिंक हटाएँ?", v.title, "हाँ, हटाएँ", { vm.deleteVideo(v) }) { toDelete = null }
    }
}

// ---------------- प्रगति ----------------

@Composable
fun ProgressScreen(vm: MainViewModel, onBack: () -> Unit) {
    val chapters by vm.chapters.collectAsState()
    val answers by vm.quizAnswers.collectAsState()
    val doneTasks by vm.doneTasksWeek.collectAsState()

    val minutes = doneTasks.sumOf { it.durationMin }
    val (doneCh, totalCh) = Learning.chapterProgress(chapters)
    val acc = Learning.subjectAccuracy(answers)
    val weak = Learning.weakTopics(answers)
    val today = LocalDate.now().toEpochDay()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader("मेरी प्रगति", onBack)

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("पिछले 7 दिन की पढ़ाई", fontWeight = FontWeight.SemiBold)
                Text("पूरे किए सत्र: ${doneTasks.size} · कुल समय: ${minutes / 60} घंटे ${minutes % 60} मिनट")
                (0..6).map { today - 6 + it }.forEach { d ->
                    val m = doneTasks.filter { it.epochDay == d }.sumOf { it.durationMin }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtDate(d), Modifier.width(110.dp), fontSize = 12.sp)
                        LinearProgressIndicator(progress = { (m / 180f).coerceAtMost(1f) }, modifier = Modifier.weight(1f))
                        Text(" $m मि", fontSize = 12.sp)
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("सिलेबस के चैप्टर", fontWeight = FontWeight.SemiBold)
                if (totalCh == 0) Text("अभी कोई चैप्टर नहीं जोड़ा। \"सीखें → मेरा सिलेबस\" में जोड़िए।")
                else {
                    Text("$doneCh / $totalCh पूरे")
                    LinearProgressIndicator(progress = { doneCh.toFloat() / totalCh }, modifier = Modifier.fillMaxWidth())
                    chapters.groupBy { it.subject }.forEach { (s, list) ->
                        val (d, t) = Learning.chapterProgress(list)
                        Text("$s: $d / $t", fontSize = 13.sp)
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("क्विज़ का हाल", fontWeight = FontWeight.SemiBold)
                if (acc.isEmpty()) Text("अभी कोई क्विज़ नहीं दिया।")
                acc.forEach { (s, p) ->
                    val pct = if (p.second == 0) 0 else p.first * 100 / p.second
                    Text("$s: ${p.first} सही / ${p.second} उत्तर ($pct%)", fontSize = 14.sp)
                }
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("इन्हें दोहराइए", fontWeight = FontWeight.SemiBold)
                if (weak.isEmpty()) {
                    Text("अभी कोई टॉपिक कमज़ोर नहीं दिख रहा। (किसी टॉपिक को तभी गिना जाता है जब आखिरी उत्तरों में से कम से कम 3 हों और आधे से ज़्यादा गलत हों।)", fontSize = 13.sp)
                } else {
                    weak.forEach { (s, c) -> Text("• $s${if (c.isNotBlank()) " · $c" else ""}") }
                    Text("एक-दो गलतियों से कोई टॉपिक कमज़ोर नहीं गिना जाता। सही उत्तर देते ही वह इस सूची से हट जाएगा।", fontSize = 12.sp)
                }
            }
        }
    }
}
