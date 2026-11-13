@file:OptIn(ExperimentalMaterial3Api::class)

package com.omstudy.ai.ui

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
import com.omstudy.ai.data.ChapterEntity
import com.omstudy.ai.data.Learning
import com.omstudy.ai.data.NoteEntity
import com.omstudy.ai.data.QuestionEntity

// ---------------- सिलेबस ----------------

@Composable
fun SyllabusScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val chapters by vm.chapters.collectAsState()
    val suggestions = rememberSubjectSuggestions(vm)
    var showAdd by remember { mutableStateOf(false) }
    var showBulk by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ChapterEntity?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader("मेरा सिलेबस", onBack)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                "यह सूची आपने खुद बनाई है, यह किसी बोर्ड या परीक्षा का आधिकारिक सिलेबस नहीं है। अपनी किताब या बोर्ड की वेबसाइट से मिलाकर जोड़िए।",
                Modifier.padding(12.dp), fontSize = 13.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }) { Text("चैप्टर जोड़ें") }
            OutlinedButton(onClick = { showBulk = true }) { Text("सूची चिपकाएँ") }
        }
        if (chapters.isEmpty()) Text("अभी कोई चैप्टर नहीं है।")
        chapters.groupBy { it.subject }.forEach { (subject, list) ->
            val (d, t) = Learning.chapterProgress(list)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$subject  ($d / $t)", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    LinearProgressIndicator(progress = { if (t == 0) 0f else d.toFloat() / t }, modifier = Modifier.fillMaxWidth())
                    list.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = c.done, onCheckedChange = { vm.toggleChapter(c, it) })
                            Text(c.title, Modifier.weight(1f))
                            IconButton(onClick = { openUrl(ctx, Learning.youtubeSearchUrl(c.subject, c.title)) }) {
                                Icon(Icons.Default.PlayCircleOutline, "वीडियो खोजें")
                            }
                            IconButton(onClick = { toDelete = c }) { Icon(Icons.Default.Delete, "हटाएँ") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var sub by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("चैप्टर जोड़ें") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectField(sub, { sub = it }, suggestions)
                    OutlinedTextField(title, { title = it.take(Learning.MAX_TITLE) }, label = { Text("चैप्टर का नाम") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = sub.isNotBlank() && title.isNotBlank(), onClick = {
                    vm.addChapters(sub, listOf(title)); showAdd = false
                }) { Text("जोड़ें") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("रद्द") } }
        )
    }
    if (showBulk) {
        var sub by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        val parsed = remember(text) { Learning.parseBulk(text) }
        AlertDialog(
            onDismissRequest = { showBulk = false },
            title = { Text("चैप्टर की सूची चिपकाएँ") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectField(sub, { sub = it }, suggestions)
                    OutlinedTextField(
                        text, { text = it.take(20000) }, label = { Text("हर लाइन में एक चैप्टर") },
                        minLines = 5, modifier = Modifier.fillMaxWidth()
                    )
                    Text("${parsed.size} चैप्टर मिले (क्रमांक/बुलेट हटा दिए जाते हैं, अधिकतम ${Learning.MAX_BULK_LINES})", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = sub.isNotBlank() && parsed.isNotEmpty(), onClick = {
                    vm.addChapters(sub, parsed); showBulk = false
                }) { Text("जोड़ें") }
            },
            dismissButton = { TextButton(onClick = { showBulk = false }) { Text("रद्द") } }
        )
    }
    toDelete?.let { c -> ConfirmDialog("चैप्टर हटाएँ?", c.title, "हाँ, हटाएँ", { vm.deleteChapter(c) }) { toDelete = null } }
}

// ---------------- नोट्स ----------------

