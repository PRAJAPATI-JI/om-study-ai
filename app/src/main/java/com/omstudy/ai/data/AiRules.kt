package com.omstudy.ai.data

/** AI सर्वर से जुड़ने के शुद्ध (pure) नियम। इनके यूनिट टेस्ट मौजूद हैं। */
object AiRules {
    const val MAX_HISTORY = 10
    const val MAX_INPUT = 2000

    /** सिर्फ https:// पते मान्य। अंत का "/" हटा देता है। गलत हो तो null। */
    fun normalizeServerUrl(input: String): String? {
        val t = input.trim().trimEnd('/')
        if (!t.startsWith("https://")) return null
        val host = t.removePrefix("https://").substringBefore('/')
        if (host.isEmpty() || host.any { it.isWhitespace() } || !host.contains('.')) return null
        return t
    }

    /** सर्वर के error कोड का हिंदी संदेश */
    val TASKS = listOf("chat" to "सवाल-जवाब", "explain" to "समझाओ", "notes" to "नोट्स", "quiz" to "क्विज़", "timetable" to "टाइमटेबल")

    fun friendlyCode(code: String): String = when (code) {
        "no_server" -> "पहले सेटिंग्स में AI सर्वर का पता डालिए।"
        "not_logged_in" -> "पहले लॉगिन कीजिए।"
        "network" -> "इंटरनेट या सर्वर से संपर्क नहीं हो पाया।"
        "rate_limited", "service_busy" -> "बहुत जल्दी-जल्दी पूछ लिया। थोड़ी देर रुककर फिर पूछिए।"
        "daily_limit", "feature_limit" -> "आज की सीमा पूरी हो गई। कल फिर कोशिश कीजिए।"
        "no_token", "bad_token", "bad_signature", "expired", "wrong_project" -> "लॉगिन की जाँच विफल हुई। दोबारा लॉगिन कीजिए।"
        "feature_unavailable" -> "AI अभी चालू नहीं है। मालिक ने अभी कोई सेवा चालू नहीं की है।"
        "provider_failed" -> "AI सेवाएँ अभी जवाब नहीं दे रहीं। थोड़ी देर बाद कोशिश कीजिए।"
        "bad_response" -> "सर्वर का जवाब समझ नहीं आया। सर्वर का पता जाँचिए।"
        else -> "कुछ गड़बड़ हो गई। थोड़ी देर बाद दोबारा कोशिश कीजिए।"
    }

    /** पिछली बातचीत में से सिर्फ आखिरी MAX_HISTORY संदेश, पहला हमेशा user का */
    fun trimHistory(roles: List<String>): Int {
        var from = (roles.size - MAX_HISTORY).coerceAtLeast(0)
        while (from < roles.size && roles[from] != "user") from++
        return from
    }
}
