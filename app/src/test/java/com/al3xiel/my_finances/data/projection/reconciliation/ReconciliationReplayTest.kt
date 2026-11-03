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
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AdjustmentData
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import com.al3xiel.my_finances.domain.model.event.RebuildReason
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.model.event.ReconciliationResolved
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Mismos eventos, misma proyección: el replay (reset + todo el registro, proyector por proyector) iguala lo vivo. */
class ReconciliationReplayTest {
    private val movementDao = FakeMovementDao()
    private val ledgerDao = FakeLedgerDao()
    private val dao = FakeReconciliationDao(movementDao, ledgerDao)
    private val projectors: List<EventProjector> =
        listOf(MovementProjector(movementDao, ledgerDao), ReconciliationProjector(dao, LedgerPoster(movementDao, ledgerDao)))

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private val events: List<Pair<DomainEvent, LocalDate>> = listOf(
        AccountCreated(AccountId("bbva"), "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(80_000)) to LocalDate.of(2026, 10, 1),
        BalanceCheckRecorded(ReconciliationId("r1"), AccountId("bbva"), pen(81_240), pen(80_000)) to LocalDate.of(2026, 10, 25),
        IncomeRegistered(MovementId("m1"), AccountId("bbva"), pen(1_000), IncomeSourceId("s"), ReconciliationId("r1"), null) to LocalDate.of(2026, 10, 25),
        ReconciliationResolved(
            ReconciliationId("r1"),
            ReconciliationCause.UNKNOWN,
            AdjustmentData(MovementId("adj"), AdjustmentDirection.UNIDENTIFIED_INCOME, pen(240)),
            null,
        ) to LocalDate.of(2026, 10, 26),
        MovementVoided(MovementId("adj"), "x", null) to LocalDate.of(2026, 10, 27),
        ProjectionsRebuilt(mapOf("movement" to 4), RebuildReason.VERSION_CHANGE) to LocalDate.of(2026, 10, 28),
    )

    private fun stored(list: List<Pair<DomainEvent, LocalDate>>) = list.mapIndexed { i, (event, on) ->
        val sequence = i + 1L
        StoredEvent(sequence, "e$sequence", 1, "c", EventCodec.encode(event), on, Instant.ofEpochSecond(sequence), "t", "p", "h", event)
    }

    private fun snapshot() = listOf(
        dao.reconciliations.toMap(), dao.links.toSet(), ledgerDao.entries.toMap(), movementDao.movements.toMap(), ledgerDao.balances.toMap(),
    )

    private fun assertReplayEqualsLive(list: List<Pair<DomainEvent, LocalDate>>) = runTest {
        val log = stored(list)
        log.forEach { e -> projectors.forEach { it.apply(e) } }
        val live = snapshot()

        projectors.forEach { p ->
            p.reset()
            log.forEach { p.apply(it) }
        }

        assertEquals(live, snapshot())
    }

    @Test
    fun replayYieldsTheSameRowsAsTheLiveProjection() = assertReplayEqualsLive(events)

    /**
     * Una BALANCED cuyo vínculo se anula (sin vincular) antes de la re-verificación: la marca sale activa y es la misma
     * en vivo y tras el replay. Es el camino que el escenario anterior no ejercita (termina con el ajuste ya anulado).
     */
    @Test
    fun aMarkedReviewIsTheSameLiveAndAfterReplay() {
        val r1 = ReconciliationId("r1")
        val marked = listOf<Pair<DomainEvent, LocalDate>>(
            AccountCreated(AccountId("bbva"), "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(80_000)) to LocalDate.of(2026, 10, 1),
            BalanceCheckRecorded(r1, AccountId("bbva"), pen(81_000), pen(80_000)) to LocalDate.of(2026, 10, 25),
            IncomeRegistered(MovementId("m1"), AccountId("bbva"), pen(1_000), IncomeSourceId("s"), r1, null) to LocalDate.of(2026, 10, 25),
            ReconciliationResolved(r1, ReconciliationCause.BALANCED, null, null) to LocalDate.of(2026, 10, 26),
            MovementVoided(MovementId("m1"), "duplicado", null) to LocalDate.of(2026, 10, 27),
            ProjectionsRebuilt(mapOf("movement" to 4), RebuildReason.VERSION_CHANGE) to LocalDate.of(2026, 10, 28),
        )

        assertReplayEqualsLive(marked)

        assertEquals(true, dao.reconciliations.getValue("r1").needsReview)
    }
}
