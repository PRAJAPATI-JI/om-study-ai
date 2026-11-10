package com.omstudy.ai.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun openSafely(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "यह सेटिंग इस फोन पर नहीं खुल सकी। कृपया हाथ से खोलें।", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val theme by vm.themeMode.collectAsState()
    val savedName by vm.userName.collectAsState()
    val email by vm.email.collectAsState()
    var showLogout by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var showClear by remember { mutableStateOf(false) }

    LaunchedEffect(savedName) { if (name.isEmpty()) name = savedName }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("मेरा अकाउंट", fontWeight = FontWeight.SemiBold)
                Text("ईमेल: $email", fontSize = 14.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("आपका नाम") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    vm.setName(name)
                    Toast.makeText(ctx, "नाम सेव हो गया", Toast.LENGTH_SHORT).show()
                }) { Text("नाम सेव करें") }
                OutlinedButton(
                    onClick = { showLogout = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("लॉगआउट") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("थीम", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("फोन जैसा", "लाइट", "डार्क").forEachIndexed { i, label ->
                        FilterChip(selected = theme == i, onClick = { vm.setTheme(i) }, label = { Text(label) })
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("अलार्म भरोसेमंद रखने के लिए (Realme फोन)", fontWeight = FontWeight.SemiBold)
                Text(
                    "Realme फोन बैटरी बचाने के लिए ऐप्स को बंद कर सकता है। इसलिए ये सेटिंग्स बदलें " +
                        "(मेन्यू के नाम फोन के अनुसार थोड़े अलग हो सकते हैं):",
                    fontSize = 14.sp
                )
                Text(
                    "1. नीचे \"ऐप सेटिंग खोलें\" दबाइए → बैटरी उपयोग → \"बैकग्राउंड गतिविधि की अनुमति दें\" और \"ऑटो-लॉन्च\" चालू करें।\n" +
                        "2. \"बैटरी ऑप्टिमाइज़ेशन\" बटन दबाकर OM STUDY AI को \"ऑप्टिमाइज़ न करें\" पर रखें।\n" +
                        "3. रीसेंट ऐप्स खोलकर OM STUDY AI को लॉक कर दीजिए, ताकि \"सब बंद करें\" से वह साफ न हो।\n" +
                        "4. ऐप को कभी \"फोर्स स्टॉप\" न करें। फोर्स स्टॉप के बाद ऐप दोबारा खोलने तक अलार्म नहीं बजेंगे।",
                    fontSize = 14.sp
                )
                OutlinedButton(onClick = {
                    openSafely(
                        ctx,
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                }, modifier = Modifier.fillMaxWidth()) { Text("ऐप सेटिंग खोलें") }
                OutlinedButton(onClick = {
                    openSafely(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }, modifier = Modifier.fillMaxWidth()) { Text("बैटरी ऑप्टिमाइज़ेशन खोलें") }
                Text(
                    "अलार्म की आवाज़ फोन की \"अलार्म वॉल्यूम\" से तय होती है। उसे कम या साइलेंट न रखें।",
                    fontSize = 13.sp
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("गोपनीयता और डेटा", fontWeight = FontWeight.SemiBold)
                Text("अलार्म, काम और लॉग इस फोन में, आपके अकाउंट के नाम से अलग सेव होते हैं। दूसरे अकाउंट से लॉगिन करने पर वे नहीं दिखते। इंटरनेट सिर्फ लॉगिन और आपके नाम/ईमेल की प्रोफाइल के लिए इस्तेमाल होता है।", fontSize = 14.sp)
                OutlinedButton(
                    onClick = { showClear = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("इस अकाउंट का फोन-डेटा मिटाएँ", color = MaterialTheme.colorScheme.error) }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ऐप के बारे में", fontWeight = FontWeight.SemiBold)
                Text("OM STUDY AI · संस्करण 0.2.0 (दूसरा चरण)")
                Text("इस संस्करण में: अकाउंट (साइनअप/लॉगिन/पासवर्ड रीसेट/लॉगआउट), अलार्म, प्लानर, कैलकुलेटर, फोकस टाइमर, थीम।", fontSize = 13.sp)
                Text("अभी नहीं: AI शिक्षक, आवाज़, JAC/सरकारी परीक्षा सामग्री, क्विज़, बैकअप।", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("लॉगआउट करें?") },
            text = { Text("लॉगआउट करने पर इस फोन के सारे अलार्म बंद हो जाएँगे। दोबारा लॉगिन करते ही आपके अलार्म फिर चालू हो जाएँगे।") },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    vm.logout()
                }) { Text("हाँ, लॉगआउट") }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("रहने दें") } }
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("सारा डेटा मिटाएँ?") },
            text = { Text("इस अकाउंट के सारे अलार्म और काम इस फोन से हमेशा के लिए मिट जाएँगे। अकाउंट बना रहेगा। यह वापस नहीं हो सकता।") },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    vm.clearAll {
                        name = ""
                        Toast.makeText(ctx, "सारा डेटा मिटा दिया गया", Toast.LENGTH_LONG).show()
                    }
                }) { Text("हाँ, मिटाएँ") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("रहने दें") } }
        )
    }
}
