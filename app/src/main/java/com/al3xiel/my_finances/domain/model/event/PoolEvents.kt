package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.ReimbursementId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.TagId
import kotlinx.serialization.Serializable

enum class PoolKind { PERMANENT, EVENT }

enum class StatementFormat { IMAGE, PDF }

enum class AllocationStrategy { OLDEST_FIRST, MANUAL }

/** Cuánto del reembolso salda una parte; calculado en el caso de uso y guardado en el evento. */
@Serializable
data class AllocationData(val partId: PartId, val amount: Money)

@Serializable
data class ReimbursementPoolCreated(
    val poolId: PoolId,
    val name: String,
    val responsiblePersonId: PersonId,
    val kind: PoolKind,
    val defaultCategoryId: CategoryId?,
    val defaultTagIds: List<TagId>,
) : DomainEvent {
    override val type get() = "ReimbursementPoolCreated"
    override val aggregateType get() = AggregateType.POOL
    override val aggregateId get() = poolId.value
}

@Serializable
data class ReimbursementPoolRenamed(val poolId: PoolId, val name: String) : DomainEvent {
    override val type get() = "ReimbursementPoolRenamed"
    override val aggregateType get() = AggregateType.POOL
    override val aggregateId get() = poolId.value
}

@Serializable
data class ReimbursementPoolArchived(val poolId: PoolId) : DomainEvent {
    override val type get() = "ReimbursementPoolArchived"
    override val aggregateType get() = AggregateType.POOL
    override val aggregateId get() = poolId.value
}

@Serializable
data class SettlementStatementGenerated(
    val statementId: StatementId,
    val poolId: PoolId,
    val periodId: PoolPeriodId,
    val partIds: List<PartId>,
    val pendingTotal: Money,
    val format: StatementFormat,
) : DomainEvent {
    override val type get() = "SettlementStatementGenerated"
    override val aggregateType get() = AggregateType.POOL
    override val aggregateId get() = poolId.value
}

@Serializable
data class ReimbursementReceived(
    val reimbursementId: ReimbursementId,
    val movementId: MovementId,
    val personId: PersonId,
    val accountId: AccountId,
    val amount: Money,
    val strategy: AllocationStrategy,
    val allocations: List<AllocationData>,
    val note: String?,
) : DomainEvent {
    override val type get() = "ReimbursementReceived"
    override val aggregateType get() = AggregateType.MOVEMENT
    override val aggregateId get() = movementId.value
}
