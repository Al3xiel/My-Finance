package com.al3xiel.my_finances.core.navigation

import androidx.navigation3.runtime.entryProvider
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.home.di.HomeNavigationModule
import com.al3xiel.my_finances.feature.home.di.destination
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeNavigationTest {
    private val routes = HomeNavigationModule.routes()

    @Test
    fun homeContributesTheScreenOfItsOwnRouteOnly() {
        val provider = entryProvider<Route>(fallback = { error("no screen for $it") }) {
            with(routes) { register() }
        }
        provider(Home)
        assertThrows(IllegalStateException::class.java) { provider(Reports) }
    }

    @Test
    fun eachAlertOpensItsDestination() {
        assertEquals(ReconciliationDetail("r1"), HomeAlert.NeedsReview(ReconciliationId("r1")).destination())
        assertEquals(ReconciliationDetail("r2"), HomeAlert.OpenReconciliation(ReconciliationId("r2")).destination())
        assertEquals(IncomeEditor(expectedIncomeId = "e1"), HomeAlert.OverdueIncome(ExpectedIncomeId("e1")).destination())
        assertEquals(Backup, HomeAlert.BackupOverdue.destination())
    }
}
