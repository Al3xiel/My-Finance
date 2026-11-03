package com.al3xiel.my_finances.feature.pools.data

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.pool.PoolEntity
import com.al3xiel.my_finances.data.projection.pool.PoolPeriodEntity
import com.al3xiel.my_finances.data.projection.pool.PoolQueryDao
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.pools.domain.model.PendingPartLine
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.model.ReimbursedPart
import com.al3xiel.my_finances.feature.pools.domain.model.StatementPart
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.pools.domain.rules.PendingPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

class PoolRepositoryImpl @Inject constructor(
    private val queries: PoolQueryDao,
) : PoolRepository {
    override suspend fun find(id: PoolId): Pool? = queries.pool(id.value)?.toDomain()

    override suspend fun activeNames(): List<String> = queries.activeNames()

    override fun observePools(): Flow<List<Pool>> = queries.observePools().map { rows -> rows.map { it.toDomain() } }

    override suspend fun pendingMinor(id: PoolId): Long = queries.pendingMinor(id.value)

    override suspend fun reimbursedParts(movementId: MovementId): List<ReimbursedPart> =
        queries.reimbursedParts(movementId.value).map { ReimbursedPart(PartId(it.partId), PoolId(it.poolId), pen(it.reimbursed)) }

    override suspend fun pendingParts(personId: PersonId, poolId: PoolId?): List<PendingPart> =
        queries.pendingParts(personId.value, poolId?.value).map {
            PendingPart(PartId(it.partId), LocalDate.parse(it.occurredOn), it.createdSequence, pen(it.pending))
        }

    override suspend fun pendingPartLines(personId: PersonId, poolId: PoolId?): List<PendingPartLine> {
        val tags = queries.pendingPartTags(personId.value, poolId?.value).groupBy({ it.partId }, { it.tagName })
        return queries.pendingPartLines(personId.value, poolId?.value).map {
            PendingPartLine(
                PartId(it.partId), PoolId(it.poolId), it.poolName, LocalDate.parse(it.occurredOn), it.createdSequence,
                it.categoryName, tags[it.partId].orEmpty(), pen(it.pending),
            )
        }
    }

    override suspend fun currentPeriod(poolId: PoolId): PoolPeriod? = queries.currentPeriod(poolId.value)?.toDomain()

    override suspend fun periodParts(periodId: PoolPeriodId): List<StatementPart> {
        val tags = queries.periodPartTags(periodId.value).groupBy({ it.partId }, { it.tagName })
        return queries.periodParts(periodId.value).map {
            StatementPart(
                PartId(it.partId), LocalDate.parse(it.occurredOn), it.createdSequence, it.categoryName,
                tags[it.partId].orEmpty(), pen(it.amount), pen(it.pending),
            )
        }
    }

    override suspend fun pendingBefore(periodId: PoolPeriodId): Long = queries.pendingBefore(periodId.value)
}

internal fun PoolEntity.toDomain() = Pool(
    id = PoolId(id),
    name = name,
    responsiblePersonId = PersonId(responsiblePersonId),
    kind = kind,
    defaultCategoryId = defaultCategoryId?.let(::CategoryId),
    defaultTagIds = defaultTagIds.split(',').filter { it.isNotEmpty() }.map(::TagId),
    isArchived = isArchived,
)

internal fun PoolPeriodEntity.toDomain() = PoolPeriod(
    id = PoolPeriodId(id),
    poolId = PoolId(poolId),
    openedOn = LocalDate.parse(openedOn),
    closedOn = closedOn?.let(LocalDate::parse),
    status = status,
    statementId = statementId?.let(::StatementId),
    total = pen(totalMinor),
    reimbursed = pen(reimbursedMinor),
)
