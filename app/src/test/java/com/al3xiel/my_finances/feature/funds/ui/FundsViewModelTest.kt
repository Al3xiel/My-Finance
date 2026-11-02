package com.al3xiel.my_finances.feature.funds.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FundsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val funds = FakeFundRepository()

    @Test
    fun withoutFundsItIsLoadedAndEmpty() {
        val state = FundsViewModel(funds).uiState.value

        assertTrue(state.loaded)
        assertTrue(state.funds.isEmpty())
        assertEquals(0L, state.summary.reserved.amountMinor)
    }

    @Test
    fun listsTheFundsAndTheSummaryOfWhatIsSetAside() {
        funds.available = 90_000
        funds.put(FundId("fare"), "Pasajes", ConsumptionMode.DAILY_ESTIMATE, 500, allocated = 5_000, detailed = 1_000, usageDays = 2)
        funds.put(FundId("old"), "Viejo", status = FundStatus.CLOSED)

        val state = FundsViewModel(funds).uiState.value

        assertEquals(listOf("Pasajes", "Viejo"), state.funds.map { it.fund.name })
        assertEquals(90_000L, state.summary.available.amountMinor)
        assertFalse(state.funds.isEmpty())
    }
}
