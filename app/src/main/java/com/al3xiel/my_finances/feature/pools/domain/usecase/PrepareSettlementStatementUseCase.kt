package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.pools.domain.model.SettlementStatementContent
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.pools.domain.rules.SettlementStatementBuilder
import javax.inject.Inject

/** Vista previa de la rendición del periodo actual: no emite eventos. Es lo que ve la pantalla antes de confirmar (D17). */
class PrepareSettlementStatementUseCase @Inject constructor(
    private val pools: PoolRepository,
    private val people: PersonRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(poolId: PoolId): Outcome<SettlementStatementContent> {
        val pool = pools.find(poolId) ?: return Outcome.Failed(BusinessError.NotFound)
        if (pool.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        val period = pools.currentPeriod(poolId) ?: return Outcome.Failed(BusinessError.NotFound)
        val alias = people.find(pool.responsiblePersonId)?.alias ?: return Outcome.Failed(BusinessError.NotFound)

        val content = SettlementStatementBuilder.build(
            pool = pool,
            responsibleAlias = alias,
            period = period,
            parts = pools.periodParts(period.id),
            previousPending = Money(pools.pendingBefore(period.id), CurrencyCode.PEN),
            generatedOn = recorder.today(),
        )
        if (content.lines.isEmpty()) return Outcome.Failed(BusinessError.NothingPending)
        return Outcome.Ok(content)
    }
}
