package com.omstudy.ai.data

/**
 * पहली बार की जानकारी वाले सवाल (onboarding) और उनसे बनने वाली सुझाई गई पढ़ाई-योजना।
 * यह सारा हिस्सा "शुद्ध" (pure) है, इसमें Android या इंटरनेट नहीं है, इसलिए इसके यूनिट टेस्ट बन सकते हैं।
 *
 * ज़रूरी ईमानदारी: योजना सिर्फ एक सुझाव है। यह किसी बोर्ड/परीक्षा का आधिकारिक सिलेबस नहीं है।
 * चैप्टर-वार योजना तब बनेगी जब जाँचा हुआ सिलेबस ऐप में जुड़ेगा।
 */
object Keys {
    const val GOALS = "goals"
    const val LANGUAGE = "language"
    const val CLASS = "class"
    const val BOARD = "board"
    const val STREAM = "stream"
    const val SUBJECTS = "subjects"
    const val WEAK = "weak"
    const val EXAMS = "exams"
    const val EXAM_YEAR = "exam_year"
    const val TRADE = "trade"
    const val TRADE_MODE = "trade_mode"
    const val LEVEL = "level"
    const val DAILY_MIN = "daily_min"
    const val START_TIME = "start_time"
    const val DAYS = "days"
    const val LESSON = "lesson"
    const val WEEKLY_TEST = "weekly_test"
    const val DONE = "onboarding_done"
}

enum class QType { SINGLE, MULTI }

data class Option(val value: String, val label: String)

data class Question(
    val key: String,
    val title: String,
    val hint: String = "",
    val type: QType,
    val options: List<Option>,
    val optional: Boolean = false
)

object Onboarding {
    const val SCHOOL = "school"
    const val GOVT = "govt"
    const val ITI = "iti"

    /** छोड़े गए (skip) वैकल्पिक सवाल का निशान */
    const val SKIP = "-"

    private val SCIENCE = listOf("भौतिकी", "रसायन", "गणित", "जीव विज्ञान", "हिंदी", "अंग्रेज़ी")
    private val COMMERCE = listOf("लेखाशास्त्र", "व्यवसाय अध्ययन", "अर्थशास्त्र", "गणित", "हिंदी", "अंग्रेज़ी")
    private val ARTS = listOf("इतिहास", "भूगोल", "राजनीति विज्ञान", "अर्थशास्त्र", "हिंदी", "अंग्रेज़ी")
    private val CLASS10 = listOf("गणित", "विज्ञान", "सामाजिक विज्ञान", "हिंदी", "अंग्रेज़ी")
    private val GOVT_SUBJECTS = listOf("गणित", "रीज़निंग", "सामान्य विज्ञान", "सामान्य ज्ञान और करंट अफेयर्स")

