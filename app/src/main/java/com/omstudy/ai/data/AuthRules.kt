package com.omstudy.ai.data

/** साइनअप/लॉगिन की जाँच के शुद्ध (pure) नियम। इनके यूनिट टेस्ट मौजूद हैं। */
object AuthRules {
    const val MIN_PASSWORD = 8
    const val MAX_NAME = 40

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

    fun validateSignUp(name: String, email: String, password: String, confirm: String): String? = when {
        name.isBlank() -> "अपना नाम लिखिए।"
        name.trim().length > MAX_NAME -> "नाम $MAX_NAME अक्षर से छोटा रखिए।"
        !isValidEmail(email) -> "सही ईमेल लिखिए।"
        password.length < MIN_PASSWORD -> "पासवर्ड कम से कम $MIN_PASSWORD अक्षर का रखिए।"
        password != confirm -> "दोनों पासवर्ड एक जैसे नहीं हैं।"
        else -> null
    }

    fun validateLogin(email: String, password: String): String? = when {
        !isValidEmail(email) -> "सही ईमेल लिखिए।"
        password.isEmpty() -> "पासवर्ड लिखिए।"
        else -> null
    }
}
