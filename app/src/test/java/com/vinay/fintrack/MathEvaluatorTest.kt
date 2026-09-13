package com.vinay.fintrack

import com.vinay.fintrack.data.MathEvaluator
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.friendlyCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MathEvaluatorTest {

    @Test
    fun testBasicAddition() {
        val res = MathEvaluator.evaluate("56+4")
        assertNotNull(res)
        assertEquals(60.0, res!!, 0.001)
        assertEquals("60", MathEvaluator.formatResult(res))
    }

    @Test
    fun testSubtractionAndDecimals() {
        val res = MathEvaluator.evaluate("100-20.50")
        assertNotNull(res)
        assertEquals(79.50, res!!, 0.001)
        assertEquals("79.50", MathEvaluator.formatResult(res))
    }

    @Test
    fun testMultiplicationAndDivision() {
        val res = MathEvaluator.evaluate("25 * 4 + 10 / 2")
        assertNotNull(res)
        assertEquals(105.0, res!!, 0.001)
    }

    @Test
    fun testHasMathOperation() {
        assertTrue(MathEvaluator.hasMathOperation("56+4"))
        assertTrue(MathEvaluator.hasMathOperation("100 - 20"))
        assertTrue(MathEvaluator.hasMathOperation("10*5"))
        assertTrue(MathEvaluator.hasMathOperation("100/4"))
        assertFalse(MathEvaluator.hasMathOperation("56"))
        assertFalse(MathEvaluator.hasMathOperation("56.50"))
        assertFalse(MathEvaluator.hasMathOperation("-50"))
    }

    @Test
    fun testInrTwoDecimals() {
        assertEquals("₹120.50", inr(120.50))
        assertEquals("₹40.00", inr(40.00))
        assertEquals("₹0.00", inr(0.00))
        assertEquals("-₹50.25", inr(-50.25))
    }

    @Test
    fun testFriendlyCycle() {
        assertEquals("Sep 2026", friendlyCycle("2026-09"))
        assertEquals("Jan 2027", friendlyCycle("2027-01"))
        assertEquals("Dec 2025", friendlyCycle("2025-12"))
    }
}
