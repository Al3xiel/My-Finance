package com.al3xiel.my_finances.feature.reports.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PeriodSelectorTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)
    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun nextMovesForwardUntilTheCurrentMonth() {
        val changes = mutableListOf<ReportPeriod>()
        rule.setContent { PeriodSelector(ReportPeriod.Month(YearMonth.of(2026, 10)), today, onPeriodChange = { changes += it }) }
        rule.onNodeWithContentDescription(string(R.string.report_period_next)).assertIsEnabled().performClick()
        assertEquals(ReportPeriod.Month(YearMonth.of(2026, 11)), changes.single())
    }

    @Test fun nextIsDisabledOnTheCurrentPeriod() {
        rule.setContent { PeriodSelector(ReportPeriod.Month(YearMonth.of(2026, 11)), today, onPeriodChange = {}) }
        rule.onNodeWithContentDescription(string(R.string.report_period_next)).assertIsNotEnabled()
    }

    @Test fun previousGoesBackAMonth() {
        val changes = mutableListOf<ReportPeriod>()
        rule.setContent { PeriodSelector(ReportPeriod.Month(YearMonth.of(2026, 1)), today, onPeriodChange = { changes += it }) }
        rule.onNodeWithContentDescription(string(R.string.report_period_previous)).performClick()
        assertEquals(ReportPeriod.Month(YearMonth.of(2025, 12)), changes.single())
    }

    @Test fun labelsFollowTheKindOfPeriod() {
        assertEquals("2026", periodLabel(ReportPeriod.Year(2026), Locale.ENGLISH))
        assertEquals("October 2026", periodLabel(ReportPeriod.Month(YearMonth.of(2026, 10)), Locale.ENGLISH))
    }
}
