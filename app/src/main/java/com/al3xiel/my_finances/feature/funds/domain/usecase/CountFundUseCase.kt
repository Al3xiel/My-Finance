package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FundCounted
import com.al3xiel.my_finances.domain.model.event.UntrackedSpendData
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import javax.inject.Inject

/**
 * Arqueo: el usuario declara cuánto le queda. Lo que falta contra lo ya cargado se convierte en un gasto no registrado
 * (con su cuenta y categoría) dentro del mismo evento; devuelve ese monto (cero si no hay).
 */
class CountFundUseCase @Inject constructor(
    private val funds: FundRepository,
    private val rules: MovementRules,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(
        id: FundId,
        declaredRemaining: Money,
        accountId: AccountId?,
        categoryId: CategoryId?,
    ): Outcome<Money> = lock.withLock {
        val fund = funds.find(id) ?: return@withLock Outcome.Failed(BusinessError.NotFound)
        if (fund.fund.status == FundStatus.CLOSED) return@withLock Outcome.Failed(BusinessError.FundClosed)

        val untracked = when (
            val result = FundRules.untrackedSpend(fund.fund.allocated, fund.detailedSpent, fund.fund.untrackedSpent, declaredRemaining)
        ) {
            is Outcome.Failed -> return@withLock result
            is Outcome.Ok -> result.value
        }

        val spend = if (untracked.isPositive) {
            if (accountId == null || categoryId == null) return@withLock Outcome.Failed(BusinessError.NotFound)
            rules.checkAccount(accountId)?.let { return@withLock Outcome.Failed(it) }
            rules.checkCategory(categoryId)?.let { return@withLock Outcome.Failed(it) }
            UntrackedSpendData(MovementId(ids.newId()), accountId, categoryId, untracked)
        } else {
            null
        }

        recorder.record(FundCounted(id, declaredRemaining, spend))
        Outcome.Ok(untracked)
    }
}
