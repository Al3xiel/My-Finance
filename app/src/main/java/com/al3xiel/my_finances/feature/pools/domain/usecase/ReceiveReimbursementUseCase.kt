package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.ReimbursementId
import com.al3xiel.my_finances.domain.model.event.AllocationData
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.ReimbursementReceived
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.pools.domain.rules.OldestFirstAllocator
import com.al3xiel.my_finances.feature.pools.domain.rules.PendingPart
import java.time.LocalDate
import javax.inject.Inject

/**
 * Registra un reembolso recibido. Las asignaciones se calculan aquí y se guardan en el evento (aunque la estrategia sea
 * `OLDEST_FIRST`), así un cambio futuro del algoritmo no toca el pasado. Un monto mayor a lo pendiente se rechaza (D1).
 * Anular el reembolso es `MovementVoided` sobre el movimiento que devuelve (D20).
 */
class ReceiveReimbursementUseCase @Inject constructor(
    private val pools: PoolRepository,
    private val people: PersonRepository,
    private val rules: MovementRules,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    /** `poolId` limita `OLDEST_FIRST` al cúmulo de origen; sin él cubre todos los cúmulos de la persona (D18). */
    suspend operator fun invoke(
        personId: PersonId,
        accountId: AccountId,
        amount: Money,
        strategy: AllocationStrategy,
        poolId: PoolId?,
        manualAllocations: List<AllocationData>,
        note: String?,
        occurredOn: LocalDate,
    ): Outcome<MovementId> =
        lock.withLock { execute(personId, accountId, amount, strategy, poolId, manualAllocations, note, occurredOn) }

    private suspend fun execute(
        personId: PersonId,
        accountId: AccountId,
        amount: Money,
        strategy: AllocationStrategy,
        poolId: PoolId?,
        manualAllocations: List<AllocationData>,
        note: String?,
        occurredOn: LocalDate,
    ): Outcome<MovementId> {
        rules.checkMoney(amount)?.let { return Outcome.Failed(it) }
        rules.checkDate(occurredOn)?.let { return Outcome.Failed(it) }
        rules.checkAccount(accountId)?.let { return Outcome.Failed(it) }
        people.find(personId) ?: return Outcome.Failed(BusinessError.NotFound)

        val pending = pools.pendingParts(personId, poolId)
        val allocations = when (strategy) {
            AllocationStrategy.OLDEST_FIRST -> when (val result = OldestFirstAllocator.allocate(pending, amount)) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value
            }
            AllocationStrategy.MANUAL -> checkManual(pending, amount, manualAllocations)?.let { return Outcome.Failed(it) }
                ?: manualAllocations
        }

        val reimbursementId = ReimbursementId(ids.newId())
        val movementId = MovementId(ids.newId())
        recorder.record(
            ReimbursementReceived(
                reimbursementId, movementId, personId, accountId, amount, strategy, allocations, note?.trim()?.ifEmpty { null },
            ),
            occurredOn,
        )
        return Outcome.Ok(movementId)
    }

    private fun checkManual(pending: List<PendingPart>, amount: Money, allocations: List<AllocationData>): BusinessError? {
        val byPart = pending.associateBy { it.partId }
        if (allocations.map { it.partId }.toSet().size != allocations.size) return BusinessError.InvalidPart
        for (allocation in allocations) {
            val part = byPart[allocation.partId] ?: return BusinessError.InvalidPart
            if (allocation.amount.currency != CurrencyCode.PEN) return BusinessError.UnsupportedCurrency
            if (!allocation.amount.isPositive) return BusinessError.InvalidAmount
            if (allocation.amount.amountMinor > part.pending.amountMinor) return BusinessError.ExceedsPending
        }
        if (allocations.sumOf { it.amount.amountMinor } != amount.amountMinor) return BusinessError.PartsMismatch
        return null
    }
}
