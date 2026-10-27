package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.movement.LedgerEntryEntity
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementEntity
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.ReimbursementReceived
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.pools.domain.rules.PendingRules
import javax.inject.Inject

/**
 * Reembolsos recibidos: el movimiento `REIMBURSEMENT` con su asiento `REIMBURSEMENT_IN` (no es ingreso), las
 * asignaciones y, de ellas, `reimbursed_minor` y el estado de cada parte. Anular el movimiento borra las asignaciones y
 * lo revierte todo (D20).
 *
 * El estado de una parte es una función de sus asignaciones y de su monto actual (por eso también atiende las
 * correcciones), así que no depende de cuándo se creó la fila. Corre después
 * de movimientos y cúmulos: lee las partes que ellos dejan.
 */
class ReimbursementProjector @Inject constructor(
    private val reimbursements: ReimbursementDao,
    private val poster: LedgerPoster,
    private val periods: PoolPeriodRefresher,
) : EventProjector {
    override val name = "reimbursement"
    override val version = 1
    override val order = 20

    private val sourceId = SystemCatalog.incomeSources.first { it.kind == IncomeSourceKind.REIMBURSEMENT }.id.value

    override suspend fun apply(event: StoredEvent) {
        when (val e = event.event) {
            is ReimbursementReceived -> receive(event, e)
            is MovementVoided -> void(event, e)
            // Corregir un gasto cambia el monto de sus partes: el estado de lo reembolsado depende de ese monto.
            is MovementCorrected -> (e.snapshot as? MovementSnapshot.Expense)?.let { refreshParts(it.parts.map { part -> part.partId.value }) }
            else -> Unit
        }
    }

    override suspend fun reset() {
        reimbursements.clearAllocations()
        reimbursements.clearReimbursements()
        reimbursements.deleteOwnEntries()
        reimbursements.deleteOwnMovements()
        reimbursements.resetParts()
    }

    private suspend fun receive(event: StoredEvent, e: ReimbursementReceived) {
        val occurredOn = event.occurredOn.toString()
        val movementId = e.movementId.value
        poster.post(
            MovementEntity(
                id = movementId,
                type = MovementType.REIMBURSEMENT,
                accountId = e.accountId.value,
                counterAccountId = null,
                amountMinor = e.amount.amountMinor,
                currency = e.amount.currency,
                incomeSourceId = sourceId,
                personId = e.personId.value,
                note = e.note,
                occurredOn = occurredOn,
                status = MovementStatus.ACTIVE,
                revision = 1,
                lastSequence = event.sequence,
            ),
            LedgerEntryEntity(
                id = "${event.sequence}-0",
                sequence = event.sequence,
                movementId = movementId,
                accountId = e.accountId.value,
                deltaMinor = e.amount.amountMinor,
                entryKind = LedgerEntryKind.REIMBURSEMENT_IN,
                occurredOn = occurredOn,
                isVoided = false,
            ),
        )
        reimbursements.insertReimbursement(
            ReimbursementEntity(e.reimbursementId.value, e.personId.value, movementId, e.amount.amountMinor, e.strategy, occurredOn),
        )
        reimbursements.insertAllocations(
            e.allocations.map { ReimbursementAllocationEntity(e.reimbursementId.value, it.partId.value, it.amount.amountMinor) },
        )
        refreshParts(e.allocations.map { it.partId.value })
    }

    private suspend fun void(event: StoredEvent, e: MovementVoided) {
        val reimbursement = reimbursements.byMovement(e.movementId.value) ?: return
        if (!poster.void(e.movementId.value, event.sequence)) return
        val partIds = reimbursements.partIdsOf(reimbursement.id)
        reimbursements.deleteAllocationsOf(reimbursement.id)
        refreshParts(partIds)
    }

    private suspend fun refreshParts(partIds: List<String>) {
        partIds.distinct().forEach { partId ->
            val amount = reimbursements.partAmount(partId) ?: return@forEach
            val reimbursed = reimbursements.allocatedTo(partId)
            val status = PendingRules.statusOf(Money(amount, CurrencyCode.PEN), Money(reimbursed, CurrencyCode.PEN))
            reimbursements.setPartReimbursement(partId, reimbursed, status)
        }
        periods.refresh()
    }
}
