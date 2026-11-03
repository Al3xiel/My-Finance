package com.al3xiel.my_finances.feature.pools.domain.rules

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.AllocationData
import java.time.LocalDate

/** Parte de un gasto con lo que aún falta reembolsar. */
data class PendingPart(
    val partId: PartId,
    val occurredOn: LocalDate,
    val createdSequence: Long,
    val pending: Money,
)

/** Reparte un reembolso entre las partes pendientes, de la más antigua a la más reciente (D18). */
object OldestFirstAllocator {
    fun allocate(parts: List<PendingPart>, amount: Money): Outcome<List<AllocationData>> {
        if (!amount.isPositive) return Outcome.Failed(BusinessError.InvalidAmount)
        val open = parts
            .filter { it.pending.isPositive }
            .sortedWith(compareBy<PendingPart> { it.occurredOn }.thenBy { it.createdSequence })
        val totalPending = open.sumOf { it.pending.amountMinor }
        if (amount.amountMinor > totalPending) return Outcome.Failed(BusinessError.ExceedsPending)

        var left = amount.amountMinor
        val allocations = mutableListOf<AllocationData>()
        for (part in open) {
            if (left == 0L) break
            val take = minOf(left, part.pending.amountMinor)
            allocations += AllocationData(part.partId, Money(take, amount.currency))
            left -= take
        }
        return Outcome.Ok(allocations)
    }
}
