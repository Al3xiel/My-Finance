package com.al3xiel.my_finances.data.projection.fund

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.FundClosed
import com.al3xiel.my_finances.domain.model.event.FundCounted
import com.al3xiel.my_finances.domain.model.event.FundCreated
import com.al3xiel.my_finances.domain.model.event.FundEdited
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.UntrackedSpendData
import com.al3xiel.my_finances.domain.model.event.UsageDayMarked
import com.al3xiel.my_finances.domain.model.event.UsageDayUnmarked
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Mismos eventos, mismas tablas: el proyector se explica solo con eventos y sus propias tablas (P12). */
class FundReplayTest {
    private val dao = FakeFundDao()
    private val projector = FundProjector(dao)

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private val pasajes = FundId("pasajes")
    private val comida = FundId("comida")
    private val oct = { day: Int -> LocalDate.of(2026, 10, day) }

    private val script: List<Pair<DomainEvent, LocalDate>> = listOf(
        FundCreated(pasajes, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(31)) to oct(1),
        FundCreated(comida, "Comida", ConsumptionMode.DETAILED, null, oct(1), null) to oct(1),
        FundToppedUp(pasajes, pen(5_000), MovementId("income1")) to oct(1),
        FundToppedUp(comida, pen(2_000), null) to oct(2),
        UsageDayMarked(pasajes, oct(3)) to oct(3),
        UsageDayMarked(pasajes, oct(4)) to oct(4),
        UsageDayUnmarked(pasajes, oct(3)) to oct(5),
        FundEdited(pasajes, "Pasajes oct", ConsumptionMode.DAILY_ESTIMATE, pen(600), oct(1), oct(31)) to oct(5),
        FundCounted(pasajes, pen(4_000), UntrackedSpendData(MovementId("m9"), AccountId("cash"), CategoryId("transport"), pen(300))) to oct(6),
        FundClosed(pasajes, RemainderPolicy.ROLL_OVER, comida, pen(3_700)) to oct(31),
    )

    private fun stored(i: Int, event: DomainEvent, on: LocalDate) = StoredEvent(
        sequence = i + 1L, eventId = "e$i", schemaVersion = 1, correlationId = "c", payload = EventCodec.encode(event),
        occurredOn = on, recordedAt = Instant.ofEpochMilli(i + 1L), appVersion = "test", previousHash = "p", hash = "h", event = event,
    )

    private fun snapshot() = listOf(dao.funds.toMap(), dao.usageDays.toSet(), dao.counts.toMap(), dao.topUps.toMap())

    @Test
    fun replayingTheSameEventsLeavesTheSameTables() = runTest {
        val events = script.mapIndexed { i, (event, on) -> stored(i, event, on) }
        events.forEach { projector.apply(it) }
        val live = snapshot()

        projector.reset()
        events.forEach { projector.apply(it) }

        assertEquals(live, snapshot())
    }

    @Test
    fun theStoryEndsWithTheDocumentedFigures() = runTest {
        script.forEachIndexed { i, (event, on) -> projector.apply(stored(i, event, on)) }

        val pasajesRow = dao.funds.getValue("pasajes")
        assertEquals(5_000L, pasajesRow.allocatedMinor)
        assertEquals(300L, pasajesRow.untrackedSpentMinor)
        assertEquals(listOf("2026-10-04"), dao.usageDays.map { it.day })
        // Cierre con ROLL_OVER: el destino sube lo que dijo el evento, sin mirar partes de gasto.
        assertEquals(5_700L, dao.funds.getValue("comida").allocatedMinor)
        assertEquals(1_000L, dao.counts.values.single().realSpentMinor)
        assertEquals(600L, dao.counts.values.single().estimatedSpentMinor)
        assertEquals(400L, dao.counts.values.single().deviationMinor)
    }
}
