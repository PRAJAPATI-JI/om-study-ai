package com.omstudy.ai.data

import java.net.URLEncoder
import kotlin.random.Random

/** सीखने वाले हिस्सों का शुद्ध (pure) तर्क। Android के बिना, इसलिए यूनिट टेस्ट बन सकते हैं। */
object Learning {
    const val MAX_BULK_LINES = 200
    const val MAX_TITLE = 100

    /** चैप्टर की चिपकाई हुई सूची (हर लाइन एक चैप्टर) को साफ करना: क्रमांक/बुलेट हटाना, खाली और दोहराव हटाना */
    fun parseBulk(text: String): List<String> {
        val seen = LinkedHashSet<String>()
        text.lines().forEach { raw ->
            var t = raw.trim()
            t = t.replace(Regex("^(\\d+[.)\\-:]|[-*•])\\s*"), "").trim()
            t = t.take(MAX_TITLE)
            if (t.isNotEmpty()) seen.add(t)
        }
        return seen.take(MAX_BULK_LINES)
    }

    /** (पूरे चैप्टर, कुल चैप्टर) */
    fun chapterProgress(chapters: List<ChapterEntity>): Pair<Int, Int> =
        chapters.count { it.done } to chapters.size

    fun isValidUrl(url: String): Boolean {
        val u = url.trim()
        return (u.startsWith("https://") || u.startsWith("http://")) && u.length > 10 && !u.contains(" ")
    }

    /** YouTube खोज का लिंक। यह सिर्फ खोज का लिंक है, किसी वीडियो का दावा नहीं। */
    fun youtubeSearchUrl(subject: String, chapter: String, hindi: Boolean = true): String {
        val words = listOf(subject, chapter, if (hindi) "हिंदी में" else "").filter { it.isNotBlank() }.joinToString(" ")
        return "https://www.youtube.com/results?search_query=" + URLEncoder.encode(words, "UTF-8")
    }

    // ---- क्विज़ ----

    /** (सही, कुल) विषय के अनुसार */
    fun subjectAccuracy(answers: List<QuizAnswerEntity>): Map<String, Pair<Int, Int>> =
        answers.groupBy { it.subject.ifBlank { "अन्य" } }
            .mapValues { (_, v) -> v.count { it.correct } to v.size }

    /**
     * कमज़ोर टॉपिक: हर (विषय, चैप्टर) के सिर्फ आखिरी [window] उत्तर देखे जाते हैं।
     * कम से कम [minAttempts] उत्तर हों और सही-दर [threshold] से कम हो, तभी कमज़ोर।
     * एक गलती से कोई टॉपिक कमज़ोर नहीं होता, और सुधरते ही सूची से हट जाता है।
     */
    fun weakTopics(
        answers: List<QuizAnswerEntity>,
        window: Int = 5,
        minAttempts: Int = 3,
        threshold: Double = 0.6
    ): List<Pair<String, String>> =
        answers.groupBy { it.subject to it.chapter }
            .filter { (_, v) ->
                val last = v.sortedBy { it.answeredAt }.takeLast(window)
                last.size >= minAttempts && last.count { it.correct }.toDouble() / last.size < threshold
            }
            .keys.toList()

    /**
     * क्विज़ के प्रश्न चुनना: पहले जिनका आखिरी उत्तर गलत था, फिर जो कभी नहीं आए, फिर बाकी।
     * हर समूह के भीतर क्रम random। [subject] खाली हो तो सारे विषय।
     */
    fun pickQuiz(
        questions: List<QuestionEntity>,
        answers: List<QuizAnswerEntity>,
        n: Int,
        subject: String = "",
        random: Random = Random.Default
    ): List<QuestionEntity> {
        val pool = questions.filter { subject.isBlank() || it.subject == subject }
        val lastByQ = answers.groupBy { it.questionId }.mapValues { (_, v) -> v.maxByOrNull { it.answeredAt }!!.correct }
        val wrong = pool.filter { lastByQ[it.id] == false }.shuffled(random)
        val unseen = pool.filter { lastByQ[it.id] == null }.shuffled(random)
        val rest = pool.filter { lastByQ[it.id] == true }.shuffled(random)
        return (wrong + unseen + rest).take(n)
    }

    fun validateQuestion(text: String, options: List<String>, correct: Int): String? = when {
        text.isBlank() -> "प्रश्न लिखिए।"
        options.size != 4 || options.any { it.isBlank() } -> "चारों विकल्प लिखिए।"
        options.map { it.trim() }.toSet().size != 4 -> "चारों विकल्प अलग-अलग होने चाहिए।"
        correct !in 0..3 -> "सही उत्तर चुनिए।"
        else -> null
    }
}
