package com.al3xiel.my_finances.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyInputTest {
    @Test
    fun keepsDigitsAndOneDecimalSeparatorWithTwoDecimals() {
        assertEquals("12.34", MoneyInputFilter.sanitize("12.345"))
        assertEquals("1.5", MoneyInputFilter.sanitize("1,5"))
        assertEquals("12.3", MoneyInputFilter.sanitize("12.3."))
        assertEquals("12.34", MoneyInputFilter.sanitize("12.3.4"))
        assertEquals("", MoneyInputFilter.sanitize("abc"))
        assertEquals("15", MoneyInputFilter.sanitize("-1x5"))
    }

    @Test
    fun normalizesLeadingZerosAndALoneSeparator() {
        assertEquals("7", MoneyInputFilter.sanitize("007"))
        assertEquals("0", MoneyInputFilter.sanitize("000"))
        assertEquals("0.", MoneyInputFilter.sanitize("."))
        assertEquals("0.5", MoneyInputFilter.sanitize(".5"))
        assertEquals("", MoneyInputFilter.sanitize(""))
    }

    @Test
    fun capsTheIntegerPartSoTheAmountAlwaysFitsALong() {
        assertEquals("123456789", MoneyInputFilter.sanitize("1234567890123"))
    }

    @Test
    fun convertsTypedTextToMinorUnits() {
        assertEquals(1_250L, MoneyInputFilter.toMinor("12.5"))
        assertEquals(1_200L, MoneyInputFilter.toMinor("12."))
        assertEquals(5L, MoneyInputFilter.toMinor("0.05"))
        assertEquals(0L, MoneyInputFilter.toMinor("0"))
        assertNull(MoneyInputFilter.toMinor(""))
        assertNull(MoneyInputFilter.toMinor("."))
    }
}
