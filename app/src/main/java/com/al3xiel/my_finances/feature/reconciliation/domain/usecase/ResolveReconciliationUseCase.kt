package com.al3xiel.my_finances.feature.reconciliation.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AdjustmentData
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.model.event.ReconciliationResolved
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationRules
import javax.inject.Inject

/**
 * Paso 2: declara la causa y, solo si queda diferencia, crea el ingreso o egreso no identificado por su valor absoluto
 * (C3). El id del movimiento de ajuste lo genera este caso de uso y viaja en el evento, para que el replay lo reproduzca.
 */
class ResolveReconciliationUseCase @Inject constructor(
    private val reconciliations: ReconciliationRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    /** La diferencia se lee y el ajuste se registra dentro del mismo lock que escribe movimientos: no se mueve entre una cosa y la otra. */
    suspend operator fun invoke(id: ReconciliationId, cause: ReconciliationCause, note: String?): Outcome<Unit> =
        lock.withLock { execute(id, cause, note) }

    private suspend fun execute(id: ReconciliationId, cause: ReconciliationCause, note: String?): Outcome<Unit> {
        val current = reconciliations.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (current.status != ReconciliationStatus.OPEN) return Outcome.Failed(BusinessError.ReconciliationNotOpen)
        val difference = reconciliations.remainingDifference(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (!ReconciliationRules.isCauseAllowed(cause, difference)) return Outcome.Failed(BusinessError.CauseNotAllowed)

        val adjustment = ReconciliationRules.adjustmentFor(difference)?.let {
            AdjustmentData(MovementId(ids.newId()), it.direction, it.amount)
        }
        recorder.record(ReconciliationResolved(id, cause, adjustment, note?.trim()?.takeIf { it.isNotEmpty() }))
        return Outcome.Ok(Unit)
    }
}
