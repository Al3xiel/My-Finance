package com.al3xiel.my_finances.data.projection.reconciliation

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.movement.LedgerEntryEntity
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementEntity
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.AdjustmentData
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementLinkedToReconciliation
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import com.al3xiel.my_finances.domain.model.event.RebuildReason
import com.al3xiel.my_finances.domain.model.event.ReconciliationCancelled
import com.al3xiel.my_finances.domain.model.event.ReconciliationResolved
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationRules
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReviewVerdict
import javax.inject.Inject

/**
 * Conciliaciones y sus vínculos. Corre después de movimientos (`order`): lee sus asientos y es dueño del movimiento
 * `ADJUSTMENT` (lo crea al resolver con ajuste y lo anula si llega su `MovementVoided`), como lo hacen reembolsos y deudas.
 *
 * Re-verifica las resueltas solo al aplicar `ProjectionsRebuilt` por cambio de versión o manual (D31): para entonces sus
 * propias tablas ya vienen de los eventos, así que el chequeo y el ajuste guardados son la evidencia. La marca refleja el
 * libro al aplicar el último disparador (docs/04 §5.4); un replay la recalcula con el estado final.
 */
class ReconciliationProjector @Inject constructor(
    private val reconciliations: ReconciliationDao,
    private val poster: LedgerPoster,
) : EventProjector {
    override val name = "reconciliation"
    override val version = 1
    override val order = 30

    private val adjustmentSourceId = SystemCatalog.incomeSources.first { it.kind == IncomeSourceKind.UNKNOWN_ADJUSTMENT }.id.value

    override suspend fun apply(event: StoredEvent) {
        when (val e = event.event) {
            is BalanceCheckRecorded -> record(event, e)
            is MovementLinkedToReconciliation -> link(e.reconciliationId.value, e.movementId.value)
            is ReconciliationResolved -> resolve(event, e)
            is ReconciliationCancelled -> cancel(e)
            is IncomeRegistered -> e.reconciliationId?.let { link(it.value, e.movementId.value) }
            is ExpenseRegistered -> e.reconciliationId?.let { link(it.value, e.movementId.value) }
            is TransferRegistered -> e.reconciliationId?.let { link(it.value, e.movementId.value) }
            is MovementCorrected -> e.reconciliationId?.let { link(it.value, e.movementId.value) }
            is MovementVoided -> {
                e.reconciliationId?.let { link(it.value, e.movementId.value) }
                voidAdjustment(event, e)
            }
            is ProjectionsRebuilt -> if (e.reason == RebuildReason.VERSION_CHANGE || e.reason == RebuildReason.MANUAL) reverify()
            else -> Unit
        }
    }

    override suspend fun reset() {
        reconciliations.clearLinks()
        reconciliations.clearReconciliations()
        reconciliations.deleteOwnEntries()
        reconciliations.deleteOwnMovements()
    }

    private suspend fun record(event: StoredEvent, e: BalanceCheckRecorded) {
        reconciliations.upsert(
            ReconciliationEntity(
                id = e.reconciliationId.value,
                accountId = e.accountId.value,
                currency = e.realBalance.currency,
                realMinor = e.realBalance.amountMinor,
                snapshotComputedMinor = e.computedBalanceSnapshot.amountMinor,
                checkedSequence = event.sequence,
                status = ReconciliationStatus.OPEN,
                cause = null,
                adjustmentMovementId = null,
                adjustmentMinor = null,
                cancelReason = null,
                checkedAt = event.recordedAt.toString(),
                resolvedAt = null,
                needsReview = false,
            ),
        )
    }

    private suspend fun link(reconciliationId: String, movementId: String) {
        val current = reconciliations.reconciliation(reconciliationId) ?: return
        if (current.status == ReconciliationStatus.CANCELLED) return
        reconciliations.insertLink(ReconciliationLinkEntity(reconciliationId, movementId))
    }

    private suspend fun resolve(event: StoredEvent, e: ReconciliationResolved) {
        val current = reconciliations.reconciliation(e.reconciliationId.value) ?: return
        if (current.status != ReconciliationStatus.OPEN) return
        val adjustment = e.adjustment
        adjustment?.let { post(event, current.accountId, it) }
        reconciliations.upsert(
            current.copy(
                status = ReconciliationStatus.RESOLVED,
                cause = e.cause,
                adjustmentMovementId = adjustment?.movementId?.value,
                adjustmentMinor = adjustment?.let { signed(it) },
                resolvedAt = event.recordedAt.toString(),
            ),
        )
    }

    private suspend fun cancel(e: ReconciliationCancelled) {
        val current = reconciliations.reconciliation(e.reconciliationId.value) ?: return
        if (current.status != ReconciliationStatus.OPEN) return
        reconciliations.upsert(current.copy(status = ReconciliationStatus.CANCELLED, cancelReason = e.reason))
    }

    private suspend fun voidAdjustment(event: StoredEvent, e: MovementVoided) {
        val current = reconciliations.byAdjustment(e.movementId.value) ?: return
        if (!poster.void(e.movementId.value, event.sequence)) return
        reconciliations.upsert(current.copy(needsReview = false))
    }

    private suspend fun reverify() {
        reconciliations.resolved().forEach { row ->
            val voided = row.adjustmentMovementId?.let { reconciliations.isMovementVoided(it) } ?: false
            val needsReview = !voided && verdict(row) == ReviewVerdict.NEEDS_REVIEW
            if (needsReview != row.needsReview) reconciliations.upsert(row.copy(needsReview = needsReview))
        }
    }

    private suspend fun verdict(row: ReconciliationEntity): ReviewVerdict {
        val computed = reconciliations.computedMinor(row.id, row.accountId, row.checkedSequence)
        val difference = ReconciliationRules.remainingDifference(Money(row.realMinor, row.currency), Money(computed, row.currency))
        val adjustment = row.adjustmentMinor?.let { minor ->
            val direction = if (minor >= 0) AdjustmentDirection.UNIDENTIFIED_INCOME else AdjustmentDirection.UNIDENTIFIED_EXPENSE
            AdjustmentData(MovementId(row.adjustmentMovementId.orEmpty()), direction, Money(Math.abs(minor), row.currency))
        }
        return ReviewVerdict.of(difference, adjustment)
    }

    private fun signed(adjustment: AdjustmentData): Long = when (adjustment.direction) {
        AdjustmentDirection.UNIDENTIFIED_INCOME -> adjustment.amount.amountMinor
        AdjustmentDirection.UNIDENTIFIED_EXPENSE -> -adjustment.amount.amountMinor
    }

    private suspend fun post(event: StoredEvent, accountId: String, adjustment: AdjustmentData) {
        val income = adjustment.direction == AdjustmentDirection.UNIDENTIFIED_INCOME
        poster.post(
            MovementEntity(
                id = adjustment.movementId.value,
                type = MovementType.ADJUSTMENT,
                accountId = accountId,
                counterAccountId = null,
                amountMinor = adjustment.amount.amountMinor,
                currency = adjustment.amount.currency,
                incomeSourceId = if (income) adjustmentSourceId else null,
                personId = null,
                note = null,
                occurredOn = event.occurredOn.toString(),
                status = MovementStatus.ACTIVE,
                revision = 1,
                lastSequence = event.sequence,
            ),
            LedgerEntryEntity(
                id = "${event.sequence}-0",
                sequence = event.sequence,
                movementId = adjustment.movementId.value,
                accountId = accountId,
                deltaMinor = signed(adjustment),
                entryKind = LedgerEntryKind.ADJUSTMENT,
                occurredOn = event.occurredOn.toString(),
                isVoided = false,
            ),
        )
    }
}