    fun splitMulti(v: String?): List<String> =
        v?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() && it != SKIP } ?: emptyList()

    fun joinMulti(values: Collection<String>): String = values.joinToString("|")

    private fun opts(vararg pairs: Pair<String, String>) = pairs.map { Option(it.first, it.second) }
    private fun same(list: List<String>) = list.map { Option(it, it) }

    /** चुनी गई कक्षा/स्ट्रीम के हिसाब से स्कूल के विषयों की सूची */
    fun schoolSubjectChoices(answers: Map<String, String>): List<String> {
        val cls = answers[Keys.CLASS]
        return when {
            cls == "10" -> CLASS10
            answers[Keys.STREAM] == "science" -> SCIENCE
            answers[Keys.STREAM] == "commerce" -> COMMERCE
            answers[Keys.STREAM] == "arts" -> ARTS
            else -> emptyList()
        }
    }

    /** जवाबों के हिसाब से पूछे जाने वाले सारे सवाल, सही क्रम में (सवाल जवाबों के हिसाब से बदलते हैं) */
    fun questionsFor(a: Map<String, String>): List<Question> {
        val goals = splitMulti(a[Keys.GOALS])
        val list = mutableListOf<Question>()

        list += Question(
            Keys.GOALS, "आप किसकी तैयारी करना चाहते हैं?", "एक से ज़्यादा चुन सकते हैं।", QType.MULTI,
            opts(SCHOOL to "स्कूल की पढ़ाई (कक्षा 10/11/12)", GOVT to "सरकारी परीक्षा", ITI to "ITI / इलेक्ट्रीशियन / सोलर")
        )
        list += Question(
            Keys.LANGUAGE, "आप किस भाषा में पढ़ना पसंद करेंगे?", "", QType.SINGLE,
            opts("hi" to "हिंदी", "en" to "English", "hinglish" to "Hinglish (हिंदी + English मिला कर)")
        )

        if (SCHOOL in goals) {
            list += Question(Keys.CLASS, "आप किस कक्षा में हैं?", "", QType.SINGLE, opts("10" to "कक्षा 10", "11" to "कक्षा 11", "12" to "कक्षा 12"))
            list += Question(
                Keys.BOARD, "आपका बोर्ड कौन सा है?", "", QType.SINGLE,
                opts("jac" to "JAC (झारखंड बोर्ड)", "cbse" to "CBSE", "other" to "कोई और बोर्ड")
            )
            if (a[Keys.CLASS] == "11" || a[Keys.CLASS] == "12") {
                list += Question(
                    Keys.STREAM, "आपकी स्ट्रीम कौन सी है?", "", QType.SINGLE,
                    opts("science" to "विज्ञान", "commerce" to "वाणिज्य", "arts" to "कला")
                )
            }
            val choices = schoolSubjectChoices(a)
            if (choices.isNotEmpty()) {
                list += Question(Keys.SUBJECTS, "आप कौन-कौन से विषय पढ़ रहे हैं?", "जो पढ़ाई में रखना चाहते हैं वे चुनिए।", QType.MULTI, same(choices))
                val picked = splitMulti(a[Keys.SUBJECTS])
                if (picked.isNotEmpty()) {
                    list += Question(Keys.WEAK, "इनमें कौन से विषय आपको कठिन लगते हैं?", "इन्हें योजना में ज़्यादा समय मिलेगा। छोड़ भी सकते हैं।", QType.MULTI, same(picked), optional = true)
                }
            }
        }

        if (GOVT in goals) {
            list += Question(
                Keys.EXAMS, "कौन सी सरकारी परीक्षा की तैयारी है?", "", QType.MULTI,
                opts(
                    "alp" to "रेलवे ALP", "tech" to "रेलवे टेक्नीशियन", "ssc" to "SSC",
                    "jh" to "झारखंड की परीक्षा", "other" to "कोई और"
                )
            )
            list += Question(
                Keys.EXAM_YEAR, "किस साल की परीक्षा का लक्ष्य है?", "", QType.SINGLE,
                opts("2026" to "2026", "2027" to "2027", "2028" to "2028", "unsure" to "पक्का नहीं पता"),
                optional = true
            )
        }

        if (ITI in goals) {
            list += Question(
                Keys.TRADE, "कौन सा हुनर / ट्रेड सीखना है?", "", QType.SINGLE,
                opts("electrician" to "इलेक्ट्रीशियन", "asst_electrician" to "असिस्टेंट इलेक्ट्रीशियन", "solar" to "सोलर इंस्टॉलेशन", "other" to "कोई और")
            )
            list += Question(
                Keys.TRADE_MODE, "आपको क्या चाहिए?", "", QType.SINGLE,
                opts("theory" to "सिर्फ थ्योरी", "practical" to "प्रैक्टिकल समझ", "both" to "दोनों")
            )
        }

        list += Question(
            Keys.LEVEL, "अभी आपकी तैयारी कैसी है?", "सच-सच चुनिए। इससे योजना सही बनती है।", QType.SINGLE,
            opts("beginner" to "शुरुआत से करनी है", "medium" to "थोड़ी-बहुत आती है", "good" to "काफी अच्छी है, बस दोहराना है")
        )
        list += Question(
            Keys.DAILY_MIN, "रोज़ कितना समय पढ़ सकते हैं?", "जितना सच में निकाल पाएँ उतना ही चुनिए।", QType.SINGLE,
            opts("60" to "1 घंटा", "120" to "2 घंटे", "180" to "3 घंटे", "240" to "4 घंटे", "300" to "5 घंटे")
        )
        list += Question(
            Keys.START_TIME, "पढ़ाई किस समय शुरू करना ठीक रहेगा?", "", QType.SINGLE,
            opts("360" to "सुबह 6 बजे", "540" to "सुबह 9 बजे", "840" to "दोपहर 2 बजे", "1080" to "शाम 6 बजे", "1230" to "रात 8:30 बजे")
        )
        list += Question(
            Keys.DAYS, "हफ्ते के किन दिनों में पढ़ पाएँगे?", "", QType.MULTI,
            opts("0" to "सोमवार", "1" to "मंगलवार", "2" to "बुधवार", "3" to "गुरुवार", "4" to "शुक्रवार", "5" to "शनिवार", "6" to "रविवार")
        )
        list += Question(
            Keys.LESSON, "आपको कैसे पाठ पसंद हैं?", "", QType.SINGLE,
            opts("short" to "छोटे-छोटे (30 मिनट)", "detailed" to "लंबे और विस्तार से (45 मिनट)"),
            optional = true
        )
        list += Question(
            Keys.WEEKLY_TEST, "हर हफ्ते दोहराई और खुद की जाँच चाहिए?", "", QType.SINGLE,
            opts("yes" to "हाँ, चाहिए", "no" to "नहीं"),
            optional = true
        )
        return list
    }

    /** पहला बिना-जवाब वाला ज़रूरी सवाल। optional सवाल "छोड़ा गया" ("skip") से भी भरा माना जाता है। */
    fun firstUnanswered(a: Map<String, String>): Question? =
        questionsFor(a).firstOrNull { a[it.key].isNullOrEmpty() }

    fun isComplete(a: Map<String, String>): Boolean = firstUnanswered(a) == null

    fun progress(a: Map<String, String>): Pair<Int, Int> {
        val qs = questionsFor(a)
        return qs.count { !a[it.key].isNullOrEmpty() } to qs.size
    }

    fun goalLabel(a: Map<String, String>): List<String> {
        val out = mutableListOf<String>()
        val goals = splitMulti(a[Keys.GOALS])
        if (SCHOOL in goals) {
            val cls = a[Keys.CLASS]
            val board = when (a[Keys.BOARD]) { "jac" -> "JAC"; "cbse" -> "CBSE"; else -> "" }
            out += listOf("कक्षा ${cls ?: ""}", board).filter { it.isNotBlank() }.joinToString(" · ")
        }
        if (GOVT in goals) {
            val names = mapOf("alp" to "रेलवे ALP", "tech" to "रेलवे टेक्नीशियन", "ssc" to "SSC", "jh" to "झारखंड परीक्षा", "other" to "सरकारी परीक्षा")
            val ex = splitMulti(a[Keys.EXAMS]).mapNotNull { names[it] }
            out += ex.ifEmpty { listOf("सरकारी परीक्षा") }.joinToString(", ")
        }
        if (ITI in goals) {
            val names = mapOf("electrician" to "इलेक्ट्रीशियन", "asst_electrician" to "असिस्टेंट इलेक्ट्रीशियन", "solar" to "सोलर इंस्टॉलेशन", "other" to "ITI / हुनर")
            out += names[a[Keys.TRADE]] ?: "ITI / हुनर"
        }
        return out
    }
}

