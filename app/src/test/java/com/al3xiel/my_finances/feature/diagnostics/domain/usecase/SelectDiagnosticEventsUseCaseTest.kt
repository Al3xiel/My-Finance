package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.BackupCreated
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.MovementLinkedToReconciliation
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelection
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectDiagnosticEventsUseCaseTest {
    private val pen = CurrencyCode.PEN
    private val store = FakeEventStore()
    private val useCase = SelectDiagnosticEventsUseCase(store)

    private fun day(n: Int) = LocalDate.of(2026, 10, n)
    private fun append(day: Int, event: DomainEvent) = runBlocking { store.append("c", listOf(NewEvent(event, day(day)))).single().sequence }

    private fun income(movement: String, account: String, reconciliation: String? = null, note: String? = null) =
        IncomeRegistered(MovementId(movement), AccountId(account), Money(1_000, pen), IncomeSourceId("s1"), reconciliation?.let { ReconciliationId(it) }, note)

    private fun expense(movement: String, account: String) = ExpenseRegistered(
        MovementId(movement), AccountId(account), Money(300, pen),
        listOf(ExpensePartData(PartId("part-$movement"), Money(300, pen), CategoryId("c1"), emptyList(), null, null)), null, "cena",
    )

    private fun transfer(movement: String, from: String, to: String) =
        TransferRegistered(MovementId(movement), AccountId(from), AccountId(to), Money(200, pen), null, null, null)

    private fun account(id: String) = AccountCreated(AccountId(id), "name-$id", AccountType.BANK, pen, null, Money(0, pen))

    /** Cuenta a1 con una conciliación r1; a2 y los movimientos de a2 no pertenecen a r1. */
    private val seq = mutableMapOf<String, Long>()

    init {
        listOf(
            "a1" to account("a1"),
            "a2" to account("a2"),
            "a3" to account("a3"),
            "p1" to PersonCreated(PersonId("p1"), "Ana"),
            "c1" to CategoryCreated(CategoryId("c1"), "Comida", "i", "#fff", null),
            "c2" to CategoryCreated(CategoryId("c2"), "Otra", "i", "#fff", null),
            "s1" to IncomeSourceCreated(IncomeSourceId("s1"), "Empresa", PersonId("p1")),
            "m1" to income("m1", "a1", note = "sueldo"),
            "m2" to expense("m2", "a1"),
            "m3" to transfer("m3", "a1", "a2"),
            "m4" to income("m4", "a2"),
            "m5" to transfer("m5", "a3", "a1"),
            "m6" to income("m6", "a3"),
            "check" to BalanceCheckRecorded(ReconciliationId("r1"), AccountId("a1"), Money(9_999, pen), Money(9_000, pen)),
            "m7" to income("m7", "a1", reconciliation = "r1"),
            "link" to MovementLinkedToReconciliation(ReconciliationId("r1"), MovementId("m7")),
            "m8" to income("m8", "a1"),
            "backup" to BackupCreated("b1", 3, "h"),
        ).forEachIndexed { index, (key, event) -> seq[key] = append(1 + index, event) }
    }

    private fun select(request: DiagnosticSelectionRequest): DiagnosticSelection =
        (runBlocking { useCase(request) } as Outcome.Ok).value

    private fun DiagnosticSelection.has(vararg keys: String) = keys.all { seq.getValue(it) in events.map { e -> e.sequence } }

    private fun DiagnosticSelection.lacks(vararg keys: String) = keys.none { seq.getValue(it) in events.map { e -> e.sequence } }

    private val reconciliation = DiagnosticSelectionRequest(DiagnosticScope.RECONCILIATION, reconciliationId = ReconciliationId("r1"))

    @Test
    fun reconciliationScopeTakesTheAccountUntilTheCheckPlusTheLinkedMovements() {
        val selection = select(reconciliation)

        assertTrue(selection.has("a1", "m1", "m2", "m3", "m5", "check", "m7", "link"))
        assertTrue(selection.lacks("m4", "m6", "m8", "backup"))
    }

    @Test
    fun theCutIsExactlyTheSequencesOfTheChosenEventsInEveryScope() {
        val requests = listOf(
            reconciliation,
            DiagnosticSelectionRequest(DiagnosticScope.FULL),
            DiagnosticSelectionRequest(DiagnosticScope.ACCOUNT_HISTORY, accountId = AccountId("a1")),
        )
        requests.forEach { request ->
            val selection = select(request)
            assertEquals(request.scope.name, selection.events.map { it.sequence }.toSet(), selection.cut.includedSequences)
        }
    }

    @Test
    fun theReconciliationCutLeavesOutWhatThePackageLeavesOut() {
        val cut = select(reconciliation).cut.includedSequences
        assertTrue(seq.getValue("m7") in cut)
        assertTrue(seq.getValue("m8") !in cut)
    }

    @Test
    fun aMovementOfTheAccountAfterTheCheckThatIsNotLinkedStaysOut() {
        assertTrue(select(reconciliation).lacks("m8"))
    }

    @Test
    fun aLinkedMovementStaysInEvenThoughItWasRecordedAfterTheCheck() {
        val selection = select(reconciliation)

        assertTrue(seq.getValue("m7") > seq.getValue("check"))
        assertTrue(selection.has("m7"))
    }

    @Test
    fun transfersThatTouchTheAccountComeInBothDirections() {
        val selection = select(reconciliation)

        assertTrue(selection.has("m3", "m5"))
    }

    @Test
    fun referencedCatalogsComeInEvenTransitively() {
        val selection = select(reconciliation)

        assertTrue(selection.has("a2", "a3", "c1", "s1", "p1"))
        assertTrue(selection.lacks("c2"))
    }

    @Test
    fun eventsAreInSequenceOrderWithoutDuplicates() {
        val sequences = select(reconciliation).events.map { it.sequence }

        assertEquals(sequences.sorted().distinct(), sequences)
    }

    @Test
    fun accountHistoryTakesEverythingTheAccountEverDidEvenAfterTheCheck() {
        val selection = select(DiagnosticSelectionRequest(DiagnosticScope.ACCOUNT_HISTORY, accountId = AccountId("a1")))

        assertTrue(selection.has("a1", "m1", "m2", "m3", "m5", "check", "m7", "link", "m8", "c1", "s1", "p1"))
        assertTrue(selection.lacks("m4", "m6", "backup"))
        assertEquals(null, selection.reconciliationId)
    }

    @Test
    fun accountHistoryCanStartFromAReconciliation() {
        val selection = select(DiagnosticSelectionRequest(DiagnosticScope.ACCOUNT_HISTORY, reconciliationId = ReconciliationId("r1")))

        assertTrue(selection.has("m8", "check"))
        assertEquals(ReconciliationId("r1"), selection.reconciliationId)
    }

    @Test
    fun fullScopeTakesEveryEventExceptTheAuditOnesOfBackups() {
        val selection = select(DiagnosticSelectionRequest(DiagnosticScope.FULL))

        assertTrue(selection.has("a1", "m4", "m6", "m8", "c2"))
        assertTrue(selection.lacks("backup"))
    }

    @Test
    fun fullAndHistoryScopesWorkWithoutAnyReconciliation() {
        val store = FakeEventStore()
        runBlocking { store.append("c", listOf(NewEvent(account("a1"), day(1)), NewEvent(income("m1", "a1"), day(2)))) }
        val useCase = SelectDiagnosticEventsUseCase(store)

        val full = (runBlocking { useCase(DiagnosticSelectionRequest(DiagnosticScope.FULL)) } as Outcome.Ok).value
        val history = (runBlocking { useCase(DiagnosticSelectionRequest(DiagnosticScope.ACCOUNT_HISTORY, accountId = AccountId("a1"))) } as Outcome.Ok).value

        assertEquals(2, full.eventCount)
        assertEquals(2, history.eventCount)
        assertEquals(null, full.reconciliationId)
    }

    @Test
    fun reconciliationScopeNeedsAKnownReconciliation() {
        assertEquals(
            Outcome.Failed(BusinessError.NotFound),
            runBlocking { useCase(DiagnosticSelectionRequest(DiagnosticScope.RECONCILIATION)) },
        )
        assertEquals(
            Outcome.Failed(BusinessError.NotFound),
            runBlocking { useCase(DiagnosticSelectionRequest(DiagnosticScope.RECONCILIATION, reconciliationId = ReconciliationId("nope"))) },
        )
    }

    @Test
    fun accountHistoryNeedsAKnownAccount() {
        assertEquals(
            Outcome.Failed(BusinessError.NotFound),
            runBlocking { useCase(DiagnosticSelectionRequest(DiagnosticScope.ACCOUNT_HISTORY)) },
        )
        assertEquals(
            Outcome.Failed(BusinessError.NotFound),
            runBlocking { useCase(DiagnosticSelectionRequest(DiagnosticScope.ACCOUNT_HISTORY, accountId = AccountId("nope"))) },
        )
    }

    @Test
    fun thePreviewSummarizesCountDatesAndAnonymizedFields() {
        val selection = select(reconciliation)

        assertEquals(selection.events.size, selection.eventCount)
        assertEquals(day(1), selection.firstOccurredOn)
        assertEquals(selection.events.maxOf { it.occurredOn }, selection.lastOccurredOn)
        assertTrue("AccountCreated.name" in selection.anonymizedFields)
        assertTrue("IncomeRegistered.note" in selection.anonymizedFields)
        assertFalse("AccountCreated.color" in selection.anonymizedFields)
    }

    @Test
    fun exportedUpToSequenceIsTheLastEventOfTheRegistry() {
        assertEquals(store.events.last().sequence, select(reconciliation).exportedUpToSequence)
    }
}
