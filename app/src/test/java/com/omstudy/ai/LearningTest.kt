package com.omstudy.ai

import com.omstudy.ai.data.ChapterEntity
import com.omstudy.ai.data.Learning
import com.omstudy.ai.data.QuestionEntity
import com.omstudy.ai.data.QuizAnswerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LearningTest {
    private fun q(id: Long, subject: String = "गणित") =
        QuestionEntity(id = id, ownerUid = "u", subject = subject, chapter = "c", text = "q$id", optA = "a", optB = "b", optC = "c", optD = "d", correct = 0)

    private fun ans(qid: Long, ok: Boolean, t: Long, subject: String = "गणित", chapter: String = "c") =
        QuizAnswerEntity(ownerUid = "u", questionId = qid, subject = subject, chapter = chapter, correct = ok, answeredAt = t)

    @Test fun bulkStripsNumbersAndBullets() {
        val r = Learning.parseBulk("1. संख्या पद्धति\n2) बीजगणित\n- ज्यामिति\n• त्रिकोणमिति\n\n  ")
        assertEquals(listOf("संख्या पद्धति", "बीजगणित", "ज्यामिति", "त्रिकोणमिति"), r)
    }

    @Test fun bulkRemovesDuplicates() = assertEquals(listOf("A", "B"), Learning.parseBulk("A\nB\nA"))

    @Test fun bulkLimitsLines() = assertEquals(Learning.MAX_BULK_LINES, Learning.parseBulk((1..500).joinToString("\n") { "ch$it" }).size)

    @Test fun bulkLimitsTitleLength() = assertEquals(Learning.MAX_TITLE, Learning.parseBulk("x".repeat(300)).first().length)

    @Test fun chapterProgressCounts() {
        val list = listOf(
            ChapterEntity(ownerUid = "u", subject = "s", title = "a", done = true),
            ChapterEntity(ownerUid = "u", subject = "s", title = "b")
        )
        assertEquals(1 to 2, Learning.chapterProgress(list))
    }

    @Test fun urlValidation() {
        assertTrue(Learning.isValidUrl("https://youtu.be/abc123"))
        assertFalse(Learning.isValidUrl("youtube.com/x"))
        assertFalse(Learning.isValidUrl("javascript:alert(1)"))
        assertFalse(Learning.isValidUrl("https://a b.com/x"))
    }

    @Test fun youtubeSearchIsEncoded() {
        val u = Learning.youtubeSearchUrl("भौतिकी", "गति")
        assertTrue(u.startsWith("https://www.youtube.com/results?search_query="))
        assertFalse(u.contains(" "))
        assertFalse(u.contains("भौतिकी"))
    }

    @Test fun accuracyBySubject() {
        val r = Learning.subjectAccuracy(listOf(ans(1, true, 1), ans(2, false, 2), ans(3, true, 3, subject = "रसायन")))
        assertEquals(1 to 2, r["गणित"])
        assertEquals(1 to 1, r["रसायन"])
    }

    @Test fun oneMistakeDoesNotMakeTopicWeak() {
        assertTrue(Learning.weakTopics(listOf(ans(1, false, 1))).isEmpty())
    }

    @Test fun repeatedMistakesMakeTopicWeak() {
        val a = listOf(ans(1, false, 1), ans(2, false, 2), ans(3, true, 3), ans(4, false, 4))
        assertEquals(listOf("गणित" to "c"), Learning.weakTopics(a))
    }

    @Test fun topicRecoversAfterCorrectAnswers() {
        val a = listOf(ans(1, false, 1), ans(2, false, 2), ans(3, false, 3), ans(4, true, 4), ans(5, true, 5), ans(6, true, 6), ans(7, true, 7))
        assertTrue(Learning.weakTopics(a).isEmpty())
    }

    @Test fun quizPrefersWrongThenUnseen() {
        val qs = listOf(q(1), q(2), q(3))
        val a = listOf(ans(1, true, 1), ans(2, false, 2))
        val picked = Learning.pickQuiz(qs, a, 3, random = Random(1))
        assertEquals(listOf(2L, 3L, 1L), picked.map { it.id })
    }

    @Test fun quizUsesLatestAnswerOfAQuestion() {
        val qs = listOf(q(1), q(2))
        val a = listOf(ans(1, false, 1), ans(1, true, 5)) // आखिरी सही
        val picked = Learning.pickQuiz(qs, a, 2, random = Random(1))
        assertEquals(2L, picked.first().id)
    }

    @Test fun quizFiltersBySubjectAndLimits() {
        val qs = listOf(q(1, "गणित"), q(2, "रसायन"), q(3, "गणित"))
        assertEquals(setOf(1L, 3L), Learning.pickQuiz(qs, emptyList(), 10, "गणित").map { it.id }.toSet())
        assertEquals(1, Learning.pickQuiz(qs, emptyList(), 1).size)
    }

    @Test fun questionValidation() {
        assertNull(Learning.validateQuestion("प्र", listOf("a", "b", "c", "d"), 2))
        assertNotNull(Learning.validateQuestion("", listOf("a", "b", "c", "d"), 2))
        assertNotNull(Learning.validateQuestion("प्र", listOf("a", "b", "c", ""), 2))
        assertNotNull(Learning.validateQuestion("प्र", listOf("a", "a", "c", "d"), 2))
        assertNotNull(Learning.validateQuestion("प्र", listOf("a", "b", "c", "d"), 7))
    }
}
