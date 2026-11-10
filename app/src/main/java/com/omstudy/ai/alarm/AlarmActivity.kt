package com.omstudy.ai.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.omstudy.ai.MainActivity
import com.omstudy.ai.data.AlarmEntity
import com.omstudy.ai.data.AppDatabase
import com.omstudy.ai.ui.OmTheme
import com.omstudy.ai.ui.fmtTime

/** अलार्म बजने पर दिखने वाली बड़ी स्क्रीन (लॉक स्क्रीन के ऊपर भी खुलती है)। */
class AlarmActivity : ComponentActivity() {

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    private var alarmId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ContextCompat.registerReceiver(
            this, closeReceiver, IntentFilter(AlarmService.ACTION_CLOSE_UI), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        setContent {
            OmTheme(isSystemInDarkTheme()) {
                AlarmScreen(alarmId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(closeReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun send(action: String, minutes: Int = 5) {
        startService(
            Intent(this, AlarmService::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmService.EXTRA_SNOOZE_MIN, minutes)
        )
    }

    @Composable
    private fun AlarmScreen(id: Long) {
        val alarm: AlarmEntity? by produceState<AlarmEntity?>(null, id) {
            value = if (id >= 0) AppDatabase.get(applicationContext).alarmDao().get(id) else null
        }
        val title = alarm?.let { it.title.ifBlank { it.subject } } ?: "टेस्ट अलार्म"
        val subject = alarm?.subject ?: "जाँच"

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                alarm?.let {
                    Text(fmtTime(it.hour, it.minute), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("विषय: $subject", fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "नमस्ते! अब $subject पढ़ने का समय है।",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        startActivity(
                            Intent(this@AlarmActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        send(AlarmService.ACTION_STARTED)
                        finish()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("पढ़ना शुरू करें", fontSize = 18.sp) }
                Spacer(Modifier.height(16.dp))
                Text("स्नूज़ (दोबारा याद दिलाएँ)", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(5, 10, 15).forEach { m ->
                        OutlinedButton(
                            onClick = {
                                send(AlarmService.ACTION_SNOOZE, m)
                                finish()
                            },
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) { Text("$m मिनट") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        send(AlarmService.ACTION_DISMISS)
                        finish()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("बंद करें", fontSize = 16.sp) }
            }
        }
    }
}
