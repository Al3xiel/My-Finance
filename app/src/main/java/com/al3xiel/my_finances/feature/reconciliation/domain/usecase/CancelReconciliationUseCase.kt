package com.al3xiel.my_finances.feature.reconciliation.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.ReconciliationCancelled
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import javax.inject.Inject

/** Cancelar exige motivo (C7) y no deja ajustes: solo una conciliación abierta se cancela. */
class CancelReconciliationUseCase @Inject constructor(
    private val reconciliations: ReconciliationRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: ReconciliationId, reason: String): Outcome<Unit> {
        val clean = reason.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.ReasonRequired)
        val current = reconciliations.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (current.status != ReconciliationStatus.OPEN) return Outcome.Failed(BusinessError.ReconciliationNotOpen)

        recorder.record(ReconciliationCancelled(id, clean))
        return Outcome.Ok(Unit)
    }
}
