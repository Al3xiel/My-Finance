package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FundClosed
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import javax.inject.Inject

/**
 * Cierra el fondo. `ROLL_OVER` pasa el sobrante a otro fondo activo y guarda el monto en el evento (D22); `RELEASE_TO_AVAILABLE`
 * lo devuelve al Disponible libre y no lleva destino.
 */
class CloseFundUseCase @Inject constructor(
    private val funds: FundRepository,
    private val recorder: EventRecorder,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(id: FundId, policy: RemainderPolicy, rollOverToFundId: FundId?): Outcome<Unit> = lock.withLock {
        val fund = funds.find(id) ?: return@withLock Outcome.Failed(BusinessError.NotFound)
        if (fund.fund.status == FundStatus.CLOSED) return@withLock Outcome.Failed(BusinessError.FundClosed)

        val event = when (policy) {
            RemainderPolicy.RELEASE_TO_AVAILABLE -> FundClosed(id, policy, null, null)
            RemainderPolicy.ROLL_OVER -> {
                if (rollOverToFundId == null || rollOverToFundId == id) return@withLock Outcome.Failed(BusinessError.NotFound)
                val target = funds.find(rollOverToFundId) ?: return@withLock Outcome.Failed(BusinessError.NotFound)
                if (target.fund.status == FundStatus.CLOSED) return@withLock Outcome.Failed(BusinessError.FundClosed)
                FundClosed(id, policy, rollOverToFundId, fund.reserved)
            }
        }
        recorder.record(event)
        Outcome.Ok(Unit)
    }
}