/** योजना का एक सत्र (session) */
data class PlanItem(
    val epochDay: Long,
    val startMinutes: Int,
    val durationMin: Int,
    val subject: String,
    val goal: String,
    val isRevision: Boolean
)

object StudyPlanner {
    const val BREAK_MIN = 10
    const val LAST_END_MINUTES = 23 * 60 + 30 // रात 11:30 के बाद कोई सत्र नहीं

    /** लक्ष्य-वार (goal → विषयों की सूची और हर विषय का वज़न). कठिन विषय का वज़न 2 */
    data class Bucket(val goal: String, val subjects: List<Pair<String, Int>>)

    fun buckets(a: Map<String, String>): List<Bucket> {
        val goals = Onboarding.splitMulti(a[Keys.GOALS])
        val out = mutableListOf<Bucket>()
        if (Onboarding.SCHOOL in goals) {
            val subs = Onboarding.splitMulti(a[Keys.SUBJECTS])
            val weak = Onboarding.splitMulti(a[Keys.WEAK]).toSet()
            if (subs.isNotEmpty()) out += Bucket("स्कूल", subs.map { it to if (it in weak) 2 else 1 })
        }
        if (Onboarding.GOVT in goals) {
            out += Bucket("सरकारी परीक्षा", listOf("गणित", "रीज़निंग", "सामान्य विज्ञान", "सामान्य ज्ञान और करंट अफेयर्स").map { it to 1 })
        }
        if (Onboarding.ITI in goals) {
            val trade = when (a[Keys.TRADE]) {
                "electrician" -> "इलेक्ट्रीशियन"
                "asst_electrician" -> "असिस्टेंट इलेक्ट्रीशियन"
                "solar" -> "सोलर इंस्टॉलेशन"
                else -> "ITI / हुनर"
            }
            val subs = when (a[Keys.TRADE_MODE]) {
                "theory" -> listOf("$trade: थ्योरी")
                "practical" -> listOf("$trade: प्रैक्टिकल समझ और सुरक्षा")
                else -> listOf("$trade: थ्योरी", "$trade: प्रैक्टिकल समझ और सुरक्षा")
            }
            out += Bucket("ITI / हुनर", subs.map { it to 1 })
        }
        return out
    }

