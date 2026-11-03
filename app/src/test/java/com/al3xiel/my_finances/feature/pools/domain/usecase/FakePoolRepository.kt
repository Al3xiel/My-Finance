package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.feature.pools.domain.model.PendingPartLine
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.model.ReimbursedPart
import com.al3xiel.my_finances.feature.pools.domain.model.StatementPart
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.pools.domain.rules.PendingPart

class FakePoolRepository : PoolRepository {
    val pools = linkedMapOf<PoolId, Pool>()
    val pending = linkedMapOf<PoolId, Long>()
    val reimbursed = linkedMapOf<MovementId, List<ReimbursedPart>>()
    val pendingLines = mutableListOf<Pair<PersonId, PendingPartLine>>()
    val currentPeriods = linkedMapOf<PoolId, PoolPeriod>()
    val periodParts = linkedMapOf<PoolPeriodId, List<StatementPart>>()
    val previousPending = linkedMapOf<PoolPeriodId, Long>()
    val pendingByPerson = linkedMapOf<PersonId, MutableList<Pair<PoolId, PendingPart>>>()

    override suspend fun find(id: PoolId) = pools[id]
    override fun observePools() = kotlinx.coroutines.flow.flowOf(pools.values.sortedBy { it.name })
    override suspend fun activeNames() = pools.values.filter { !it.isArchived }.map { it.name }
    override suspend fun pendingMinor(id: PoolId) = pending[id] ?: 0L
    override suspend fun reimbursedParts(movementId: MovementId) = reimbursed[movementId].orEmpty()

    override suspend fun pendingPartLines(personId: PersonId, poolId: PoolId?) =
        pendingLines.filter { it.first == personId && (poolId == null || it.second.poolId == poolId) }.map { it.second }

    override suspend fun currentPeriod(poolId: PoolId) = currentPeriods[poolId]
    override suspend fun periodParts(periodId: PoolPeriodId) = periodParts[periodId].orEmpty()
    override suspend fun pendingBefore(periodId: PoolPeriodId) = previousPending[periodId] ?: 0L

    override suspend fun pendingParts(personId: PersonId, poolId: PoolId?) =
        pendingByPerson[personId].orEmpty().filter { poolId == null || it.first == poolId }.map { it.second }
}
