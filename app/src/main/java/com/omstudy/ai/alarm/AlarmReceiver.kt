package com.omstudy.ai.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** अलार्म का समय होते ही Android इसे चलाता है; यह अलार्म सर्विस शुरू कर देता है। */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_RING
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L))
            putExtra(AlarmScheduler.EXTRA_SNOOZE, intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false))
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
