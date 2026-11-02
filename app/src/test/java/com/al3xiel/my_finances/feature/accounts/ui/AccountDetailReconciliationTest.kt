package com.al3xiel.my_finances.feature.accounts.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** S51: botón "Conciliar" y la última conciliación de la cuenta con su estado y la marca `needsReview`. */
class AccountDetailReconciliationTest {
    @get:Rule val main = MainDispatcherRule()

    private val accounts = FakeAccountRepository()
    private val reconciliations = FakeReconciliationRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val bcp = AccountId("bcp")

    private fun account(archived: Boolean = false) {
        accounts.accounts[bcp] = Account(bcp, "BCP", AccountType.BANK, CurrencyCode.PEN, null, archived)
    }

    private fun reconciliation(
        id: String,
        status: ReconciliationStatus,
        checkedAt: String = "2026-10-25T10:00:00Z",
        needsReview: Boolean = false,
    ) = Reconciliation(
        ReconciliationId(id), bcp, Money(0, CurrencyCode.PEN), Money(0, CurrencyCode.PEN), status, null, null, null,
        Instant.parse(checkedAt), null, needsReview,
    ).also { reconciliations.reconciliations[it.id] = it }

    private fun viewModel() = AccountDetailViewModel(bcp.value, accounts, clock, reconciliations)

    @Test
    fun anActiveAccountCanBeReconciledAndHasNoPreviousOne() {
        account()

        val state = viewModel().uiState.value

        assertTrue(state.canReconcile)
        assertNull(state.latestReconciliation)
        assertNull(state.latestReconciledOn)
    }

    @Test
    fun anArchivedAccountCannotBeReconciled() {
        account(archived = true)

        assertFalse(viewModel().uiState.value.canReconcile)
    }

    @Test
    fun showsTheLatestReconciliationWithItsStatusDateAndReviewMark() {
        account()
        reconciliation("r1", ReconciliationStatus.RESOLVED, needsReview = true)

        val state = viewModel().uiState.value

        assertEquals(ReconciliationStatus.RESOLVED, state.latestReconciliation?.status)
        assertTrue(state.latestReconciliation?.needsReview == true)
        assertEquals(LocalDate.of(2026, 10, 25), state.latestReconciledOn)
        assertFalse(state.hasOpenReconciliation)
    }

    @Test
    fun anOpenOneTurnsTheButtonIntoContinue() {
        account()
        reconciliation("r1", ReconciliationStatus.OPEN)

        assertTrue(viewModel().uiState.value.hasOpenReconciliation)
    }
}
