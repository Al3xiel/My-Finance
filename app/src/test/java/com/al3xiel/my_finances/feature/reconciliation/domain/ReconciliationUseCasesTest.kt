package com.al3xiel.my_finances.feature.reconciliation.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.ReconciliationCancelled
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.CancelReconciliationUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.RecordBalanceCheckUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReconciliationUseCasesTest {
    private val store = FakeEventStore()
    private val accounts = FakeAccountRepository()
    private val reconciliations = FakeReconciliationRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val ids = SequentialIdGenerator()
    private val recorder = EventRecorder(store, ids, clock)
    private val record = RecordBalanceCheckUseCase(accounts, reconciliations, recorder, ids)
    private val cancel = CancelReconciliationUseCase(reconciliations, recorder)

    private val bbva = AccountId("bbva")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun account(archived: Boolean = false, currency: CurrencyCode = CurrencyCode.PEN) =
        Account(bbva, "BBVA", AccountType.BANK, currency, null, archived).also {
            accounts.accounts[bbva] = it
            accounts.balances[bbva] = 80_000
        }

    private fun open(id: String = "r0", status: ReconciliationStatus = ReconciliationStatus.OPEN) = Reconciliation(
        ReconciliationId(id), bbva, pen(81_240), pen(80_000), status, null, null, null, Instant.EPOCH, null, false,
    ).also { reconciliations.reconciliations[it.id] = it }

    @Test
    fun recordingStoresTheRealBalanceAndTheAppBalanceAsEvidence() = runTest {
        account()

        val result = record(bbva, pen(81_240))

        val id = (result as Outcome.Ok).value
        assertEquals(BalanceCheckRecorded(id, bbva, pen(81_240), pen(80_000)), store.events.single().event)
        assertEquals(LocalDate.of(2026, 11, 2), store.events.single().occurredOn)
    }

    @Test
    fun theRealBalanceCanBeZeroOrNegative() = runTest {
        account()

        assertTrue(record(bbva, pen(0)) is Outcome.Ok)
        reconciliations.reconciliations.clear()
        assertTrue(record(bbva, pen(-1_500)) is Outcome.Ok)
    }

    @Test
    fun onlyOneOpenReconciliationPerAccount() = runTest {
        account()
        open()

        assertEquals(Outcome.Failed(BusinessError.ReconciliationAlreadyOpen), record(bbva, pen(81_240)))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun aResolvedOneDoesNotBlockANewCheck() = runTest {
        account()
        open(status = ReconciliationStatus.RESOLVED)

        assertTrue(record(bbva, pen(81_240)) is Outcome.Ok)
    }

    @Test
    fun unknownArchivedOrForeignCurrencyAccountsAreRejected() = runTest {
        assertEquals(Outcome.Failed(BusinessError.NotFound), record(bbva, pen(1)))

        account(archived = true)
        assertEquals(Outcome.Failed(BusinessError.AlreadyArchived), record(bbva, pen(1)))

        account()
        assertEquals(
            Outcome.Failed(BusinessError.UnsupportedCurrency),
            record(bbva, Money(1, CurrencyCode.entries.first { it != CurrencyCode.PEN })),
        )
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun cancellingNeedsAReasonAndAnOpenReconciliation() = runTest {
        open()

        assertEquals(Outcome.Failed(BusinessError.ReasonRequired), cancel(ReconciliationId("r0"), "  "))
        assertEquals(Outcome.Failed(BusinessError.NotFound), cancel(ReconciliationId("zz"), "x"))
        open("r1", ReconciliationStatus.RESOLVED)
        assertEquals(Outcome.Failed(BusinessError.ReconciliationNotOpen), cancel(ReconciliationId("r1"), "x"))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun cancellingRecordsTheTrimmedReason() = runTest {
        open()

        assertEquals(Outcome.Ok(Unit), cancel(ReconciliationId("r0"), " me equivoqué de cuenta "))

        assertEquals(ReconciliationCancelled(ReconciliationId("r0"), "me equivoqué de cuenta"), store.events.single().event)
    }
}
