package com.al3xiel.my_finances.data.projection.pool

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

/** Lo que suman las partes de un periodo; el proyector lo usa para fijar totales y estado. */
data class PeriodSum(
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    @ColumnInfo(name = "reimbursed_minor") val reimbursedMinor: Long,
)

/** Escritura para el proyector de cúmulos; las consultas de pantalla vienen en T15. */
@Dao
interface PoolDao {
    @Upsert suspend fun upsertPool(pool: PoolEntity)

    @Query("SELECT * FROM pools WHERE id = :id") suspend fun pool(id: String): PoolEntity?

    @Upsert suspend fun upsertPeriod(period: PoolPeriodEntity)

    @Query("SELECT * FROM pool_periods WHERE id = :id") suspend fun period(id: String): PoolPeriodEntity?

    @Query("SELECT * FROM pool_periods") suspend fun allPeriods(): List<PoolPeriodEntity>

    @Query("SELECT COUNT(*) FROM pool_periods WHERE pool_id = :poolId") suspend fun periodCount(poolId: String): Int

    @Query("SELECT * FROM pool_periods WHERE pool_id = :poolId AND closed_on IS NULL ORDER BY opened_on DESC, rowid DESC LIMIT 1")
    suspend fun openPeriodOf(poolId: String): PoolPeriodEntity?

    @Insert suspend fun insertStatement(statement: SettlementStatementEntity)

    @Query("SELECT pool_period_id FROM movement_parts WHERE id = :partId") suspend fun periodOfPart(partId: String): String?

    @Query("UPDATE movement_parts SET pool_period_id = :periodId WHERE id = :partId")
    suspend fun assignPeriod(partId: String, periodId: String?)

    @Query("UPDATE movement_parts SET pool_period_id = NULL WHERE movement_id = :movementId")
    suspend fun clearPeriodsOf(movementId: String)

    @Query(
        "SELECT pool_period_id AS period_id, SUM(amount_minor) AS total_minor, SUM(reimbursed_minor) AS reimbursed_minor " +
            "FROM movement_parts WHERE pool_period_id IS NOT NULL GROUP BY pool_period_id",
    )
    suspend fun periodSums(): List<PeriodSum>

    @Query("UPDATE movement_parts SET pool_period_id = NULL") suspend fun clearAssignedPeriods()
    @Query("DELETE FROM pools") suspend fun clearPools()
    @Query("DELETE FROM pool_periods") suspend fun clearPeriods()
    @Query("DELETE FROM settlement_statements") suspend fun clearStatements()
}
