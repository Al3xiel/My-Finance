package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderPolicyTest {
    private val to = LocalDate.of(2026, 11, 5)

    @Test
    fun noReminderUntilTheDayAfterTheRange() {
        assertFalse(ReminderPolicy.isDue(to, 0, null, to))
        assertTrue(ReminderPolicy.isDue(to, 0, null, to.plusDays(1)))
    }

    @Test
    fun aMissedFirstDayStillWarnsLater() {
        assertTrue(ReminderPolicy.isDue(to, 0, null, to.plusDays(10)))
    }

    @Test
    fun nextOneComesThreeDaysAfterTheLast() {
        val last = to.plusDays(1)
        assertFalse(ReminderPolicy.isDue(to, 1, last, last.plusDays(2)))
        assertTrue(ReminderPolicy.isDue(to, 1, last, last.plusDays(3)))
    }

    @Test
    fun neverMoreThanThree() {
        assertTrue(ReminderPolicy.isDue(to, 2, to.plusDays(4), to.plusDays(7)))
        assertFalse(ReminderPolicy.isDue(to, 3, to.plusDays(7), to.plusDays(30)))
    }
}
