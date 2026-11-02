package com.al3xiel.my_finances.feature.reconciliation.domain.rules

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.event.AdjustmentData

/** Veredicto de re-verificar una conciliación resuelta contra la diferencia que hoy calculan las reglas vigentes. */
enum class ReviewVerdict {
    OK,
    NEEDS_REVIEW,
    ;

    companion object {
        /**
         * `recomputedDifference` es la diferencia al corte del chequeo, sin el ajuste; `adjustment` es el que guardó el evento.
         * Pide revisión si, sin ajuste, la diferencia ya no es 0 (BALANCED, MISSING_RECORD, RECORDING_ERROR) o si el ajuste dejó de coincidir con la diferencia.
         */
        fun of(recomputedDifference: Money, adjustment: AdjustmentData?): ReviewVerdict {
            val expected = ReconciliationRules.adjustmentFor(recomputedDifference)
            val matches = when {
                adjustment == null -> expected == null
                expected == null -> false
                else -> expected.direction == adjustment.direction && expected.amount == adjustment.amount
            }
            return if (matches) OK else NEEDS_REVIEW
        }
    }
}
