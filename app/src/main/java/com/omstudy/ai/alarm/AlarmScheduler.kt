package com.omstudy.ai.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.omstudy.ai.MainActivity
import com.omstudy.ai.data.AlarmEntity
import com.google.firebase.auth.FirebaseAuth
import com.omstudy.ai.data.AppDatabase
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * अलार्म सेट करने का काम। यहाँ AlarmManager.setAlarmClock() इस्तेमाल होता है:
 * यह Android का वही तरीका है जो घड़ी (clock) वाले ऐप इस्तेमाल करते हैं। यह बैटरी सेविंग (Doze) में भी समय पर बजता है
 * और इसके लिए अलग "exact alarm" अनुमति नहीं माँगनी पड़ती।
 */
object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SNOOZE = "is_snooze"
    const val TEST_ALARM_ID = -1L
    private const val ACTION_FIRE = "com.omstudy.ai.ALARM_FIRE"
    private const val SNOOZE_BASE = 1_000_000
    private const val TEST_CODE = 2_000_000

    private fun alarmManager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun firePi(ctx: Context, requestCode: Int, alarmId: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SNOOZE, snooze)
        }
        return PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAt(ctx: Context, millis: Long, requestCode: Int, alarmId: Long, snooze: Boolean) {
        val showPi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager(ctx).setAlarmClock(
            AlarmManager.AlarmClockInfo(millis, showPi),
            firePi(ctx, requestCode, alarmId, snooze)
        )
    }

    /** अलार्म को अगले समय के लिए सेट करता है (बंद हो तो पुराना रद्द कर देता है)। */
    fun schedule(ctx: Context, alarm: AlarmEntity) {
        cancel(ctx, alarm.id)
        if (!alarm.enabled) return
        val next = RepeatRule.nextTrigger(
            alarm.hour, alarm.minute, alarm.daysMask, alarm.oneTimeEpochDay, LocalDateTime.now()
        ) ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAt(ctx, millis, alarm.id.toInt(), alarm.id, false)
    }

    fun scheduleSnooze(ctx: Context, alarmId: Long, minutes: Int) {
        val millis = System.currentTimeMillis() + minutes * 60_000L
        setAt(ctx, millis, SNOOZE_BASE + alarmId.toInt(), alarmId, true)
    }

    /** जाँच के लिए: 10 सेकंड बाद टेस्ट अलार्म */
    fun scheduleTest(ctx: Context, seconds: Int = 10) {
        setAt(ctx, System.currentTimeMillis() + seconds * 1000L, TEST_CODE, TEST_ALARM_ID, false)
    }

    fun cancel(ctx: Context, alarmId: Long) {
        val am = alarmManager(ctx)
        am.cancel(firePi(ctx, alarmId.toInt(), alarmId, false))
        am.cancel(firePi(ctx, SNOOZE_BASE + alarmId.toInt(), alarmId, true))
    }

    /** ऐप खुलने, फोन रीस्टार्ट, समय/टाइमज़ोन बदलने या ऐप अपडेट के बाद सारे चालू अलार्म दोबारा सेट करता है। */
    suspend fun rescheduleAll(ctx: Context) {
        // सिर्फ अभी लॉगिन अकाउंट के अलार्म। लॉगिन नहीं है तो कुछ नहीं लगता।
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val list = AppDatabase.get(ctx).alarmDao().enabledList(uid)
        list.forEach { schedule(ctx, it) }
    }
}
