package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NextOccurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun sameDayNextMonth() {
        assertEquals(ExpectedRange(d(2026, 11, 10), d(2026, 11, 15)), NextOccurrence.of(ExpectedRange(d(2026, 10, 10), d(2026, 10, 15))))
    }

    @Test
    fun aRangeThatCrossesAMonthKeepsTheCrossing() {
        assertEquals(ExpectedRange(d(2026, 11, 28), d(2026, 12, 5)), NextOccurrence.of(ExpectedRange(d(2026, 10, 28), d(2026, 11, 5))))
    }

    @Test
    fun lastDayOfMonthFallsOnTheLastDayOfTheNext() {
        assertEquals(d(2026, 11, 30), NextOccurrence.shift(d(2026, 10, 31)))
        assertEquals(d(2027, 2, 28), NextOccurrence.shift(d(2027, 1, 31)))
        assertEquals(d(2028, 2, 29), NextOccurrence.shift(d(2028, 1, 31)))
        assertEquals(d(2027, 3, 31), NextOccurrence.shift(d(2027, 2, 28)))
    }

    @Test
    fun january29And30LandOnFebruary28AndStayThere() {
        assertEquals(d(2027, 2, 28), NextOccurrence.shift(d(2027, 1, 29)))
        assertEquals(d(2027, 2, 28), NextOccurrence.shift(d(2027, 1, 30)))
        assertEquals(d(2027, 3, 31), NextOccurrence.shift(d(2027, 2, 28)))
    }

    @Test
    fun rollsOverTheYear() {
        assertEquals(d(2027, 1, 5), NextOccurrence.shift(d(2026, 12, 5)))
    }
}
