package com.al3xiel.my_finances.feature.expectedincome.data

import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeEntity
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeRow
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** El estado efectivo que ve la app (D27): el guardado, hoy y el estado del movimiento confirmado. */
class ExpectedIncomeRowMappingTest {
    private val to = LocalDate.of(2026, 11, 5)

    private fun row(stored: ExpectedIncomeStatus, movement: MovementStatus? = null) = ExpectedIncomeRow(
        ExpectedIncomeEntity(
            "e1", "s1", "a1", 200_000, "2026-10-28", "2026-11-05", true, stored, null,
            if (movement != null) "m1" else null, null,
        ),
        movement,
    )

    @Test
    fun scheduledTurnsOverdueAfterTheRange() {
        assertEquals(ExpectedIncomeStatus.SCHEDULED, row(ExpectedIncomeStatus.SCHEDULED).toDomain(to).status)
        assertEquals(ExpectedIncomeStatus.OVERDUE, row(ExpectedIncomeStatus.SCHEDULED).toDomain(to.plusDays(1)).status)
    }

    @Test
    fun confirmedKeepsItsMovementWhileItIsActive() {
        val domain = row(ExpectedIncomeStatus.CONFIRMED, MovementStatus.ACTIVE).toDomain(to.plusDays(10))

        assertEquals(ExpectedIncomeStatus.CONFIRMED, domain.status)
        assertEquals(MovementId("m1"), domain.confirmedMovementId)
    }

    @Test
    fun confirmedWithAVoidedMovementIsPendingAgain() {
        val domain = row(ExpectedIncomeStatus.CONFIRMED, MovementStatus.VOIDED).toDomain(to.plusDays(1))

        assertEquals(ExpectedIncomeStatus.OVERDUE, domain.status)
        assertNull(domain.confirmedMovementId)
        assertEquals(ExpectedIncomeStatus.SCHEDULED, row(ExpectedIncomeStatus.CONFIRMED, MovementStatus.VOIDED).toDomain(to.minusDays(1)).status)
    }
}
