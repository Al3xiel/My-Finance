package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import java.time.LocalDate

/** Reglas puras de ingresos esperados (docs/04 §3.7 y D27); sin reloj propio: el día llega como argumento. */
object ExpectedIncomeRules {
    /** El rango puede estar en el pasado, pero `from ≤ to`. */
    fun validateRange(range: ExpectedRange): BusinessError? =
        if (range.from.isAfter(range.to)) BusinessError.InvalidRange else null

    /**
     * Estado efectivo. Un confirmado cuyo movimiento se anuló vuelve a pendiente (programado o vencido, según el
     * rango); `OVERDUE` es hoy > `expectedTo`.
     */
    fun effectiveStatus(
        stored: ExpectedIncomeStatus,
        expectedTo: LocalDate,
        today: LocalDate,
        confirmedMovementVoided: Boolean = false,
    ): ExpectedIncomeStatus = when {
        stored == ExpectedIncomeStatus.CANCELLED -> ExpectedIncomeStatus.CANCELLED
        stored == ExpectedIncomeStatus.CONFIRMED && !confirmedMovementVoided -> ExpectedIncomeStatus.CONFIRMED
        ExpectedIncomeStatusRules.isOverdue(expectedTo, today) ->ExpectedIncomeStatus.OVERDUE
        else -> ExpectedIncomeStatus.SCHEDULED
    }

    /** Pendiente = programado o vencido: lo único que se puede editar, cancelar o confirmar. */
    fun isPending(status: ExpectedIncomeStatus) = status == ExpectedIncomeStatus.SCHEDULED || status == ExpectedIncomeStatus.OVERDUE
}
