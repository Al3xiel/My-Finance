package com.al3xiel.my_finances.data.projection.expectedincome

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import com.al3xiel.my_finances.domain.model.MovementStatus
import kotlinx.coroutines.flow.Flow

/** El esperado y el estado del movimiento que lo confirmó (si lo hubo): lo que hace falta para el estado efectivo (D27). */
data class ExpectedIncomeRow(
    @Embedded val expected: ExpectedIncomeEntity,
    @ColumnInfo(name = "confirmed_movement_status") val confirmedMovementStatus: MovementStatus?,
)

private const val ROW = """
    SELECT e.*, m.status AS confirmed_movement_status
    FROM expected_incomes e LEFT JOIN movements m ON m.id = e.confirmed_movement_id
"""

/** Pendiente: programado, o confirmado con el ingreso anulado. */
private const val PENDING = "(e.status = 'SCHEDULED' OR (e.status = 'CONFIRMED' AND m.status = 'VOIDED'))"

/** Fechas como texto ISO: ordenar y comparar como texto es ordenar y comparar por día. */
@Dao
interface ExpectedIncomeQueryDao {
    @Query("$ROW WHERE e.id = :id") suspend fun expected(id: String): ExpectedIncomeRow?

    @Query("$ROW ORDER BY e.expected_from, e.expected_to, e.id") fun observeAll(): Flow<List<ExpectedIncomeRow>>

    @Query("$ROW WHERE $PENDING AND e.account_id = :accountId AND e.income_source_id = :incomeSourceId ORDER BY e.expected_from, e.id")
    suspend fun pendingFor(accountId: String, incomeSourceId: String): List<ExpectedIncomeRow>

    @Query("$ROW WHERE $PENDING AND e.expected_to < :today ORDER BY e.expected_to, e.id")
    suspend fun overdue(today: String): List<ExpectedIncomeRow>

    @Query("$ROW WHERE $PENDING AND e.expected_to >= :today ORDER BY e.expected_from, e.id LIMIT 1")
    suspend fun next(today: String): ExpectedIncomeRow?

    @Query("$ROW WHERE e.status = 'CONFIRMED' AND e.confirmed_movement_id = :movementId AND m.status = 'ACTIVE'")
    suspend fun confirmedBy(movementId: String): ExpectedIncomeRow?
}
