package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import javax.inject.Inject

/** Recarga con dinero propio: sale del Disponible libre, no de una cuenta en particular (D24). */
class TopUpFundUseCase @Inject constructor(
    private val funds: FundRepository,
    private val recorder: EventRecorder,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(id: FundId, amount: Money): Outcome<Unit> = lock.withLock {
        val fund = funds.find(id) ?: return@withLock Outcome.Failed(BusinessError.NotFound)
        if (fund.fund.status == FundStatus.CLOSED) return@withLock Outcome.Failed(BusinessError.FundClosed)
        val available = Money(funds.availableBalanceMinor(), amount.currency)
        FundRules.checkOwnTopUp(amount, available)?.let { return@withLock Outcome.Failed(it) }

        recorder.record(FundToppedUp(id, amount, null))
        Outcome.Ok(Unit)
    }
}
