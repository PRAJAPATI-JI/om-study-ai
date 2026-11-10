package com.omstudy.ai.alarm

import java.time.LocalDate
import java.time.LocalDateTime

/** अलार्म अगली बार कब बजेगा, यह तय करने का शुद्ध (pure) हिसाब। इसका यूनिट टेस्ट मौजूद है। */
object RepeatRule {

    /** सोमवार = bit0 ... रविवार = bit6 */
    fun bitForDay(date: LocalDate): Int = 1 shl (date.dayOfWeek.value - 1)

    fun nextTrigger(
        hour: Int,
        minute: Int,
        daysMask: Int,
        oneTimeEpochDay: Long?,
        now: LocalDateTime
    ): LocalDateTime? {
        if (daysMask == 0) {
            val date = oneTimeEpochDay?.let { LocalDate.ofEpochDay(it) } ?: now.toLocalDate()
            val candidate = date.atTime(hour, minute)
            return when {
                candidate.isAfter(now) -> candidate
                oneTimeEpochDay == null -> candidate.plusDays(1)
                else -> null // तारीख़ तय थी और बीत चुकी है
            }
        }
        for (i in 0..7) {
            val date = now.toLocalDate().plusDays(i.toLong())
            if (daysMask and bitForDay(date) != 0) {
                val candidate = date.atTime(hour, minute)
                if (candidate.isAfter(now)) return candidate
            }
        }
        return null
    }
}
