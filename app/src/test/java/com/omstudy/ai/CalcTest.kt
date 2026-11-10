package com.omstudy.ai

import com.omstudy.ai.tools.Calc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalcTest {
    @Test fun addition() = assertEquals("5", Calc.evaluate("2+3"))
    @Test fun precedence() = assertEquals("14", Calc.evaluate("2+3×4"))
    @Test fun brackets() = assertEquals("20", Calc.evaluate("(2+3)×4"))
    @Test fun division() = assertEquals("2.5", Calc.evaluate("10÷4"))
    @Test fun repeatingDivision() = assertEquals("0.333333333333333", Calc.evaluate("1÷3"))
    @Test fun decimalAccuracy() = assertEquals("0.3", Calc.evaluate("0.1+0.2"))
    @Test fun percent() = assertEquals("0.5", Calc.evaluate("50%"))
    @Test fun unaryMinus() = assertEquals("-3", Calc.evaluate("-5+2"))
    @Test fun zeroResult() = assertEquals("0", Calc.evaluate("5-5"))
    @Test fun bigNumbers() = assertEquals("9999999999800000000001", Calc.evaluate("99999999999×99999999999"))

    @Test fun divideByZero() {
        assertThrows(Calc.CalcException::class.java) { Calc.evaluate("5÷0") }
    }

    @Test fun incompleteExpression() {
        assertThrows(Calc.CalcException::class.java) { Calc.evaluate("2+") }
    }

    @Test fun emptyInput() {
        assertThrows(Calc.CalcException::class.java) { Calc.evaluate("") }
    }

    @Test fun doubleDot() {
        assertThrows(Calc.CalcException::class.java) { Calc.evaluate("1..2") }
    }

    @Test fun unclosedBracket() {
        assertThrows(Calc.CalcException::class.java) { Calc.evaluate("(2+3") }
    }
}
