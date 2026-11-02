package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.domain.usecase.MovementVoidFollowUp
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import javax.inject.Inject

/**
 * Anular el ingreso que confirmó un esperado (D28): el esperado vuelve solo a pendiente (estado efectivo, D27) y el
 * siguiente que había generado se cancela con motivo, en el mismo lote que `MovementVoided`. Si ese siguiente ya se
 * confirmó, la anulación se veta. Un ingreso que no confirmó nada, o sin siguiente, no tiene consecuencias.
 */
class ExpectedIncomeVoidHooks @Inject constructor(
    private val expectedIncomes: ExpectedIncomeRepository,
) : MovementVoidFollowUp {
    override suspend fun followUp(movementId: MovementId): Outcome<List<DomainEvent>> {
        val confirmed = expectedIncomes.confirmedBy(movementId) ?: return Outcome.Ok(emptyList())
        val next = confirmed.nextExpectedId?.let { expectedIncomes.find(it) } ?: return Outcome.Ok(emptyList())
        return when {
            next.status == ExpectedIncomeStatus.CONFIRMED -> Outcome.Failed(BusinessError.NextExpectedConfirmed)
            ExpectedIncomeRules.isPending(next.status) -> Outcome.Ok(listOf(ExpectedIncomeCancelled(next.id, CANCEL_REASON)))
            else -> Outcome.Ok(emptyList())
        }
    }

    companion object {
        /** Motivo guardado en el evento cuando la serie se corta por anular el ingreso que la generó. */
        const val CANCEL_REASON = "Se anuló el ingreso que lo generó"
    }
}
