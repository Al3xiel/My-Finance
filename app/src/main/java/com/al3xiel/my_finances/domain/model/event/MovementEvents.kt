package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.TagId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpensePartData(
    val partId: PartId,
    val amount: Money,
    val categoryId: CategoryId,
    val tagIds: List<TagId>,
    val poolId: PoolId?,
    val fundId: FundId?,
)

/** Comisión de una transferencia: genera un gasto aparte en la cuenta origen (D11). */
@Serializable
data class FeeData(val amount: Money, val categoryId: CategoryId)

@Serializable
data class IncomeRegistered(
    val movementId: MovementId,
    val accountId: AccountId,
    val amount: Money,
    val incomeSourceId: IncomeSourceId,
    val reconciliationId: ReconciliationId?,
    val note: String?,
) : DomainEvent {
    override val type get() = "IncomeRegistered"
    override val aggregateType get() = AggregateType.MOVEMENT
    override val aggregateId get() = movementId.value
}

@Serializable
data class ExpenseRegistered(
    val movementId: MovementId,
    val accountId: AccountId,
    val total: Money,
    val parts: List<ExpensePartData>,
    val reconciliationId: ReconciliationId?,
    val note: String?,
) : DomainEvent {
    override val type get() = "ExpenseRegistered"
    override val aggregateType get() = AggregateType.MOVEMENT
    override val aggregateId get() = movementId.value
}

@Serializable
data class TransferRegistered(
    val movementId: MovementId,
    val fromAccountId: AccountId,
    val toAccountId: AccountId,
    val amount: Money,
    val fee: FeeData?,
    val reconciliationId: ReconciliationId?,
    val note: String?,
) : DomainEvent {
    override val type get() = "TransferRegistered"
    override val aggregateType get() = AggregateType.MOVEMENT
    override val aggregateId get() = movementId.value
}

/** Estado completo del movimiento tras la corrección (no un diff); no existe para transferencias (D12). */
@Serializable
sealed interface MovementSnapshot {
    @Serializable
    @SerialName("INCOME")
    data class Income(
        val accountId: AccountId,
        val amount: Money,
        val incomeSourceId: IncomeSourceId,
        val note: String?,
    ) : MovementSnapshot

    @Serializable
    @SerialName("EXPENSE")
    data class Expense(
        val accountId: AccountId,
        val total: Money,
        val parts: List<ExpensePartData>,
        val note: String?,
    ) : MovementSnapshot
}

@Serializable
data class MovementCorrected(
    val movementId: MovementId,
    val snapshot: MovementSnapshot,
    val reason: String,
    val reconciliationId: ReconciliationId?,
) : DomainEvent {
    override val type get() = "MovementCorrected"
    override val aggregateType get() = AggregateType.MOVEMENT
    override val aggregateId get() = movementId.value
}

@Serializable
data class MovementVoided(
    val movementId: MovementId,
    val reason: String,
    val reconciliationId: ReconciliationId?,
) : DomainEvent {
    override val type get() = "MovementVoided"
    override val aggregateType get() = AggregateType.MOVEMENT
    override val aggregateId get() = movementId.value
}
