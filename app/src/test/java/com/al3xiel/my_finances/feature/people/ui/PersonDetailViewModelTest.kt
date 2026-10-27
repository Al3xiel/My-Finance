package com.al3xiel.my_finances.feature.people.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.model.PersonDetail
import com.al3xiel.my_finances.feature.people.domain.repository.PersonDetailRepository
import com.al3xiel.my_finances.feature.people.domain.rules.PersonBalanceGuard
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PersonDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val mom = PersonId("mom")
    private val details = MutableStateFlow<Map<PersonId, PersonDetail>>(emptyMap())
    private val balances = mutableMapOf<PersonId, Long>()
    private val repository = object : PersonDetailRepository {
        override fun observePersonDetail(personId: PersonId): Flow<PersonDetail?> = details.map { it[personId] }
        override suspend fun balanceMinor(personId: PersonId) = balances[personId] ?: 0L
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun detail(balance: Long) = PersonDetail(
        person = Person(mom, "Mamá", false),
        balance = pen(balance),
        pools = listOf(PoolReceivable(PoolId("dogs"), "Perros", PoolKind.PERMANENT, mom, "Mamá", pen(600), pen(balance))),
        loans = emptyList(),
        reimbursements = emptyList(),
        parts = emptyList(),
    )

    private fun viewModel() = PersonDetailViewModel(mom.value, repository)

    @Test
    fun showsThePersonWithTheirBalance() {
        details.value = mapOf(mom to detail(11_000))

        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertFalse(state.notFound)
        assertEquals("Mamá", state.detail!!.person.alias)
        assertEquals(11_000L, state.detail!!.balance.amountMinor)
    }

    @Test
    fun anUnknownPersonIsNotFound() {
        val state = viewModel().uiState.value

        assertTrue(state.notFound)
        assertNull(state.detail)
    }

    @Test
    fun followsTheRepositoryWhenTheBalanceChanges() {
        details.value = mapOf(mom to detail(11_000))
        val vm = viewModel()

        details.value = mapOf(mom to detail(4_000))

        assertEquals(4_000L, vm.uiState.value.detail!!.balance.amountMinor)
    }

    @Test
    fun aPersonWithAPendingBalanceCannotBeArchived() = runTest {
        val guard = PersonBalanceGuard(repository)
        balances[mom] = 1

        assertEquals(BusinessError.BalanceNotZero, guard.check(mom))

        balances[mom] = 0
        assertNull(guard.check(mom))
    }
}
