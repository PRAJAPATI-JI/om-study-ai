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

/** सिलेबस का एक चैप्टर। यह विद्यार्थी ने खुद जोड़ा है, आधिकारिक सिलेबस नहीं। */
@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String,
    val subject: String,
    val title: String,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String,
    val title: String,
    val body: String,
    val subject: String = "",
    val chapter: String = "",
    val bookmarked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** विद्यार्थी का खुद बनाया बहुविकल्पीय प्रश्न। correct = 0..3 */
@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String,
    val subject: String,
    val chapter: String,
    val text: String,
    val optA: String,
    val optB: String,
    val optC: String,
    val optD: String,
    val correct: Int,
    val explanation: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun options(): List<String> = listOf(optA, optB, optC, optD)
}

@Entity(tableName = "quiz_answers")
data class QuizAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String,
    val questionId: Long,
    val subject: String,
    val chapter: String,
    val correct: Boolean,
    val answeredAt: Long = System.currentTimeMillis()
)

/** विद्यार्थी का सहेजा हुआ वीडियो लिंक। शीर्षक और लिंक वही हैं जो उसने खुद डाले। */
@Entity(tableName = "saved_videos")
data class SavedVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String,
    val title: String,
    val url: String,
    val subject: String = "",
    val chapter: String = "",
    val watched: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
