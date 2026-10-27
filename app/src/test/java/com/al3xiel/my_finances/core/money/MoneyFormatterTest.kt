package com.al3xiel.my_finances.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatterTest {
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test
    fun formatsWithSymbolGroupingAndTwoDecimals() {
        assertEquals("S/ 0.05", MoneyFormatter.format(pen(5)))
        assertEquals("S/ 12.50", MoneyFormatter.format(pen(1_250)))
        assertEquals("S/ 1,234,567.89", MoneyFormatter.format(pen(123_456_789)))
    }

    @Test
    fun formatsNegativeWithLeadingMinus() {
        assertEquals("-S/ 3.00", MoneyFormatter.format(pen(-300)))
    }

    @Test
    fun parsesTypedTextToMinorUnits() {
        assertEquals(1_250L, MoneyFormatter.parseMinor("12.50"))
        assertEquals(1_200L, MoneyFormatter.parseMinor("12"))
        assertEquals(1_250L, MoneyFormatter.parseMinor("12,5"))
        assertEquals(5L, MoneyFormatter.parseMinor("0.05"))
        assertEquals(123_456L, MoneyFormatter.parseMinor("1,234.56"))
    }

    @Test
    fun parseRejectsInvalidInput() {
        assertNull(MoneyFormatter.parseMinor(""))
        assertNull(MoneyFormatter.parseMinor("abc"))
        assertNull(MoneyFormatter.parseMinor("1.234"))
        assertNull(MoneyFormatter.parseMinor("-5"))
        assertNull(MoneyFormatter.parseMinor("99999999999999999999"))
    }
}
