package com.al3xiel.my_finances.core.designsystem

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressFractionTest {
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test fun halfOfTheTotalIsAHalf() = assertEquals(0.5f, progressFraction(pen(500), pen(1_000)), 0.0001f)

    @Test fun aTotalOfZeroOrLessNeverDividesAndDrawsEmpty() {
        assertEquals(0f, progressFraction(pen(500), pen(0)), 0f)
        assertEquals(0f, progressFraction(pen(500), pen(-100)), 0f)
    }

    @Test fun moreThanTheTotalIsClampedToFull() = assertEquals(1f, progressFraction(pen(1_500), pen(1_000)), 0f)

    @Test fun aNegativePartIsClampedToEmpty() = assertEquals(0f, progressFraction(pen(-200), pen(1_000)), 0f)
}
