package com.al3xiel.my_finances.core.navigation

import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.YearMonth
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportOriginNavigationTest {
    @Test
    fun theOriginRouteSurvivesSavingAndRestoringTheTabState() {
        val route = ReportOrigin("2026-10-01", "2026-10-31")
        val state = TabBackStacks().select(MainTab.REPORTS).push(route)
        val restored = Json.decodeFromString(TabBackStacks.serializer(), Json.encodeToString(TabBackStacks.serializer(), state))
        assertEquals(state, restored)
        assertEquals(listOf(Reports, route), restored.activeStack)
    }

    @Test
    fun theOriginRouteTextsRebuildTheSamePeriod() {
        val month = ReportPeriod.Month(YearMonth.of(2026, 10))
        val route = ReportOrigin(month.from.toString(), month.to.toString())
        assertEquals(month, ReportPeriod.fromTexts(route.from, route.to))
    }
}