@Composable
fun NotesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val notes by vm.notes.collectAsState()
    val suggestions = rememberSubjectSuggestions(vm)
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<NoteEntity?>(null) }

    val shown = remember(notes, query) {
        val q = query.trim()
        if (q.isEmpty()) notes else notes.filter {
            it.title.contains(q, true) || it.body.contains(q, true) || it.subject.contains(q, true) || it.chapter.contains(q, true)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader("मेरे नोट्स", onBack)
        OutlinedTextField(
            query, { query = it.take(60) }, label = { Text("नोट्स में खोजें") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) { Text("नया नोट") }
        if (shown.isEmpty()) Text(if (notes.isEmpty()) "अभी कोई नोट नहीं है।" else "कुछ नहीं मिला।")
        shown.forEach { n ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title.ifBlank { "(बिना शीर्षक)" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        IconButton(onClick = { vm.saveNote(n.copy(bookmarked = !n.bookmarked)) }) {
                            Icon(if (n.bookmarked) Icons.Default.Star else Icons.Default.StarBorder, "तारा")
                        }
                        IconButton(onClick = { editing = n; showEditor = true }) { Icon(Icons.Default.Edit, "बदलें") }
                        IconButton(onClick = { toDelete = n }) { Icon(Icons.Default.Delete, "हटाएँ") }
                    }
                    val tag = listOf(n.subject, n.chapter).filter { it.isNotBlank() }.joinToString(" · ")
                    if (tag.isNotBlank()) Text(tag, fontSize = 12.sp)
                    Text(n.body, maxLines = 4, fontSize = 14.sp)
                }
            }
        }
    }

    if (showEditor) {
        val base = editing
        var title by remember { mutableStateOf(base?.title ?: "") }
        var sub by remember { mutableStateOf(base?.subject ?: "") }
        var chap by remember { mutableStateOf(base?.chapter ?: "") }
        var body by remember { mutableStateOf(base?.body ?: "") }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (base == null) "नया नोट" else "नोट बदलें") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it.take(100) }, label = { Text("शीर्षक") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SubjectField(sub, { sub = it }, suggestions)
                    OutlinedTextField(chap, { chap = it.take(100) }, label = { Text("चैप्टर (वैकल्पिक)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { body = it.take(5000) }, label = { Text("नोट") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() || body.isNotBlank(), onClick = {
                    val n = base ?: NoteEntity(ownerUid = "", title = "", body = "")
                    vm.saveNote(n.copy(title = title.trim(), subject = sub.trim(), chapter = chap.trim(), body = body.trim()))
                    showEditor = false
                }) { Text("सेव") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("रद्द") } }
        )
    }
    toDelete?.let { n -> ConfirmDialog("नोट हटाएँ?", n.title.ifBlank { "यह नोट" }, "हाँ, हटाएँ", { vm.deleteNote(n) }) { toDelete = null } }
}

// ---------------- क्विज़ ----------------

@Composable
fun QuizScreen(vm: MainViewModel, onBack: () -> Unit) {
    val questions by vm.questions.collectAsState()
    val answers by vm.quizAnswers.collectAsState()
    var mode by rememberSaveable { mutableStateOf("home") }
    var filter by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf(5) }
    var session by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }

    when (mode) {
        "run" -> QuizRun(vm, session) { mode = "home" }
        "bank" -> QuestionBank(vm) { mode = "home" }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageHeader("क्विज़ और दोहराई", onBack)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    "ये प्रश्न आपने खुद बनाए हैं। ये आधिकारिक पिछले साल के प्रश्न नहीं हैं। पहले वे प्रश्न आते हैं जो पिछली बार गलत हुए थे।",
                    Modifier.padding(12.dp), fontSize = 13.sp
                )
            }
            Text("कुल प्रश्न: ${questions.size} · अब तक के उत्तर: ${answers.size}")
            val subjects = questions.map { it.subject }.distinct()
            if (subjects.isNotEmpty()) {
                Text("विषय चुनिए (खाली = सब)", fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filter.isEmpty(), onClick = { filter = "" }, label = { Text("सब") })
                    subjects.forEach { s -> FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10).forEach { n ->
                    FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n प्रश्न") })
                }
            }
            Button(
                enabled = questions.isNotEmpty(),
                onClick = {
                    session = Learning.pickQuiz(questions, answers, count, filter)
                    if (session.isNotEmpty()) mode = "run"
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("क्विज़ शुरू करें", fontSize = 17.sp) }
            OutlinedButton(onClick = { mode = "bank" }, modifier = Modifier.fillMaxWidth()) { Text("मेरे प्रश्न (जोड़ें / बदलें)") }
            if (questions.isEmpty()) Text("क्विज़ के लिए पहले \"मेरे प्रश्न\" में कुछ प्रश्न जोड़िए।")
        }
    }
}

