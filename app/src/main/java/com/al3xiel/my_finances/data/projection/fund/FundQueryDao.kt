package com.al3xiel.my_finances.data.projection.fund

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class FundRow(
    @Embedded val fund: FundEntity,
    val detailed: Long,
    @ColumnInfo(name = "usage_days") val usageDays: Int,
    @ColumnInfo(name = "last_count_on") val lastCountOn: String?,
)

private const val FUND_ROW = """
    SELECT f.*,
      (SELECT COALESCE(SUM(p.amount_minor), 0) FROM movement_parts p JOIN movements m ON m.id = p.movement_id
        WHERE p.fund_id = f.id AND m.status = 'ACTIVE') AS detailed,
      (SELECT COUNT(*) FROM fund_usage_days d WHERE d.fund_id = f.id) AS usage_days,
      (SELECT MAX(c.occurred_on) FROM fund_counts c WHERE c.fund_id = f.id) AS last_count_on
    FROM funds f
"""

/** Lecturas de fondos; el gastado detallado solo cuenta partes de movimientos activos (D23). */
@Dao
interface FundQueryDao {
    @Query("$FUND_ROW WHERE f.id = :id") suspend fun fund(id: String): FundRow?

    @Query("$FUND_ROW WHERE f.id = :id") fun observeFund(id: String): Flow<FundRow?>

    @Query("$FUND_ROW ORDER BY (f.status = 'CLOSED'), f.name") fun observeFunds(): Flow<List<FundRow>>

    @Query("SELECT COALESCE(SUM(balance_minor), 0) FROM account_balances") fun observeTotalBalance(): Flow<Long>

    @Query("SELECT COALESCE(SUM(balance_minor), 0) FROM account_balances") suspend fun totalBalance(): Long

    @Query("SELECT name FROM funds WHERE status = 'ACTIVE'") suspend fun activeNames(): List<String>

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM fund_topups WHERE linked_income_movement_id = :movementId")
    suspend fun toppedUpFrom(movementId: String): Long

    @Query("SELECT COUNT(*) FROM fund_counts WHERE fund_id = :fundId") suspend fun countCount(fundId: String): Int

    @Query("SELECT COUNT(*) FROM fund_usage_days WHERE fund_id = :fundId") suspend fun usageDayCount(fundId: String): Int

    @Query(
        "SELECT COUNT(*) FROM movement_parts p JOIN movements m ON m.id = p.movement_id WHERE p.fund_id = :fundId AND m.status = 'ACTIVE'",
    )
    suspend fun assignedExpenseCount(fundId: String): Int

    @Query(
        """
        SELECT MAX(d) FROM (
            SELECT MAX(day) AS d FROM fund_usage_days WHERE fund_id = :fundId
            UNION ALL
            SELECT MAX(m.occurred_on) AS d FROM movement_parts p JOIN movements m ON m.id = p.movement_id
            WHERE p.fund_id = :fundId AND m.status = 'ACTIVE'
        )
        """,
    )
    suspend fun latestActivityOn(fundId: String): String?

    @Query("SELECT day FROM fund_usage_days WHERE fund_id = :fundId ORDER BY day") suspend fun usageDays(fundId: String): List<String>

    @Query("SELECT day FROM fund_usage_days WHERE fund_id = :fundId ORDER BY day") fun observeUsageDays(fundId: String): Flow<List<String>>

    @Query("SELECT * FROM fund_counts WHERE fund_id = :fundId ORDER BY occurred_on DESC, id DESC")
    fun observeCounts(fundId: String): Flow<List<FundCountEntity>>
}
