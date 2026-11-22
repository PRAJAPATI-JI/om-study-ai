package com.omstudy.ai.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.omstudy.ai.data.AiClient
import com.omstudy.ai.data.AiException
import com.omstudy.ai.data.AiRules
import com.omstudy.ai.data.AuthRepository
import com.omstudy.ai.data.Prefs
import com.omstudy.ai.data.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class ChatMsg(val role: String, val text: String, val note: String = "")

private fun errText(e: Throwable): String {
    val ex = e as? AiException ?: return AiRules.friendlyCode("network")
    val friendly = AiRules.friendlyCode(ex.code)
    return friendly
}

/** AI शिक्षक चैट। बातचीत सिर्फ इस स्क्रीन के खुले रहने तक रहती है (अभी फोन में सेव नहीं होती)। */
@Composable
fun AiScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val server by ctx.dataStore.data.map { it[Prefs.SERVER_URL].orEmpty() }.collectAsState(initial = "")
    val msgs = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    if (server.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI शिक्षक", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("AI अभी चालू नहीं है। इसके लिए मालिक को अपना AI सर्वर बनाकर उसका पता सेटिंग्स में डालना होता है। बिना सर्वर के ऐप के बाकी सारे हिस्से (अलार्म, प्लानर, सीखें) पहले जैसे चलते हैं।")
        }
        return
    }

    fun send() {
        val q = input.trim().take(AiRules.MAX_INPUT)
        if (q.isEmpty() || busy) return
        msgs.add(ChatMsg("user", q)); input = ""; busy = true
        val history = msgs.map { it.role to it.text }
        val from = AiRules.trimHistory(history.map { it.first })
        scope.launch {
            AiClient.chat(ctx, history.drop(from)).fold(
                onSuccess = { r -> msgs.add(ChatMsg("assistant", r.text.ifBlank { "(खाली जवाब)" }, if (r.fallback) "दूसरी AI सेवा से मिला" else "")) },
                onFailure = { e -> msgs.add(ChatMsg("error", errText(e))) }
            )
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI शिक्षक", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("AI की बात हमेशा सही नहीं होती। परीक्षा की ज़रूरी बातें किताब/शिक्षक से जाँचिए। अपना पूरा नाम, पासवर्ड या निजी जानकारी यहाँ न लिखें।", fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                val container = when (m.role) {
                    "user" -> MaterialTheme.colorScheme.primaryContainer
                    "error" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(m.text)
                        if (m.note.isNotEmpty()) Text(m.note, fontSize = 11.sp)
                    }
                }
            }
            if (busy) item { Text("सोच रहा हूँ…") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input, onValueChange = { input = it.take(AiRules.MAX_INPUT) },
                label = { Text("अपना सवाल लिखिए") }, modifier = Modifier.weight(1f), maxLines = 4
            )
            Button(onClick = { send() }, enabled = !busy && input.isNotBlank()) { Text("भेजें") }
        }
    }
}

/** सेटिंग्स का हिस्सा: AI सर्वर का पता, UID, और (सिर्फ मालिक को) Admin बटन */
@Composable
fun AiSettingsCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid by AuthRepository.uid.collectAsState()
    val saved by ctx.dataStore.data.map { it[Prefs.SERVER_URL].orEmpty() }.collectAsState(initial = "")
    var field by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(saved) { if (field.isEmpty()) field = saved }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI सर्वर", fontWeight = FontWeight.SemiBold)
            Text("AI चलाने के लिए आपका अपना सर्वर चाहिए। उसका https:// वाला पता यहाँ डालिए। खाली छोड़ने पर AI बंद रहता है।", fontSize = 13.sp)
            OutlinedTextField(
                value = field, onValueChange = { field = it.take(200) }, label = { Text("सर्वर का पता (https://…)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val clean = if (field.isBlank()) "" else AiRules.normalizeServerUrl(field)
                    if (clean == null) {
                        Toast.makeText(ctx, "पता https:// से शुरू होना चाहिए, जैसे https://नाम.workers.dev", Toast.LENGTH_LONG).show()
                    } else {
                        scope.launch {
                            ctx.dataStore.edit { it[Prefs.SERVER_URL] = clean }
                            field = clean; status = "सेव हो गया।"
                        }
                    }
                }) { Text("सेव करें") }
                OutlinedButton(onClick = {
                    status = "जाँच रहा हूँ…"
                    scope.launch {
                        AiClient.me(ctx).fold(
                            onSuccess = { me ->
                                isAdmin = me.isAdmin
                                status = "सर्वर से जुड़ गया। AI " + (if (me.textReady) "चालू है।" else "अभी चालू नहीं (मालिक ने कोई सेवा चालू नहीं की)।")
                            },
                            onFailure = { e -> isAdmin = false; status = errText(e) }
                        )
                    }
                }) { Text("कनेक्शन जाँचें") }
            }
            if (status.isNotEmpty()) Text(status, fontSize = 13.sp)
            if (isAdmin && saved.isNotEmpty()) {
                OutlinedButton(onClick = {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(saved))) }
                    catch (e: Exception) { Toast.makeText(ctx, "ब्राउज़र नहीं खुल सका।", Toast.LENGTH_LONG).show() }
                }, modifier = Modifier.fillMaxWidth()) { Text("Owner Admin Panel खोलें (ब्राउज़र में)") }
            }
            Text("आपकी UID (मालिक बनने के लिए सर्वर की OWNER_UID में यही डालनी होती है):", fontSize = 12.sp)
            androidx.compose.foundation.text.selection.SelectionContainer { Text(uid.orEmpty(), fontSize = 13.sp) }
        }
    }
}
