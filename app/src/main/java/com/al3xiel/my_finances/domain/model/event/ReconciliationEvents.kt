package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import kotlinx.serialization.Serializable

enum class ReconciliationCause { BALANCED, MISSING_RECORD, RECORDING_ERROR, SUSPECTED_APP_ERROR, UNKNOWN }

enum class AdjustmentDirection { UNIDENTIFIED_INCOME, UNIDENTIFIED_EXPENSE }

/** El ajuste no identificado de una resolución: `movementId` es el del movimiento `ADJUSTMENT` que se crea. */
@Serializable
data class AdjustmentData(
    val movementId: MovementId,
    val direction: AdjustmentDirection,
    val amount: Money,
)

/** Razones fijas de `ProjectionsRebuilt.reason`; solo `VERSION_CHANGE` y `MANUAL` re-verifican conciliaciones. */
object RebuildReason {
    const val VERSION_CHANGE = "VERSION_CHANGE"
    const val MANUAL = "MANUAL"
    const val RESTORE = "RESTORE"
    val ALL = setOf(VERSION_CHANGE, MANUAL, RESTORE)
}

/** `checkedAt` no viaja en el payload: es el `recorded_at` del evento y nadie lo edita. */
@Serializable
data class BalanceCheckRecorded(
    val reconciliationId: ReconciliationId,
    val accountId: AccountId,
    val realBalance: Money,
    val computedBalanceSnapshot: Money,
) : DomainEvent {
    override val type get() = "BalanceCheckRecorded"
    override val aggregateType get() = AggregateType.RECONCILIATION
    override val aggregateId get() = reconciliationId.value
}

@Serializable
data class MovementLinkedToReconciliation(
    val reconciliationId: ReconciliationId,
    val movementId: MovementId,
) : DomainEvent {
    override val type get() = "MovementLinkedToReconciliation"
    override val aggregateType get() = AggregateType.RECONCILIATION
    override val aggregateId get() = reconciliationId.value
}

@Serializable
data class ReconciliationResolved(
    val reconciliationId: ReconciliationId,
    val cause: ReconciliationCause,
    val adjustment: AdjustmentData?,
    val note: String?,
) : DomainEvent {
    override val type get() = "ReconciliationResolved"
    override val aggregateType get() = AggregateType.RECONCILIATION
    override val aggregateId get() = reconciliationId.value
}

@Serializable
data class ReconciliationCancelled(
    val reconciliationId: ReconciliationId,
    val reason: String,
) : DomainEvent {
    override val type get() = "ReconciliationCancelled"
    override val aggregateType get() = AggregateType.RECONCILIATION
    override val aggregateId get() = reconciliationId.value
}