    /**
     * अगले [days] दिनों की योजना। नियम:
     *  - रोज़ का कुल समय कभी चुने हुए समय से ज़्यादा नहीं।
     *  - हर लक्ष्य को बारी-बारी से समय मिलता है, कोई लक्ष्य चुपचाप गायब नहीं होता।
     *  - कठिन विषय को दोगुना वज़न।
     *  - रात 11:30 के बाद सत्र नहीं।
     *  - हफ्ते के आखिरी पढ़ाई वाले दिन का आखिरी सत्र "दोहराई और खुद की जाँच" बन सकता है।
     */
    fun generate(a: Map<String, String>, startEpochDay: Long, startDayOfWeekMon0: Int, days: Int = 7): List<PlanItem> {
        val daily = a[Keys.DAILY_MIN]?.toIntOrNull() ?: return emptyList()
        val startTime = a[Keys.START_TIME]?.toIntOrNull() ?: return emptyList()
        val studyDays = Onboarding.splitMulti(a[Keys.DAYS]).mapNotNull { it.toIntOrNull() }.toSet()
        if (studyDays.isEmpty()) return emptyList()
        val block = if (a[Keys.LESSON] == "short") 30 else 45
        val buckets = buckets(a)
        if (buckets.isEmpty()) return emptyList()

        // नर्म वज़न-वाला क्रम: हर लक्ष्य बराबर बारी लेता है, लक्ष्य के भीतर विषय वज़न के हिसाब से
        val goalCursor = IntArray(buckets.size)
        val subjectPool = buckets.map { b ->
            b.subjects.flatMap { (s, w) -> List(w) { s } }
        }
        var goalTurn = 0
        fun nextSubject(): Pair<String, String> {
            val gi = goalTurn % buckets.size
            goalTurn++
            val pool = subjectPool[gi]
            val s = pool[goalCursor[gi] % pool.size]
            goalCursor[gi]++
            return s to buckets[gi].goal
        }

        val perDay = (daily / block).coerceAtLeast(1)
        val items = mutableListOf<PlanItem>()
        val studyOffsets = (0 until days).filter { ((startDayOfWeekMon0 + it) % 7) in studyDays }
        val lastStudyOffset = studyOffsets.lastOrNull()
        val weeklyReview = a[Keys.WEEKLY_TEST] == "yes"

        for (offset in studyOffsets) {
            var cursor = startTime
            var count = 0
            // अगर "रोज़ का समय" 1 घंटा है और सत्र 45 मिनट का, तो एक ही सत्र। कुल कभी ज़्यादा नहीं।
            var used = 0
            while (count < perDay && cursor + block <= LAST_END_MINUTES && used + block <= maxOf(daily, block)) {
                val (subject, goal) = nextSubject()
                val last = count == perDay - 1 || cursor + block + BREAK_MIN + block > LAST_END_MINUTES
                val review = weeklyReview && offset == lastStudyOffset && last && perDay >= 2
                items += PlanItem(
                    epochDay = startEpochDay + offset,
                    startMinutes = cursor,
                    durationMin = block,
                    subject = if (review) "दोहराई और खुद की जाँच" else subject,
                    goal = goal,
                    isRevision = review
                )
                used += block
                cursor += block + BREAK_MIN
                count++
            }
        }
        return items
    }
}
