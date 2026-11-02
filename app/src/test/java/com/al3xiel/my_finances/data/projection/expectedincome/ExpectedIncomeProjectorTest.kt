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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExpectedIncomeProjectorTest {
    private val dao = FakeExpectedIncomeDao()
    private val projector = ExpectedIncomeProjector(dao)
    private val sueldo = ExpectedIncomeId("e1")
    private var sequence = 0L

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private suspend fun apply(event: DomainEvent) {
        sequence++
        projector.apply(
            StoredEvent(
                sequence = sequence, eventId = "e$sequence", schemaVersion = 1, correlationId = "c",
                payload = EventCodec.encode(event), occurredOn = LocalDate.of(2026, 10, 1),
                recordedAt = Instant.ofEpochMilli(sequence), appVersion = "test", previousHash = "p", hash = "h", event = event,
            ),
        )
    }

    private fun scheduled(id: ExpectedIncomeId = sueldo) = ExpectedIncomeScheduled(
        id, IncomeSourceId("s1"), AccountId("a1"), pen(200_000), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 5), true,
    )

    @Test
    fun scheduledCreatesAPendingRow() = runTest {
        apply(scheduled())

        val row = dao.rows.getValue("e1")
        assertEquals(ExpectedIncomeStatus.SCHEDULED, row.status)
        assertEquals(200_000L, row.estimatedMinor)
        assertEquals("2026-10-28", row.expectedFrom)
        assertEquals("2026-11-05", row.expectedTo)
        assertTrue(row.repeatsMonthly)
        assertNull(row.confirmedMovementId)
    }

    @Test
    fun editChangesOnlyThePresentFields() = runTest {
        apply(scheduled())

        apply(ExpectedIncomeEdited(sueldo, null, AccountId("a2"), pen(210_000), null, LocalDate.of(2026, 11, 6), null))

        val row = dao.rows.getValue("e1")
        assertEquals("a2", row.accountId)
        assertEquals(210_000L, row.estimatedMinor)
        assertEquals("2026-11-06", row.expectedTo)
        assertEquals("s1", row.incomeSourceId)
        assertEquals("2026-10-28", row.expectedFrom)
        assertTrue(row.repeatsMonthly)
    }

    @Test
    fun confirmedKeepsTheMovementAndTheNextOne() = runTest {
        apply(scheduled())

        apply(ExpectedIncomeConfirmed(sueldo, MovementId("m1"), ExpectedIncomeId("e2")))

        val row = dao.rows.getValue("e1")
        assertEquals(ExpectedIncomeStatus.CONFIRMED, row.status)
        assertEquals("m1", row.confirmedMovementId)
        assertEquals("e2", row.nextExpectedId)
    }

    @Test
    fun cancelledKeepsTheReason() = runTest {
        apply(scheduled())

        apply(ExpectedIncomeCancelled(sueldo, "ya no cobro ahí"))

        assertEquals(ExpectedIncomeStatus.CANCELLED, dao.rows.getValue("e1").status)
        assertEquals("ya no cobro ahí", dao.rows.getValue("e1").cancelReason)
    }

    @Test
    fun eventsOfAnUnknownExpectedIncomeAreIgnored() = runTest {
        apply(ExpectedIncomeEdited(sueldo, null, null, pen(1), null, null, null))
        apply(ExpectedIncomeConfirmed(sueldo, MovementId("m1"), null))
        apply(ExpectedIncomeCancelled(sueldo, null))

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun resetClearsTheTable() = runTest {
        apply(scheduled())

        projector.reset()

        assertTrue(dao.rows.isEmpty())
    }
}
