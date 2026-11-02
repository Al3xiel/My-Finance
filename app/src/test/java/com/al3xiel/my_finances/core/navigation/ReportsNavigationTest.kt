package com.al3xiel.my_finances.core.navigation

import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.YearMonth
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsNavigationTest {
    @Test
    fun everyReportRouteSurvivesSavingAndRestoringTheTabState() {
        val routes = listOf<Route>(
            ReportByCategory("2026-10-01", "2026-10-31"), ReportByTag("2026-10-01", "2026-10-31"),
            ReportByAccount("2026-01-01", "2026-12-31"), ReportExport("2026-10-01", "2026-10-09"),
            MovementsFiltered(from = "2026-10-01", to = "2026-10-31", categoryId = "food"),
        )
        routes.forEach { route ->
            val state = TabBackStacks().select(MainTab.REPORTS).push(route)
            val restored = Json.decodeFromString(TabBackStacks.serializer(), Json.encodeToString(TabBackStacks.serializer(), state))
            assertEquals(state, restored)
            assertEquals(listOf(Reports, route), restored.activeStack)
        }
    }

    @Test
    fun theRouteTextsRebuildTheSamePeriod() {
        val month = ReportPeriod.Month(YearMonth.of(2026, 10))
        val route = ReportByCategory(month.from.toString(), month.to.toString())
        assertEquals(month, ReportPeriod.fromTexts(route.from, route.to))
    }

    @Test
    fun reportsIsStillATabRoot() {
        assertEquals(MainTab.REPORTS, MainTab.fromRoot(Reports))
    }
}
