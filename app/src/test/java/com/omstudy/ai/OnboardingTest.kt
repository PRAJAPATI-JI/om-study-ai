package com.omstudy.ai

import com.omstudy.ai.data.Keys
import com.omstudy.ai.data.Onboarding
import com.omstudy.ai.data.StudyPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    private fun school12Science() = mapOf(
        Keys.GOALS to "school", Keys.LANGUAGE to "hi", Keys.CLASS to "12", Keys.BOARD to "jac",
        Keys.STREAM to "science", Keys.SUBJECTS to "भौतिकी|रसायन|गणित", Keys.WEAK to "गणित",
        Keys.LEVEL to "medium", Keys.DAILY_MIN to "120", Keys.START_TIME to "1080",
        Keys.DAYS to "0|1|2|3|4|5|6", Keys.LESSON to "short", Keys.WEEKLY_TEST to "yes"
    )

    @Test fun firstQuestionIsGoals() = assertEquals(Keys.GOALS, Onboarding.firstUnanswered(emptyMap())?.key)

    @Test fun class10HasNoStreamQuestion() {
        val a = mapOf(Keys.GOALS to "school", Keys.LANGUAGE to "hi", Keys.CLASS to "10", Keys.BOARD to "jac")
        assertFalse(Onboarding.questionsFor(a).any { it.key == Keys.STREAM })
        assertEquals(Keys.SUBJECTS, Onboarding.firstUnanswered(a)?.key)
    }

    @Test fun class12AsksStream() {
        val a = mapOf(Keys.GOALS to "school", Keys.LANGUAGE to "hi", Keys.CLASS to "12", Keys.BOARD to "jac")
        assertEquals(Keys.STREAM, Onboarding.firstUnanswered(a)?.key)
    }

    @Test fun govtGoalDoesNotAskSchoolQuestions() {
        val keys = Onboarding.questionsFor(mapOf(Keys.GOALS to "govt")).map { it.key }
        assertFalse(Keys.CLASS in keys)
        assertTrue(Keys.EXAMS in keys)
    }

    @Test fun completeSchoolFlow() {
        assertTrue(Onboarding.isComplete(school12Science()))
        assertNull(Onboarding.firstUnanswered(school12Science()))
    }

    @Test fun skippedOptionalQuestionCountsAsAnswered() {
        val a = school12Science() + (Keys.WEAK to Onboarding.SKIP)
        assertTrue(Onboarding.isComplete(a))
    }

    @Test fun progressCountsAnswers() {
        val (done, total) = Onboarding.progress(mapOf(Keys.GOALS to "school", Keys.LANGUAGE to "hi"))
        assertEquals(2, done)
        assertTrue(total > done)
    }

    @Test fun splitMultiIgnoresSkipAndBlank() {
        assertEquals(listOf("a", "b"), Onboarding.splitMulti("a|b|-| "))
        assertEquals(emptyList<String>(), Onboarding.splitMulti(null))
    }

    // ---- योजना ----

    @Test fun planNeverExceedsDailyTime() {
        val plan = StudyPlanner.generate(school12Science(), 20000, 0, 7)
        assertTrue(plan.isNotEmpty())
        plan.groupBy { it.epochDay }.values.forEach { day ->
            assertTrue(day.sumOf { it.durationMin } <= 120)
        }
    }

    @Test fun planOnlyOnChosenDays() {
        val a = school12Science() + (Keys.DAYS to "0|2") // सोमवार, बुधवार
        val plan = StudyPlanner.generate(a, 20000, 0, 7)
        assertEquals(setOf(20000L, 20002L), plan.map { it.epochDay }.toSet())
    }

    @Test fun planRespectsStartDayOfWeek() {
        val a = school12Science() + (Keys.DAYS to "4") // सिर्फ शुक्रवार
        val plan = StudyPlanner.generate(a, 20000, 2, 7) // शुरुआत बुधवार से, शुक्रवार = offset 2
        assertEquals(setOf(20002L), plan.map { it.epochDay }.toSet())
    }

    @Test fun noSessionEndsAfterLateLimit() {
        val a = school12Science() + (Keys.START_TIME to "1230") + (Keys.DAILY_MIN to "300")
        StudyPlanner.generate(a, 20000, 0, 7).forEach {
            assertTrue(it.startMinutes + it.durationMin <= StudyPlanner.LAST_END_MINUTES)
        }
    }

    @Test fun weakSubjectGetsMoreSessions() {
        val a = school12Science() + (Keys.DAILY_MIN to "300") + (Keys.WEEKLY_TEST to "no")
        val plan = StudyPlanner.generate(a, 20000, 0, 7)
        val math = plan.count { it.subject == "गणित" }
        val physics = plan.count { it.subject == "भौतिकी" }
        assertTrue(math > physics)
    }

    @Test fun everyGoalGetsTime() {
        val a = school12Science() + mapOf(
            Keys.GOALS to "school|govt|iti", Keys.EXAMS to "alp", Keys.TRADE to "electrician",
            Keys.TRADE_MODE to "both", Keys.DAILY_MIN to "240", Keys.WEEKLY_TEST to "no"
        )
        val goals = StudyPlanner.generate(a, 20000, 0, 7).map { it.goal }.toSet()
        assertEquals(setOf("स्कूल", "सरकारी परीक्षा", "ITI / हुनर"), goals)
    }

    @Test fun emptyWhenNoStudyDays() {
        val a = school12Science() + (Keys.DAYS to "-")
        assertTrue(StudyPlanner.generate(a, 20000, 0, 7).isEmpty())
    }

    @Test fun emptyWhenTimeMissing() {
        assertTrue(StudyPlanner.generate(school12Science() - Keys.DAILY_MIN, 20000, 0, 7).isEmpty())
    }

    @Test fun weeklyReviewIsLabelledAndNotMoreThanOnePerWeek() {
        val plan = StudyPlanner.generate(school12Science(), 20000, 0, 7)
        assertEquals(1, plan.count { it.isRevision })
    }
}
