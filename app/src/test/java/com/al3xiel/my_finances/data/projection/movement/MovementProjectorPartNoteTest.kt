package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** El detalle de cada parte del gasto llega a `movement_parts.note` y el replay lo reproduce igual. */
class MovementProjectorPartNoteTest {
    private val bbva = AccountId("bbva")
    private val day = LocalDate.of(2026, 10, 25)

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun part(id: String, minor: Long, note: String?) =
        ExpensePartData(PartId(id), pen(minor), CategoryId("food"), emptyList(), null, null, note)

    private val events: List<DomainEvent> = listOf(
        AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(10_000)),
        ExpenseRegistered(
            MovementId("m1"), bbva, pen(500), listOf(part("p1", 300, "Pastillas de Boby"), part("p2", 200, null)), null, null,
        ),
    )

    private class World {
        val movements = FakeMovementDao()
        val ledger = FakeLedgerDao()
        val projector = MovementProjector(movements, ledger)
        var sequence = 0L
    }

    private suspend fun World.apply(event: DomainEvent, on: LocalDate = day) {
        sequence++
        projector.apply(
            StoredEvent(
                sequence = sequence, eventId = "e$sequence", schemaVersion = 2, correlationId = "c",
                payload = EventCodec.encode(event), occurredOn = on, recordedAt = Instant.ofEpochMilli(sequence),
                appVersion = "test", previousHash = "p", hash = "h", event = event,
            ),
        )
    }

    private suspend fun World.applyAll(list: List<DomainEvent>) = list.forEach { apply(it) }

    @Test
    fun aPartDetailIsStoredWithItsPartAndAPartWithoutOneHasNone() = runTest {
        val world = World()
        world.applyAll(events)

        assertEquals("Pastillas de Boby", world.movements.parts.getValue("p1").note)
        assertNull(world.movements.parts.getValue("p2").note)
    }

    @Test
    fun aCorrectionUpdatesTheDetailOfAKeptPartAndStoresTheDetailOfANewOne() = runTest {
        val world = World()
        world.applyAll(events)
        world.apply(
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Expense(bbva, pen(500), listOf(part("p1", 300, "Pastillas de Boby (grandes)"), part("p3", 200, "Paseo")), null),
                "detalle", null,
            ),
        )

        assertEquals(setOf("p1", "p3"), world.movements.parts.keys)
        assertEquals("Pastillas de Boby (grandes)", world.movements.parts.getValue("p1").note)
        assertEquals("Paseo", world.movements.parts.getValue("p3").note)
    }

    @Test
    fun aCorrectionThatClearsTheDetailLeavesNone() = runTest {
        val world = World()
        world.applyAll(events)
        world.apply(
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Expense(bbva, pen(500), listOf(part("p1", 300, null), part("p2", 200, null)), null),
                "sin detalle", null,
            ),
        )

        assertNull(world.movements.parts.getValue("p1").note)
    }

    @Test
    fun replayingTheSameEventsGivesTheSameParts() = runTest {
        val first = World().also { it.applyAll(events) }
        val second = World().also { it.applyAll(events) }

        assertEquals(first.movements.parts, second.movements.parts)
        assertEquals(listOf("Pastillas de Boby", null), second.movements.parts.values.map { it.note })
    }

    @Test
    fun resetAndReplayRebuildsTheDetail() = runTest {
        val world = World()
        world.applyAll(events)
        val before = world.movements.parts.toMap()

        world.projector.reset()
        world.sequence = 0
        world.applyAll(events)

        assertEquals(before, world.movements.parts)
    }
}
