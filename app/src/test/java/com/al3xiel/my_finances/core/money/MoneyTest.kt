package com.al3xiel.my_finances.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun sumsAndSubtractsMinorUnits() {
        val a = Money(1_050, CurrencyCode.PEN)
        val b = Money(250, CurrencyCode.PEN)
        assertEquals(Money(1_300, CurrencyCode.PEN), a + b)
        assertEquals(Money(800, CurrencyCode.PEN), a - b)
    }

    @Test
    fun negateFlipsSign() {
        assertEquals(Money(-5, CurrencyCode.PEN), -Money(5, CurrencyCode.PEN))
    }

    @Test
    fun rejectsMixedCurrencies() {
        val pen = Money(100, CurrencyCode.PEN)
        val usd = Money(100, CurrencyCode.USD)
        assertThrows(IllegalArgumentException::class.java) { pen + usd }
        assertThrows(IllegalArgumentException::class.java) { pen - usd }
    }

    @Test
    fun overflowThrows() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE, CurrencyCode.PEN) + Money(1, CurrencyCode.PEN) }
    }

    @Test
    fun signChecks() {
        assertEquals(true, Money(1, CurrencyCode.PEN).isPositive)
        assertEquals(false, Money(0, CurrencyCode.PEN).isPositive)
        assertEquals(true, Money.zero(CurrencyCode.PEN).isZero)
    }
}
