package com.al3xiel.my_finances.data.projection.expectedincome

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeConfirmed
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeEdited
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Mismos eventos, mismas tablas: el proyector se explica solo con eventos y su propia tabla. */
class ExpectedIncomeReplayTest {
    private val dao = FakeExpectedIncomeDao()
    private val projector = ExpectedIncomeProjector(dao)

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    private val script: List<DomainEvent> = listOf(
        ExpectedIncomeScheduled(ExpectedIncomeId("e1"), IncomeSourceId("s1"), AccountId("a1"), pen(200_000), d(10, 28), d(11, 5), true),
        ExpectedIncomeEdited(ExpectedIncomeId("e1"), null, null, pen(210_000), null, null, null),
        ExpectedIncomeScheduled(ExpectedIncomeId("e2"), IncomeSourceId("s1"), AccountId("a1"), pen(210_000), d(11, 28), d(12, 5), true),
        ExpectedIncomeConfirmed(ExpectedIncomeId("e1"), MovementId("m1"), ExpectedIncomeId("e2")),
        ExpectedIncomeCancelled(ExpectedIncomeId("e2"), "se anuló el ingreso"),
    )

    private fun stored(i: Int, event: DomainEvent) = StoredEvent(
        sequence = i + 1L, eventId = "e$i", schemaVersion = 1, correlationId = "c", payload = EventCodec.encode(event),
        occurredOn = d(11, 1), recordedAt = Instant.ofEpochMilli(i + 1L), appVersion = "test", previousHash = "p", hash = "h", event = event,
    )

    @Test
    fun replayingTheSameEventsLeavesTheSameTable() = runTest {
        val events = script.mapIndexed { i, e -> stored(i, e) }
        events.forEach { projector.apply(it) }
        val live = dao.rows.toMap()

        projector.reset()
        events.forEach { projector.apply(it) }

        assertEquals(live, dao.rows.toMap())
    }

    @Test
    fun theStoryEndsWithTheDocumentedFigures() = runTest {
        script.forEachIndexed { i, e -> projector.apply(stored(i, e)) }

        val first = dao.rows.getValue("e1")
        assertEquals(ExpectedIncomeStatus.CONFIRMED, first.status)
        assertEquals(210_000L, first.estimatedMinor)
        assertEquals("e2", first.nextExpectedId)
        assertEquals(ExpectedIncomeStatus.CANCELLED, dao.rows.getValue("e2").status)
    }
}
