package com.al3xiel.my_finances.data.projection.reconciliation

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AdjustmentData
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementLinkedToReconciliation
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import com.al3xiel.my_finances.domain.model.event.RebuildReason
import com.al3xiel.my_finances.domain.model.event.ReconciliationCancelled
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.model.event.ReconciliationResolved
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReconciliationProjectorTest {
    private val movementDao = FakeMovementDao()
    private val ledgerDao = FakeLedgerDao()
    private val dao = FakeReconciliationDao(movementDao, ledgerDao)
    private val movementProjector = MovementProjector(movementDao, ledgerDao)
    private val projector = ReconciliationProjector(dao, LedgerPoster(movementDao, ledgerDao))
    private val projectors: List<EventProjector> = listOf(movementProjector, projector)

    private val bbva = AccountId("bbva")
    private val r1 = ReconciliationId("r1")
    private var sequence = 0L

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private suspend fun apply(event: DomainEvent, on: LocalDate = LocalDate.of(2026, 10, 25)) {
        sequence++
        val stored = StoredEvent(
            sequence, "e$sequence", 1, "c", EventCodec.encode(event), on, Instant.ofEpochSecond(1_000L + sequence), "test", "p", "h", event,
        )
        projectors.forEach { it.apply(stored) }
    }

    private fun open() = AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(80_000))
    private fun check(real: Long = 81_240, snapshot: Long = 80_000) = BalanceCheckRecorded(r1, bbva, pen(real), pen(snapshot))
    private fun income(id: String, minor: Long, reconciliation: ReconciliationId? = null) =
        IncomeRegistered(MovementId(id), bbva, pen(minor), IncomeSourceId("s"), reconciliation, null)

    private fun adjustment(direction: AdjustmentDirection, minor: Long) = AdjustmentData(MovementId("adj"), direction, pen(minor))

    private suspend fun diff() =
        dao.reconciliation("r1")!!.let { it.realMinor - dao.computedMinor("r1", it.accountId, it.checkedSequence) }

    private fun rebuilt(reason: String) = ProjectionsRebuilt(mapOf("movement" to 4), reason)

    @Test
    fun recordingOpensAReconciliationWithoutMovingBalances() = runTest {
        apply(open())
        apply(check())

        val row = dao.reconciliation("r1")!!
        assertEquals(ReconciliationStatus.OPEN, row.status)
        assertEquals(81_240L, row.realMinor)
        assertEquals(80_000L, row.snapshotComputedMinor)
        assertEquals(2L, row.checkedSequence)
        assertFalse(row.needsReview)
        assertEquals(80_000L, ledgerDao.balanceOf("bbva"))
    }

    @Test
    fun explicitLinkAndRegistrationWithTheIdBothLinkAndTheDifferenceFollowsLive() = runTest {
        apply(open())
        apply(check())
        apply(income("m1", 1_000, r1))
        assertEquals(setOf("m1"), dao.links.map { it.movementId }.toSet())
        assertEquals(240L, diff())

        apply(income("m2", 100))
        assertEquals(240L, diff())
        apply(MovementLinkedToReconciliation(r1, MovementId("m2")))
        assertEquals(setOf("m1", "m2"), dao.links.map { it.movementId }.toSet())
        assertEquals(140L, diff())
    }

    @Test
    fun linkedCorrectionReplacesTheOldEntryAndANonLinkedVoidDoesNotMoveTheDifference() = runTest {
        apply(open())
        apply(income("m0", 300))
        apply(check(real = 81_000, snapshot = 80_300))
        apply(income("m1", 500, r1))
        apply(MovementCorrected(MovementId("m1"), MovementSnapshot.Income(bbva, pen(700), IncomeSourceId("s"), null), "monto", r1))
        assertEquals(81_000L - (80_300L + 700L), diff())

        apply(MovementVoided(MovementId("m0"), "duplicado", null))
        assertEquals(81_000L - (80_300L + 700L), diff())
    }

    @Test
    fun resolvingWithAnAdjustmentPostsTheUnidentifiedIncomeAtTheResolutionDate() = runTest {
        apply(open())
        apply(check())
        apply(
            ReconciliationResolved(r1, ReconciliationCause.UNKNOWN, adjustment(AdjustmentDirection.UNIDENTIFIED_INCOME, 1_240), "nota"),
            on = LocalDate.of(2026, 11, 2),
        )

        val row = dao.reconciliation("r1")!!
        assertEquals(ReconciliationStatus.RESOLVED, row.status)
        assertEquals(ReconciliationCause.UNKNOWN, row.cause)
        assertEquals("adj", row.adjustmentMovementId)
        assertEquals(1_240L, row.adjustmentMinor)
        val movement = movementDao.movements.getValue("adj")
        assertEquals(MovementType.ADJUSTMENT, movement.type)
        assertEquals("2026-11-02", movement.occurredOn)
        val unknownSource = SystemCatalog.incomeSources.first { it.kind == IncomeSourceKind.UNKNOWN_ADJUSTMENT }.id.value
        assertEquals(unknownSource, movement.incomeSourceId)
        assertNull(movement.note)
        assertEquals(LedgerEntryKind.ADJUSTMENT, ledgerDao.entries.values.last().entryKind)
        assertEquals(81_240L, ledgerDao.balanceOf("bbva"))
    }

    @Test
    fun unidentifiedExpenseTakesMoneyOutAndIsStoredWithNegativeSign() = runTest {
        apply(open())
        apply(check(real = 79_000))
        apply(ReconciliationResolved(r1, ReconciliationCause.SUSPECTED_APP_ERROR, adjustment(AdjustmentDirection.UNIDENTIFIED_EXPENSE, 1_000), null))

        assertEquals(-1_000L, dao.reconciliation("r1")!!.adjustmentMinor)
        assertEquals(79_000L, ledgerDao.balanceOf("bbva"))
    }

    @Test
    fun resolvingWithoutAdjustmentCreatesNoMovement() = runTest {
        apply(open())
        apply(check(real = 80_000))
        apply(ReconciliationResolved(r1, ReconciliationCause.BALANCED, null, null))

        assertEquals(ReconciliationStatus.RESOLVED, dao.reconciliation("r1")!!.status)
        assertTrue(movementDao.movements.isEmpty())
        assertEquals(80_000L, ledgerDao.balanceOf("bbva"))
    }

    @Test
    fun cancellingKeepsTheReasonAndLeavesNoAdjustment() = runTest {
        apply(open())
        apply(check())
        apply(ReconciliationCancelled(r1, "me equivoqué"))

        val row = dao.reconciliation("r1")!!
        assertEquals(ReconciliationStatus.CANCELLED, row.status)
        assertEquals("me equivoqué", row.cancelReason)
        assertTrue(movementDao.movements.isEmpty())
    }

    private suspend fun resolvedBalanced() {
        apply(open())
        apply(check(real = 80_000))
        apply(ReconciliationResolved(r1, ReconciliationCause.BALANCED, null, null))
    }

    private fun shiftOpening(minor: Long) =
        ledgerDao.entries.replaceAll { _, e -> if (e.entryKind == LedgerEntryKind.OPENING) e.copy(deltaMinor = e.deltaMinor - minor) else e }

    @Test
    fun versionChangeRebuildMarksABalancedOneThatNoLongerAddsUp() = runTest {
        resolvedBalanced()
        shiftOpening(500)

        apply(rebuilt(RebuildReason.VERSION_CHANGE))

        assertTrue(dao.reconciliation("r1")!!.needsReview)
    }

    @Test
    fun manualRebuildAlsoMarksButRestoreAndOrdinaryEventsDoNot() = runTest {
        resolvedBalanced()
        shiftOpening(500)

        apply(rebuilt(RebuildReason.RESTORE))
        apply(income("m9", 10))
        assertFalse(dao.reconciliation("r1")!!.needsReview)

        apply(rebuilt(RebuildReason.MANUAL))
        assertTrue(dao.reconciliation("r1")!!.needsReview)
    }

    @Test
    fun anAdjustmentThatStillMatchesIsNotMarkedButOneThatNoLongerMatchesIs() = runTest {
        apply(open())
        apply(check())
        apply(ReconciliationResolved(r1, ReconciliationCause.UNKNOWN, adjustment(AdjustmentDirection.UNIDENTIFIED_INCOME, 1_240), null))

        apply(rebuilt(RebuildReason.VERSION_CHANGE))
        assertFalse(dao.reconciliation("r1")!!.needsReview)

        shiftOpening(240)
        apply(rebuilt(RebuildReason.VERSION_CHANGE))
        assertTrue(dao.reconciliation("r1")!!.needsReview)
    }

    @Test
    fun voidingTheAdjustmentRestoresTheBalanceKeepsItResolvedAndClearsTheMark() = runTest {
        apply(open())
        apply(check())
        apply(ReconciliationResolved(r1, ReconciliationCause.UNKNOWN, adjustment(AdjustmentDirection.UNIDENTIFIED_INCOME, 1_240), null))
        shiftOpening(240)
        apply(rebuilt(RebuildReason.VERSION_CHANGE))
        assertTrue(dao.reconciliation("r1")!!.needsReview)

        apply(MovementVoided(MovementId("adj"), "ya no corresponde", null))

        val row = dao.reconciliation("r1")!!
        assertEquals(ReconciliationStatus.RESOLVED, row.status)
        assertFalse(row.needsReview)
        assertEquals(MovementStatus.VOIDED, movementDao.movements.getValue("adj").status)
        assertEquals(79_760L, ledgerDao.balanceOf("bbva"))

        apply(rebuilt(RebuildReason.VERSION_CHANGE))
        assertFalse(dao.reconciliation("r1")!!.needsReview)
    }

    @Test
    fun voidingAnythingElseLeavesReconciliationsAlone() = runTest {
        resolvedBalanced()
        apply(income("m1", 100))
        apply(MovementVoided(MovementId("m1"), "x", null))

        assertEquals(ReconciliationStatus.RESOLVED, dao.reconciliation("r1")!!.status)
    }

    @Test
    fun theBankCanSayZeroAndTheRowKeepsIt() = runTest {
        apply(open())
        apply(check(real = 0))

        val row = dao.reconciliation("r1")!!
        assertEquals(0L, row.realMinor)
        assertEquals(-80_000L, diff())
    }

    @Test
    fun aNegativeRealBalanceResolvesWithAnUnidentifiedExpenseAndIsReverifiedByItsSign() = runTest {
        apply(open())
        apply(check(real = -500))
        apply(ReconciliationResolved(r1, ReconciliationCause.SUSPECTED_APP_ERROR, adjustment(AdjustmentDirection.UNIDENTIFIED_EXPENSE, 80_500), null))
        assertEquals(-500L, ledgerDao.balanceOf("bbva"))
        assertEquals(-80_500L, dao.reconciliation("r1")!!.adjustmentMinor)

        apply(rebuilt(RebuildReason.VERSION_CHANGE))
        assertFalse("el egreso sigue coincidiendo con la diferencia", dao.reconciliation("r1")!!.needsReview)

        shiftOpening(100)
        apply(rebuilt(RebuildReason.VERSION_CHANGE))
        assertTrue("la diferencia cambió y el egreso ya no coincide", dao.reconciliation("r1")!!.needsReview)
    }
}
