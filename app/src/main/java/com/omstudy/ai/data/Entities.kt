package com.omstudy.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ownerUid = जिस अकाउंट (Firebase user ID) का यह रिकॉर्ड है। खाली "" मतलब पुराना (लॉगिन से पहले का) रिकॉर्ड,
 * जो पहली बार लॉगिन करने वाले अकाउंट को मिल जाता है।
 *
 * daysMask: bit0 = सोमवार ... bit6 = रविवार.
 * daysMask == 0 मतलब "सिर्फ एक बार" वाला अलार्म (oneTimeEpochDay खाली हो तो अगली बार आने वाला समय)।
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0,
    val oneTimeEpochDay: Long? = null,
    val enabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val vibrate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val ownerUid: String = ""
)

/** priority: 0 = कम, 1 = सामान्य, 2 = ज़रूरी */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val epochDay: Long,
    val startMinutes: Int,
    val durationMin: Int,
    val priority: Int = 1,
    val note: String = "",
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val ownerUid: String = ""
)

/** action: fired, snooze, dismiss, started_study, missed */
@Entity(tableName = "alarm_logs")
data class AlarmLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val title: String,
    val firedAt: Long,
    val action: String,
    val ownerUid: String = ""
)

/** पहली बार की जानकारी (onboarding) का हर जवाब। (ownerUid, key) मिलकर पहचान हैं, यानी हर अकाउंट के अपने जवाब। */
@Entity(tableName = "profile_answers", primaryKeys = ["ownerUid", "key"])
data class ProfileAnswerEntity(
    val ownerUid: String,
    val key: String,
    val value: String
)
