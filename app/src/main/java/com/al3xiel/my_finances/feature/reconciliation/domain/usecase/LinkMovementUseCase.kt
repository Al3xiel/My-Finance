package com.al3xiel.my_finances.feature.reconciliation.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.MovementLinkedToReconciliation
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationRules
import javax.inject.Inject

/** Vincula un movimiento que ya existe (olvidado o mal registrado) a una conciliación abierta; no mueve saldos (C2). */
class LinkMovementUseCase @Inject constructor(
    private val reconciliations: ReconciliationRepository,
    private val movements: MovementRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(reconciliationId: ReconciliationId, movementId: MovementId): Outcome<Unit> {
        val movement = movements.find(movementId) ?: return Outcome.Failed(BusinessError.NotFound)
        if (movement.status == MovementStatus.VOIDED) return Outcome.Failed(BusinessError.AlreadyVoided)
        val touched = setOfNotNull(movement.accountId, movement.counterAccountId)
        ReconciliationRules.linkError(reconciliations.find(reconciliationId), touched)?.let { return Outcome.Failed(it) }
        if (reconciliations.isLinked(reconciliationId, movementId)) return Outcome.Ok(Unit)

        recorder.record(MovementLinkedToReconciliation(reconciliationId, movementId))
        return Outcome.Ok(Unit)
    }
}
