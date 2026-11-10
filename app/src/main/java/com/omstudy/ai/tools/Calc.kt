package com.omstudy.ai.tools

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * सुरक्षित कैलकुलेटर: + − × ÷ ( ) और % (किसी संख्या के बाद % = उसे 100 से भाग)।
 * BigDecimal इस्तेमाल होता है, इसलिए 0.1 + 0.2 = 0.3 आता है।
 */
object Calc {
    class CalcException(message: String) : Exception(message)

    private val MC = MathContext(15, RoundingMode.HALF_UP)

    fun evaluate(input: String): String {
        val s = input.replace('×', '*').replace('÷', '/').replace(" ", "")
        if (s.isEmpty()) throw CalcException("पहले कुछ लिखिए")
        if (s.length > 80) throw CalcException("बहुत लंबा हिसाब है")
        val parser = Parser(s)
        val value = parser.parseExpr()
        if (parser.pos != s.length) throw CalcException("हिसाब गलत लिखा है")
        return format(value)
    }

    private fun format(v: BigDecimal): String =
        if (v.signum() == 0) "0" else v.stripTrailingZeros().toPlainString()

    private class Parser(val s: String) {
        var pos = 0

        fun parseExpr(): BigDecimal {
            var v = parseTerm()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val r = parseTerm()
                v = if (op == '+') v.add(r) else v.subtract(r)
            }
            return v
        }

        fun parseTerm(): BigDecimal {
            var v = parseFactor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val r = parseFactor()
                v = if (op == '*') {
                    v.multiply(r)
                } else {
                    if (r.signum() == 0) throw CalcException("0 से भाग नहीं हो सकता")
                    v.divide(r, MC)
                }
            }
            return v
        }

        fun parseFactor(): BigDecimal {
            if (pos < s.length && s[pos] == '-') {
                pos++
                return parseFactor().negate()
            }
            if (pos < s.length && s[pos] == '+') {
                pos++
                return parseFactor()
            }
            var v = parsePrimary()
            while (pos < s.length && s[pos] == '%') {
                pos++
                v = v.divide(BigDecimal(100))
            }
            return v
        }

        fun parsePrimary(): BigDecimal {
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = parseExpr()
                if (pos >= s.length || s[pos] != ')') throw CalcException("कोष्ठक ( ) पूरा नहीं है")
                pos++
                return v
            }
            val start = pos
            var dots = 0
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) {
                if (s[pos] == '.') dots++
                pos++
            }
            val text = s.substring(start, pos)
            if (text.isEmpty() || text == "." || dots > 1) throw CalcException("हिसाब गलत लिखा है")
            return BigDecimal(text)
        }
    }
}
