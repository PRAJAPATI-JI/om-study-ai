package com.omstudy.ai.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** फोन रीस्टार्ट / समय बदलने / ऐप अपडेट के बाद अलार्म दोबारा लगाता है। */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
