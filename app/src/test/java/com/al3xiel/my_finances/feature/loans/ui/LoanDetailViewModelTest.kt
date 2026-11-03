package com.al3xiel.my_finances.feature.loans.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.RepaymentId
import com.al3xiel.my_finances.domain.model.event.LoanRepaymentReceived
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.feature.loans.domain.model.Loan
import com.al3xiel.my_finances.feature.loans.domain.model.LoanDetail
import com.al3xiel.my_finances.feature.loans.domain.model.LoanRepaymentLine
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import com.al3xiel.my_finances.feature.loans.domain.repository.LoanRepository
import com.al3xiel.my_finances.feature.loans.domain.rules.LoanVoidGuard
import com.al3xiel.my_finances.feature.loans.domain.usecase.ReceiveLoanRepaymentUseCase
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LoanDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val pepe = PersonId("pepe")
    private val loanId = LoanId("l1")
    private val details = MutableStateFlow<Map<LoanId, LoanDetail>>(emptyMap())
    private val loans = object : LoanRepository {
        override suspend fun find(id: LoanId) = details.value[id]?.loan
        override suspend fun findByMovement(movementId: MovementId) = details.value.values.map { it.loan }.firstOrNull { it.movementId == movementId }
        override fun observeDetail(id: LoanId): Flow<LoanDetail?> = details.map { it[id] }
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    init {
        f.accounts.balances[f.bbva] = 100_000
    }

    /** Préstamo de S/ 50 con `repaid` ya pagado (un solo pago `mr1` si es mayor a cero). */
    private fun loan(repaid: Long) {
        val loan = Loan(loanId, pepe, MovementId("ml1"), pen(5_000), pen(repaid), if (repaid >= 5_000) LoanStatus.REPAID else LoanStatus.OPEN, LocalDate.of(2026, 11, 4))
        val payments = if (repaid > 0) listOf(LoanRepaymentLine(MovementId("mr1"), pen(repaid), LocalDate.of(2026, 11, 5))) else emptyList()
        details.value = mapOf(loanId to LoanDetail(loan, "Pepe", payments))
        movement("ml1", MovementType.LOAN_DISBURSEMENT, 5_000)
        if (repaid > 0) movement("mr1", MovementType.LOAN_REPAYMENT, repaid)
    }

    private fun movement(id: String, type: MovementType, minor: Long) {
        f.movements.movements[MovementId(id)] = Movement(MovementId(id), type, f.bbva, null, pen(minor), null, null, f.today, MovementStatus.ACTIVE, 1, emptyList())
    }

    private fun viewModel() = LoanDetailViewModel(
        loanId.value, loans, f.accounts,
        ReceiveLoanRepaymentUseCase(loans, f.rules, f.recorder, f.ids, f.writeLock),
        VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock, setOf(LoanVoidGuard(loans))),
        f.clock,
    )

    @Test
    fun showsTheLoanItsPaymentsAndTheAccounts() {
        loan(repaid = 2_000)

        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals("Pepe", state.detail!!.personName)
        assertEquals(3_000L, state.detail!!.loan.pending.amountMinor)
        assertEquals(listOf(MovementId("mr1")), state.detail!!.repayments.map { it.movementId })
        assertEquals(listOf("BBVA", "Efectivo"), state.accountOptions.map { it.label })
    }

    @Test
    fun anUnknownLoanIsNotFound() {
        assertTrue(viewModel().uiState.value.notFound)
    }

    @Test
    fun registeringAPaymentEmitsTheEventAndClosesTheDialog() {
        loan(repaid = 0)
        val vm = viewModel()
        vm.onAction(LoanDetailUiAction.OpenPay)
        assertEquals(f.bbva, vm.uiState.value.pay!!.accountId)

        vm.onAction(LoanDetailUiAction.EditPayAmount("20"))
        vm.onAction(LoanDetailUiAction.ConfirmPay)

        val event = f.store.events.single().event as LoanRepaymentReceived
        assertEquals(RepaymentId("id-1"), event.repaymentId)
        assertEquals(loanId, event.loanId)
        assertEquals(2_000L, event.amount.amountMinor)
        assertEquals(f.bbva, event.accountId)
        assertNull(vm.uiState.value.pay)
    }

    @Test
    fun aPaymentAboveThePendingKeepsTheDialogOpenWithTheError() {
        loan(repaid = 0)
        val vm = viewModel()
        vm.onAction(LoanDetailUiAction.OpenPay)
        vm.onAction(LoanDetailUiAction.EditPayAmount("51"))

        vm.onAction(LoanDetailUiAction.ConfirmPay)

        assertEquals(BusinessError.ExceedsPending, vm.uiState.value.pay!!.error)
        assertEquals(0, f.store.events.size)

        vm.onAction(LoanDetailUiAction.EditPayAmount("50"))
        assertNull(vm.uiState.value.pay!!.error)
    }

    @Test
    fun anEmptyPaymentIsRejectedAndTheDialogCanBeDismissed() {
        loan(repaid = 0)
        val vm = viewModel()
        vm.onAction(LoanDetailUiAction.OpenPay)

        vm.onAction(LoanDetailUiAction.ConfirmPay)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.pay!!.error)

        vm.onAction(LoanDetailUiAction.DismissPay)
        assertNull(vm.uiState.value.pay)
    }

    @Test
    fun voidingAPaymentAsksForAReasonThenEmitsMovementVoided() {
        loan(repaid = 2_000)
        val vm = viewModel()

        vm.onAction(LoanDetailUiAction.AskVoidRepayment(MovementId("mr1")))
        assertNotNull(vm.uiState.value.voiding)
        vm.onAction(LoanDetailUiAction.ConfirmVoid("monto mal"))

        assertEquals(MovementVoided(MovementId("mr1"), "monto mal", null), f.store.events.single().event)
        assertNull(vm.uiState.value.voiding)
    }

    @Test
    fun voidingWithoutAReasonShowsTheError() {
        loan(repaid = 2_000)
        val vm = viewModel()
        vm.onAction(LoanDetailUiAction.AskVoidRepayment(MovementId("mr1")))

        vm.onAction(LoanDetailUiAction.ConfirmVoid("  "))

        assertEquals(BusinessError.ReasonRequired, vm.uiState.value.notice)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun aLoanWithPaymentsCannotBeVoidedUntilThePaymentsAre() {
        loan(repaid = 2_000)
        val vm = viewModel()
        vm.onAction(LoanDetailUiAction.AskVoidLoan)

        vm.onAction(LoanDetailUiAction.ConfirmVoid("error"))

        assertEquals(BusinessError.HasRepayments, vm.uiState.value.notice)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun aLoanWithoutPaymentsCanBeVoided() {
        loan(repaid = 0)
        val vm = viewModel()
        vm.onAction(LoanDetailUiAction.AskVoidLoan)

        vm.onAction(LoanDetailUiAction.ConfirmVoid("error"))

        assertEquals(MovementVoided(MovementId("ml1"), "error", null), f.store.events.single().event)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun voidingAPaymentThatLeavesTheBalanceNegativeWarnsAndSavesWhenConfirmed() {
        f.accounts.balances[f.bbva] = 1_000
        loan(repaid = 2_000)
        val vm = viewModel()
        vm.onAction(LoanDetailUiAction.AskVoidRepayment(MovementId("mr1")))

        vm.onAction(LoanDetailUiAction.ConfirmVoid("monto mal"))
        assertEquals(BusinessError.WouldGoNegative(-1_000), vm.uiState.value.negativeWarning)
        assertEquals(0, f.store.events.size)

        vm.onAction(LoanDetailUiAction.ConfirmNegativeBalance)

        assertEquals(MovementVoided(MovementId("mr1"), "monto mal", null), f.store.events.single().event)
        assertNull(vm.uiState.value.negativeWarning)
        assertFalse(vm.uiState.value.notFound)
    }
}
