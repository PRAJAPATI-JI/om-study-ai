package com.omstudy.ai

import com.omstudy.ai.alarm.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RepeatRuleTest {
    // 2 नवंबर 2026 सोमवार है
    private val monday10am = LocalDateTime.of(2026, 11, 2, 10, 0)

    private val MON = 1
    private val WED = 4
    private val ALL = 127

    @Test fun mondayAlarmLaterTodayFiresToday() =
        assertEquals(LocalDateTime.of(2026, 11, 2, 11, 0), RepeatRule.nextTrigger(11, 0, MON, null, monday10am))

    @Test fun mondayAlarmAlreadyPassedFiresNextMonday() =
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), RepeatRule.nextTrigger(9, 0, MON, null, monday10am))

    @Test fun wednesdayAlarmFiresWednesday() =
        assertEquals(LocalDateTime.of(2026, 11, 4, 6, 0), RepeatRule.nextTrigger(6, 0, WED, null, monday10am))

    @Test fun dailyAlarmPassedFiresTomorrow() =
        assertEquals(LocalDateTime.of(2026, 11, 3, 6, 0), RepeatRule.nextTrigger(6, 0, ALL, null, monday10am))

    @Test fun exactlyNowIsNotInFuture() =
        assertEquals(LocalDateTime.of(2026, 11, 3, 10, 0), RepeatRule.nextTrigger(10, 0, ALL, null, monday10am))

    @Test fun oneTimeWithoutDateLaterToday() =
        assertEquals(LocalDateTime.of(2026, 11, 2, 18, 30), RepeatRule.nextTrigger(18, 30, 0, null, monday10am))

    @Test fun oneTimeWithoutDatePassedGoesTomorrow() =
        assertEquals(LocalDateTime.of(2026, 11, 3, 6, 0), RepeatRule.nextTrigger(6, 0, 0, null, monday10am))

    @Test fun oneTimeWithFutureDate() {
        val d = LocalDate.of(2026, 11, 20).toEpochDay()
        assertEquals(LocalDateTime.of(2026, 11, 20, 7, 15), RepeatRule.nextTrigger(7, 15, 0, d, monday10am))
    }

    @Test fun oneTimeWithPastDateReturnsNull() {
        val d = LocalDate.of(2026, 10, 1).toEpochDay()
        assertNull(RepeatRule.nextTrigger(7, 15, 0, d, monday10am))
    }

    @Test fun bitForDayMondayAndSunday() {
        assertEquals(1, RepeatRule.bitForDay(LocalDate.of(2026, 11, 2)))
        assertEquals(64, RepeatRule.bitForDay(LocalDate.of(2026, 11, 1)))
    }
}
