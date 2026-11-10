package com.omstudy.ai.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.omstudy.ai.OmApp
import com.omstudy.ai.R
import com.omstudy.ai.data.AlarmLogEntity
import com.omstudy.ai.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * अलार्म की आवाज़, वाइब्रेशन और नोटिफिकेशन यहीं चलते हैं।
 * अलार्म सिर्फ 60 सेकंड बजता है, फिर अपने-आप रुककर "छूटा अलार्म" दिखाता है (अंतहीन लूप नहीं)।
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_RING = "com.omstudy.ai.RING"
        const val ACTION_SNOOZE = "com.omstudy.ai.SNOOZE"
        const val ACTION_DISMISS = "com.omstudy.ai.DISMISS"
        const val ACTION_STARTED = "com.omstudy.ai.STARTED"
        const val ACTION_CLOSE_UI = "com.omstudy.ai.CLOSE_UI"
        const val EXTRA_SNOOZE_MIN = "snooze_min"
        const val NOTIF_ID = 1001
        const val RING_TIMEOUT_MS = 60_000L
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ringing = false
    private var currentId = -1L
    private var currentTitle = ""
    private val timeoutRunnable = Runnable { onTimeout() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_RING -> ring(alarmId, intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false))
            ACTION_SNOOZE -> finishWith(alarmId, "snooze", intent.getIntExtra(EXTRA_SNOOZE_MIN, 5))
            ACTION_DISMISS -> finishWith(alarmId, "dismiss", 0)
            ACTION_STARTED -> finishWith(alarmId, "started_study", 0)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(alarmId: Long, isSnooze: Boolean) {
        if (ringing && currentId >= 0) logAction(currentId, currentTitle, "missed")
        stopSound()
        ringing = true
        currentId = alarmId
        currentTitle = "पढ़ाई का समय"
        startForeground(NOTIF_ID, buildNotification(alarmId, "पढ़ाई का समय", "OM STUDY AI"))
        startSound()
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, RING_TIMEOUT_MS)

        val appCtx = applicationContext
        scope.launch {
            val db = AppDatabase.get(appCtx)
            val alarm = if (alarmId >= 0) db.alarmDao().get(alarmId) else null
            val title = alarm?.let { it.title.ifBlank { it.subject } } ?: "टेस्ट अलार्म"
            val subject = alarm?.subject ?: "जाँच"
            currentTitle = title
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                NOTIF_ID,
                buildNotification(alarmId, title, "नमस्ते! अब $subject पढ़ने का समय है।")
            )
            if (alarm == null || alarm.vibrate) handler.post { startVibration() }
            if (alarm != null) {
                db.logDao().insert(
                    AlarmLogEntity(
                        ownerUid = alarm.ownerUid, alarmId = alarmId, title = title,
                        firedAt = System.currentTimeMillis(), action = "fired"
                    )
                )
                if (!isSnooze) {
                    if (alarm.daysMask == 0) {
                        // एक बार वाला अलार्म बज चुका, उसे बंद कर दो
                        db.alarmDao().setEnabled(alarm.id, false, System.currentTimeMillis())
                    } else {
                        AlarmScheduler.schedule(appCtx, alarm) // अगला दिन तय करो
                    }
                }
            }
        }
    }

    private fun finishWith(alarmId: Long, action: String, snoozeMin: Int) {
        val title = if (alarmId == currentId && currentTitle.isNotEmpty()) currentTitle else ""
        stopRinging()
        val appCtx = applicationContext
        scope.launch {
            if (action == "snooze" && alarmId != -2L) {
                AlarmScheduler.scheduleSnooze(appCtx, alarmId, snoozeMin)
            }
            if (alarmId >= 0) {
                val a = AppDatabase.get(appCtx).alarmDao().get(alarmId)
                if (a != null) {
                    val t = title.ifEmpty { a.title.ifBlank { a.subject } }
                    AppDatabase.get(appCtx).logDao().insert(
                        AlarmLogEntity(
                            ownerUid = a.ownerUid, alarmId = alarmId, title = t,
                            firedAt = System.currentTimeMillis(), action = action
                        )
                    )
                }
            }
            stopSelf()
        }
    }

    private fun onTimeout() {
        val id = currentId
        val title = currentTitle
        stopRinging()
        if (id >= 0) logAction(id, title, "missed")
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openPi = PendingIntent.getActivity(
            this, 5, Intent(this, com.omstudy.ai.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            3000 + (id.toInt().coerceAtLeast(0)),
            NotificationCompat.Builder(this, OmApp.CHANNEL_MISSED)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("छूटा अलार्म: ${title.ifEmpty { "पढ़ाई" }}")
                .setContentText("कोई बात नहीं। जब समय मिले, थोड़ा पढ़ लीजिए।")
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .build()
        )
        stopSelf()
    }

    private fun logAction(alarmId: Long, title: String, action: String) {
        val appCtx = applicationContext
        scope.launch {
            val a = AppDatabase.get(appCtx).alarmDao().get(alarmId) ?: return@launch
            AppDatabase.get(appCtx).logDao().insert(
                AlarmLogEntity(
                    ownerUid = a.ownerUid, alarmId = alarmId, title = title,
                    firedAt = System.currentTimeMillis(), action = action
                )
            )
        }
    }

    private fun buildNotification(alarmId: Long, title: String, text: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val actPi = PendingIntent.getActivity(
            this, 1,
            Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        fun servicePi(req: Int, action: String, minutes: Int) = PendingIntent.getService(
            this, req,
            Intent(this, AlarmService::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SNOOZE_MIN, minutes),
            flags
        )
        return NotificationCompat.Builder(this, OmApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(actPi, true)
            .setContentIntent(actPi)
            .addAction(0, "5 मिनट बाद", servicePi(2, ACTION_SNOOZE, 5))
            .addAction(0, "बंद करें", servicePi(3, ACTION_DISMISS, 0))
            .build()
    }

    private fun startSound() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            player = null // आवाज़ न बजे तो भी स्क्रीन और नोटिफिकेशन दिखेंगे
        }
    }

    private fun startVibration() {
        if (!ringing) return
        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator = v
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        } catch (e: Exception) {
            vibrator = null
        }
    }

    private fun stopSound() {
        handler.removeCallbacks(timeoutRunnable)
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopRinging() {
        stopSound()
        ringing = false
        sendBroadcast(Intent(ACTION_CLOSE_UI).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopSound()
        super.onDestroy()
    }
}
