package com.al3xiel.my_finances.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El saldo real de una conciliación puede ser 0 o negativo (D34): su campo acepta un signo menos al inicio. */
class MoneyInputSignedTest {
    @Test
    fun keepsOnlyALeadingMinusWhenNegativesAreAllowed() {
        assertEquals("-12.5", MoneyInputFilter.sanitize("-12,5", allowNegative = true))
        assertEquals("-", MoneyInputFilter.sanitize("-", allowNegative = true))
        assertEquals("-15", MoneyInputFilter.sanitize("-1-5", allowNegative = true))
        assertEquals("-0.5", MoneyInputFilter.sanitize("−.5", allowNegative = true))
        assertEquals("15", MoneyInputFilter.sanitize("1-5", allowNegative = true))
    }

    @Test
    fun theDefaultStillDropsTheMinus() {
        assertEquals("15", MoneyInputFilter.sanitize("-15"))
    }

    @Test
    fun convertsSignedTextToMinorUnits() {
        assertEquals(-1_500L, MoneyInputFilter.toSignedMinor("-15"))
        assertEquals(-5L, MoneyInputFilter.toSignedMinor("-0.05"))
        assertEquals(1_250L, MoneyInputFilter.toSignedMinor("12.5"))
        assertEquals(0L, MoneyInputFilter.toSignedMinor("0"))
        assertEquals(0L, MoneyInputFilter.toSignedMinor("-0"))
        assertNull(MoneyInputFilter.toSignedMinor("-"))
        assertNull(MoneyInputFilter.toSignedMinor(""))
    }
}
