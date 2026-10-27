package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolArchived
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolCreated
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolRenamed
import com.al3xiel.my_finances.domain.model.event.SettlementStatementGenerated
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import javax.inject.Inject

/**
 * Cúmulos, periodos, rendiciones y a qué periodo pertenece cada parte (`movement_parts.pool_period_id`).
 *
 * Los proyectores corren uno tras otro sobre todo el registro (no evento a evento), así que este solo lee sus tablas y
 * la columna que le pertenece; lo demás lo toma del evento. Una parte no abre ni cierra periodos: cae en el abierto de
 * su cúmulo (D19) y conserva su periodo mientras siga en el mismo cúmulo, aunque ese periodo ya esté rendido.
 * Los totales y estados los fija [PoolPeriodRefresher].
 */
class PoolProjector @Inject constructor(
    private val pools: PoolDao,
    private val refresher: PoolPeriodRefresher,
) : EventProjector {
    override val name = "pool"
    override val version = 1
    override val order = 10

    override suspend fun apply(event: StoredEvent) {
        val occurredOn = event.occurredOn.toString()
        when (val e = event.event) {
            is ReimbursementPoolCreated -> create(e, occurredOn)

            is ReimbursementPoolRenamed -> pools.pool(e.poolId.value)?.let { pools.upsertPool(it.copy(name = e.name)) }

            is ReimbursementPoolArchived -> pools.pool(e.poolId.value)?.let { pools.upsertPool(it.copy(isArchived = true)) }

            is SettlementStatementGenerated -> {
                sendStatement(e, occurredOn)
                refresher.refresh()
            }

            is ExpenseRegistered -> {
                assign(e.parts)
                refresher.refresh()
            }

            is MovementCorrected -> (e.snapshot as? MovementSnapshot.Expense)?.let {
                assign(it.parts)
                refresher.refresh()
            }

            is MovementVoided -> {
                pools.clearPeriodsOf(e.movementId.value)
                refresher.refresh()
            }

            else -> Unit
        }
    }

    override suspend fun reset() {
        pools.clearAssignedPeriods()
        pools.clearStatements()
        pools.clearPeriods()
        pools.clearPools()
    }

    private suspend fun create(e: ReimbursementPoolCreated, occurredOn: String) {
        val id = e.poolId.value
        pools.upsertPool(
            PoolEntity(
                id = id,
                name = e.name,
                responsiblePersonId = e.responsiblePersonId.value,
                kind = e.kind,
                defaultCategoryId = e.defaultCategoryId?.value,
                defaultTagIds = e.defaultTagIds.joinToString(",") { it.value },
                isArchived = false,
            ),
        )
        openPeriod(id, occurredOn)
    }

    /** Id determinista: así el replay produce los mismos periodos. */
    private suspend fun openPeriod(poolId: String, openedOn: String) {
        val n = pools.periodCount(poolId) + 1
        pools.upsertPeriod(
            PoolPeriodEntity("$poolId-p$n", poolId, openedOn, null, PoolPeriodStatus.OPEN, null, 0, 0),
        )
    }

    private suspend fun sendStatement(e: SettlementStatementGenerated, occurredOn: String) {
        val pool = pools.pool(e.poolId.value) ?: return
        val period = pools.period(e.periodId.value) ?: return
        // Un periodo permanente se rinde una sola vez: una segunda rendición del mismo periodo no abre otro.
        if (pool.kind == PoolKind.PERMANENT && period.closedOn != null) return
        pools.insertStatement(
            SettlementStatementEntity(
                e.statementId.value, pool.id, period.id, e.pendingTotal.amountMinor, e.format, occurredOn,
            ),
        )
        if (pool.kind == PoolKind.PERMANENT) {
            pools.upsertPeriod(period.copy(statementId = e.statementId.value, closedOn = occurredOn))
            openPeriod(pool.id, occurredOn)
        } else {
            pools.upsertPeriod(period.copy(statementId = e.statementId.value))
        }
    }

    private suspend fun assign(parts: List<ExpensePartData>) {
        parts.forEach { part ->
            val partId = part.partId.value
            val current = pools.periodOfPart(partId)
            val poolId = part.poolId?.value
            if (poolId == null) {
                if (current != null) pools.assignPeriod(partId, null)
                return@forEach
            }
            if (current != null && pools.period(current)?.poolId == poolId) return@forEach
            pools.assignPeriod(partId, pools.openPeriodOf(poolId)?.id)
        }
    }
}
