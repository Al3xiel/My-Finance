package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.data.projection.movement.MovementPartEntity

/** DAO en memoria. Comparte `parts` con el test para simular lo que dejó el proyector de movimientos. */
class FakePoolDao(val parts: MutableMap<String, MovementPartEntity> = linkedMapOf()) : PoolDao {
    val pools = linkedMapOf<String, PoolEntity>()
    val periods = linkedMapOf<String, PoolPeriodEntity>()
    val statements = linkedMapOf<String, SettlementStatementEntity>()

    override suspend fun upsertPool(pool: PoolEntity) { pools[pool.id] = pool }
    override suspend fun pool(id: String) = pools[id]

    override suspend fun upsertPeriod(period: PoolPeriodEntity) { periods[period.id] = period }
    override suspend fun period(id: String) = periods[id]
    override suspend fun allPeriods() = periods.values.toList()
    override suspend fun periodCount(poolId: String) = periods.values.count { it.poolId == poolId }
    override suspend fun openPeriodOf(poolId: String) =
        periods.values.filter { it.poolId == poolId && it.closedOn == null }.withIndex()
            .maxWithOrNull(compareBy({ it.value.openedOn }, { it.index }))?.value

    override suspend fun insertStatement(statement: SettlementStatementEntity) { statements[statement.id] = statement }

    override suspend fun periodOfPart(partId: String) = parts[partId]?.poolPeriodId
    override suspend fun assignPeriod(partId: String, periodId: String?) {
        parts[partId]?.let { parts[partId] = it.copy(poolPeriodId = periodId) }
    }
    override suspend fun clearPeriodsOf(movementId: String) {
        parts.replaceAll { _, p -> if (p.movementId == movementId) p.copy(poolPeriodId = null) else p }
    }

    override suspend fun periodSums() = parts.values.filter { it.poolPeriodId != null }.groupBy { it.poolPeriodId!! }
        .map { (id, rows) -> PeriodSum(id, rows.sumOf { it.amountMinor }, rows.sumOf { it.reimbursedMinor }) }

    override suspend fun clearAssignedPeriods() { parts.replaceAll { _, p -> p.copy(poolPeriodId = null) } }
    override suspend fun clearPools() = pools.clear()
    override suspend fun clearPeriods() = periods.clear()
    override suspend fun clearStatements() = statements.clear()
}
