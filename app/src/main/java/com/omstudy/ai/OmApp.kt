package com.omstudy.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.omstudy.ai.alarm.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OmApp : Application() {
    companion object {
        const val CHANNEL_ALARM = "om_alarm"
        const val CHANNEL_MISSED = "om_missed"
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "स्टडी अलार्म", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "पढ़ाई के अलार्म (आवाज़ ऐप खुद बजाता है)"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MISSED, "छूटे अलार्म", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "जो अलार्म आपने नहीं सुना"
            }
        )
        // ऐप खुलने पर हर बार चालू अलार्म दोबारा सेट कर लेते हैं (सुरक्षा के लिए)
        CoroutineScope(Dispatchers.IO).launch { AlarmScheduler.rescheduleAll(applicationContext) }
    }
}
