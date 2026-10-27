package com.al3xiel.my_finances.feature.pools.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PersonReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PoolDetail
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.Receivables
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ReceivablesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val source = MutableStateFlow(empty())
    private val repository = object : ReceivablesRepository {
        override fun observeReceivables(): Flow<Receivables> = source
        override fun observePoolDetail(poolId: PoolId): Flow<PoolDetail?> = flowOf(null)
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun empty() = Receivables(Money.zero(CurrencyCode.PEN), emptyList(), emptyList(), emptyList())

    private fun pool(id: String, name: String, period: Long, total: Long) =
        PoolReceivable(PoolId(id), name, PoolKind.PERMANENT, PersonId("mom"), "Mamá", pen(period), pen(total))

    private fun viewModel() = ReceivablesViewModel(repository)

    @Test
    fun startsEmptyWithATotalOfZero() {
        val state = viewModel().uiState.value

        assertTrue(state.isEmpty)
        assertEquals(0L, state.total.amountMinor)
    }

    @Test
    fun showsTheTotalPeoplePoolsAndLoans() {
        val loan = LoanReceivable(LoanId("l1"), PersonId("pepe"), "Pepe", pen(5_000), pen(2_000), LocalDate.of(2026, 11, 4))
        source.value = Receivables(
            pen(14_000),
            listOf(PersonReceivable(PersonId("mom"), "Mamá", pen(11_000), pen(0)), PersonReceivable(PersonId("pepe"), "Pepe", pen(0), pen(3_000))),
            listOf(pool("dogs", "Perros", 6_000, 11_000)),
            listOf(loan),
        )

        val state = viewModel().uiState.value

        assertFalse(state.isEmpty)
        assertEquals(14_000L, state.total.amountMinor)
        assertEquals(listOf("Mamá", "Pepe"), state.people.map { it.alias })
        assertEquals(listOf(PoolId("dogs")), state.pools.map { it.poolId })
        assertEquals(listOf(LoanId("l1")), state.loans.map { it.loanId })
    }

    @Test
    fun poolsWithMoreInTheOpenPeriodComeFirstThenByName() {
        source.value = empty().copy(
            pools = listOf(pool("b", "Beta", 0, 100), pool("a", "Alfa", 0, 100), pool("c", "Gama", 500, 600)),
        )

        assertEquals(listOf("Gama", "Alfa", "Beta"), viewModel().uiState.value.pools.map { it.name })
    }

    @Test
    fun followsTheRepositoryWhenTheDataChanges() {
        val vm = viewModel()
        assertTrue(vm.uiState.value.isEmpty)

        source.value = empty().copy(total = pen(300), loans = listOf(LoanReceivable(LoanId("l"), PersonId("p"), "P", pen(300), pen(0), LocalDate.of(2026, 11, 1))))

        assertEquals(300L, vm.uiState.value.total.amountMinor)
        assertFalse(vm.uiState.value.isEmpty)
    }
}
