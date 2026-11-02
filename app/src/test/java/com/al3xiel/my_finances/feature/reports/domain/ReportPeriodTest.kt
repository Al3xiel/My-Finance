package com.al3xiel.my_finances.feature.reports.domain

import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPeriodTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test fun `month spans its first to last day`() {
        val p = ReportPeriod.Month(YearMonth.of(2026, 2))
        assertEquals(LocalDate.of(2026, 2, 1), p.from)
        assertEquals(LocalDate.of(2026, 2, 28), p.to)
    }

    @Test fun `year spans january to december`() {
        val p = ReportPeriod.Year(2025)
        assertEquals(LocalDate.of(2025, 1, 1), p.from)
        assertEquals(LocalDate.of(2025, 12, 31), p.to)
    }

    @Test fun `closing date of the current period is today`() {
        assertEquals(today, ReportPeriod.Month(YearMonth.of(2026, 11)).closingOn(today))
        assertEquals(LocalDate.of(2026, 10, 31), ReportPeriod.Month(YearMonth.of(2026, 10)).closingOn(today))
        assertEquals(today, ReportPeriod.Year(2026).closingOn(today))
    }

    @Test fun `next of the current month is not allowed`() {
        assertNull(ReportPeriod.Month(YearMonth.of(2026, 11)).next(today))
        assertEquals(ReportPeriod.Month(YearMonth.of(2026, 11)), ReportPeriod.Month(YearMonth.of(2026, 10)).next(today))
        assertEquals(ReportPeriod.Month(YearMonth.of(2025, 12)), ReportPeriod.Month(YearMonth.of(2026, 1)).previous())
    }

    @Test fun `custom range validates order and future`() {
        assertTrue(ReportPeriod.validateCustom(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1), today) != null)
        assertTrue(ReportPeriod.validateCustom(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 3), today) != null)
        assertNull(ReportPeriod.validateCustom(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 2), today))
        assertFalse(ReportPeriod.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)).isFuture(today))
    }

    @Test fun `route texts round trip`() {
        val p = ReportPeriod.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9))
        assertEquals(p, ReportPeriod.fromTexts("2026-10-01", "2026-10-09"))
        assertEquals(ReportPeriod.Month(YearMonth.of(2026, 10)), ReportPeriod.fromTexts("2026-10-01", "2026-10-31"))
    }
}