@Composable
private fun QuizRun(vm: MainViewModel, session: List<QuestionEntity>, onExit: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf(-1) }
    var score by remember { mutableStateOf(0) }
    var wrong by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    androidx.activity.compose.BackHandler { onExit() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (index >= session.size) {
            Text("क्विज़ पूरा!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("आपके $score सही / ${session.size} प्रश्न", fontSize = 18.sp)
            Text(
                when {
                    score == session.size -> "बहुत बढ़िया! सारे सही।"
                    score * 2 >= session.size -> "अच्छा प्रयास। गलत वाले दोबारा देख लीजिए।"
                    else -> "कोई बात नहीं, अभ्यास से सुधार होता है। गलत वाले दोबारा देखिए।"
                }
            )
            if (wrong.isNotEmpty()) {
                Text("जो गलत हुए:", fontWeight = FontWeight.SemiBold)
                wrong.forEach { q ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(q.text, fontWeight = FontWeight.SemiBold)
                            Text("सही उत्तर: ${q.options()[q.correct]}")
                            if (q.explanation.isNotBlank()) Text(q.explanation, fontSize = 13.sp)
                        }
                    }
                }
            }
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("वापस") }
        } else {
            val q = session[index]
            Text("प्रश्न ${index + 1} / ${session.size}", fontSize = 13.sp)
            LinearProgressIndicator(progress = { index.toFloat() / session.size }, modifier = Modifier.fillMaxWidth())
            Text(q.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            q.options().forEachIndexed { i, opt ->
                val answered = picked >= 0
                val colors = when {
                    !answered -> ButtonDefaults.outlinedButtonColors()
                    i == q.correct -> ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    i == picked -> ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    else -> ButtonDefaults.outlinedButtonColors()
                }
                OutlinedButton(
                    enabled = !answered,
                    colors = colors,
                    onClick = {
                        picked = i
                        val ok = i == q.correct
                        if (ok) score++ else wrong = wrong + q
                        vm.recordAnswer(q, ok)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("${"ABCD"[i]}. $opt", fontSize = 16.sp) }
            }
            if (picked >= 0) {
                Text(if (picked == q.correct) "सही!" else "गलत। सही उत्तर: ${q.options()[q.correct]}", fontWeight = FontWeight.Bold)
                if (q.explanation.isNotBlank()) Text(q.explanation, fontSize = 14.sp)
                Button(onClick = { index++; picked = -1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (index == session.size - 1) "नतीजा देखें" else "अगला")
                }
            }
            TextButton(onClick = onExit) { Text("क्विज़ छोड़ें") }
        }
    }
}

@Composable
private fun QuestionBank(vm: MainViewModel, onBack: () -> Unit) {
    val questions by vm.questions.collectAsState()
    val suggestions = rememberSubjectSuggestions(vm)
    var editing by remember { mutableStateOf<QuestionEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<QuestionEntity?>(null) }
    androidx.activity.compose.BackHandler { onBack() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader("मेरे प्रश्न", onBack)
        Button(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) { Text("नया प्रश्न") }
        if (questions.isEmpty()) Text("अभी कोई प्रश्न नहीं है।")
        questions.forEach { q ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(q.text, maxLines = 2, fontWeight = FontWeight.SemiBold)
                        Text(listOf(q.subject, q.chapter).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp)
                    }
                    IconButton(onClick = { editing = q; showEditor = true }) { Icon(Icons.Default.Edit, "बदलें") }
                    IconButton(onClick = { toDelete = q }) { Icon(Icons.Default.Delete, "हटाएँ") }
                }
            }
        }
    }

    if (showEditor) {
        val base = editing
        var sub by remember { mutableStateOf(base?.subject ?: "") }
        var chap by remember { mutableStateOf(base?.chapter ?: "") }
        var text by remember { mutableStateOf(base?.text ?: "") }
        val opts = remember { mutableStateListOf(base?.optA ?: "", base?.optB ?: "", base?.optC ?: "", base?.optD ?: "") }
        var correct by remember { mutableStateOf(base?.correct ?: 0) }
        var expl by remember { mutableStateOf(base?.explanation ?: "") }
        val problem = Learning.validateQuestion(text, opts.toList(), correct)
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (base == null) "नया प्रश्न" else "प्रश्न बदलें") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectField(sub, { sub = it }, suggestions)
                    OutlinedTextField(chap, { chap = it.take(100) }, label = { Text("चैप्टर (वैकल्पिक)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(text, { text = it.take(500) }, label = { Text("प्रश्न") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    opts.forEachIndexed { i, v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = correct == i, onClick = { correct = i })
                            OutlinedTextField(
                                v, { opts[i] = it.take(200) }, label = { Text("विकल्प ${"ABCD"[i]}") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text("जिस विकल्प के आगे गोला भरा है, वही सही उत्तर है।", fontSize = 12.sp)
                    OutlinedTextField(expl, { expl = it.take(500) }, label = { Text("समझाइश (वैकल्पिक)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    if (problem != null && text.isNotBlank()) Text(problem, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = problem == null, onClick = {
                    val b = base ?: QuestionEntity(ownerUid = "", subject = "", chapter = "", text = "", optA = "", optB = "", optC = "", optD = "", correct = 0)
                    vm.saveQuestion(
                        b.copy(
                            subject = sub.trim().ifBlank { "अन्य" }, chapter = chap.trim(), text = text.trim(),
                            optA = opts[0].trim(), optB = opts[1].trim(), optC = opts[2].trim(), optD = opts[3].trim(),
                            correct = correct, explanation = expl.trim()
                        )
                    )
                    showEditor = false
                }) { Text("सेव") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("रद्द") } }
        )
    }
    toDelete?.let { q -> ConfirmDialog("प्रश्न हटाएँ?", q.text, "हाँ, हटाएँ", { vm.deleteQuestion(q) }) { toDelete = null } }
}
