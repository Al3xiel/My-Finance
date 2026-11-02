package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import javax.inject.Inject

/** Cancela un ingreso esperado pendiente, con motivo opcional. Cancelar una ocurrencia termina la serie (P3). */
class CancelExpectedIncomeUseCase @Inject constructor(
    private val expectedIncomes: ExpectedIncomeRepository,
    private val recorder: EventRecorder,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(id: ExpectedIncomeId, reason: String?): Outcome<Unit> = lock.withLock {
        val current = expectedIncomes.find(id) ?: return@withLock Outcome.Failed(BusinessError.NotFound)
        if (!ExpectedIncomeRules.isPending(current.status)) return@withLock Outcome.Failed(BusinessError.NotPending)

        recorder.record(ExpectedIncomeCancelled(id, reason?.trim()?.takeIf { it.isNotEmpty() }))
        Outcome.Ok(Unit)
    }
}
