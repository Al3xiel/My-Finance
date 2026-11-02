package com.al3xiel.my_finances.feature.reconciliation.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.MovementLinkedToReconciliation
import com.al3xiel.my_finances.domain.model.event.ReconciliationCancelled
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationDetail
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationMovement
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.CancelReconciliationUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.LinkMovementUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ReconciliationDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val reconciliations = FakeReconciliationRepository()
    private val r1 = ReconciliationId("r1")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun reconciliation(status: ReconciliationStatus = ReconciliationStatus.OPEN) =
        Reconciliation(r1, f.bbva, pen(81_240), pen(80_000), status, null, null, null, Instant.EPOCH, null, false)
            .also { reconciliations.reconciliations[r1] = it }

    private fun movement(id: String) =
        ReconciliationMovement(MovementId(id), MovementType.INCOME, f.bbva, null, pen(1_000), f.today, "reembolso", MovementStatus.ACTIVE)

    private fun detail(difference: Long, linked: List<ReconciliationMovement> = emptyList(), suggestions: List<ReconciliationMovement> = emptyList()) =
        ReconciliationDetail(reconciliation(), pen(81_240 - difference), pen(difference), linked, suggestions)

    private fun viewModel() = ReconciliationDetailViewModel(
        "r1",
        reconciliations,
        f.accounts,
        LinkMovementUseCase(reconciliations, f.movements, ReconciliationGuards(reconciliations), f.recorder),
        CancelReconciliationUseCase(reconciliations, f.recorder),
    )

    @Test
    fun mirrorsTheLiveDetailAndNamesTheAccount() {
        val flow = reconciliations.details.getOrPut(r1) { kotlinx.coroutines.flow.MutableStateFlow(null) }
        flow.value = detail(1_240)
        val vm = viewModel()

        assertEquals(pen(1_240), vm.uiState.value.detail?.difference)
        assertEquals("BBVA", vm.uiState.value.accountName)
        assertTrue(vm.uiState.value.canAct)

        flow.value = detail(240, linked = listOf(movement("m1")))
        assertEquals(pen(240), vm.uiState.value.detail?.difference)
        assertEquals(1, vm.uiState.value.detail?.linked?.size)
    }

    @Test
    fun aMissingReconciliationIsNotFoundOnceLoaded() {
        val vm = viewModel()

        assertTrue(vm.uiState.value.notFound)
        assertNull(vm.uiState.value.detail)
    }

    @Test
    fun aClosedReconciliationIsReadOnly() {
        reconciliations.details.getOrPut(r1) { kotlinx.coroutines.flow.MutableStateFlow(null) }.value =
            detail(0).copy(reconciliation = reconciliation(ReconciliationStatus.RESOLVED))

        assertFalse(viewModel().uiState.value.canAct)
    }

    @Test
    fun linkingASuggestionRecordsTheLink() {
        reconciliations.details.getOrPut(r1) { kotlinx.coroutines.flow.MutableStateFlow(null) }.value = detail(1_240)
        f.incomeMovement("m1", f.bbva, 1_000)
        val vm = viewModel()

        vm.onAction(ReconciliationDetailUiAction.Link(MovementId("m1")))

        assertEquals(MovementLinkedToReconciliation(r1, MovementId("m1")), f.store.events.single().event)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun aRejectedLinkShowsTheErrorAndItCanBeDismissed() {
        reconciliations.details.getOrPut(r1) { kotlinx.coroutines.flow.MutableStateFlow(null) }.value = detail(1_240)
        f.incomeMovement("c1", f.cash, 100)
        val vm = viewModel()

        vm.onAction(ReconciliationDetailUiAction.Link(MovementId("c1")))
        assertEquals(BusinessError.MovementNotInAccount, vm.uiState.value.error)

        vm.onAction(ReconciliationDetailUiAction.DismissError)
        assertNull(vm.uiState.value.error)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun cancellingAsksForTheReasonThenRecordsItAndCloses() {
        reconciliations.details.getOrPut(r1) { kotlinx.coroutines.flow.MutableStateFlow(null) }.value = detail(1_240)
        val vm = viewModel()

        vm.onAction(ReconciliationDetailUiAction.AskCancel)
        assertTrue(vm.uiState.value.askingCancel)
        vm.onAction(ReconciliationDetailUiAction.DismissCancel)
        assertFalse(vm.uiState.value.askingCancel)

        vm.onAction(ReconciliationDetailUiAction.AskCancel)
        vm.onAction(ReconciliationDetailUiAction.ConfirmCancel("me equivoqué de cuenta"))

        assertEquals(ReconciliationCancelled(r1, "me equivoqué de cuenta"), f.store.events.single().event)
        assertTrue(vm.uiState.value.closed)
        assertFalse(vm.uiState.value.askingCancel)
    }

    @Test
    fun aBlankReasonKeepsTheDialogAndShowsTheError() {
        reconciliations.details.getOrPut(r1) { kotlinx.coroutines.flow.MutableStateFlow(null) }.value = detail(1_240)
        val vm = viewModel()
        vm.onAction(ReconciliationDetailUiAction.AskCancel)

        vm.onAction(ReconciliationDetailUiAction.ConfirmCancel("  "))

        assertEquals(BusinessError.ReasonRequired, vm.uiState.value.error)
        assertFalse(vm.uiState.value.closed)
        assertTrue(f.store.events.isEmpty())
    }
}
