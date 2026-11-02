package com.al3xiel.my_finances.feature.reconciliation.domain.rules

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause

/** Lo que `ReconciliationResolved` lleva como ajuste, sin el id del movimiento (lo genera el caso de uso). */
data class AdjustmentSpec(val direction: AdjustmentDirection, val amount: Money)

/** Reglas puras de la conciliación (C1–C3, C7). Todo en `Money`; el signo de la diferencia es el del banco menos la app. */
object ReconciliationRules {
    private val needNoAdjustment = setOf(ReconciliationCause.BALANCED, ReconciliationCause.MISSING_RECORD, ReconciliationCause.RECORDING_ERROR)
    private val needAdjustment = setOf(ReconciliationCause.UNKNOWN, ReconciliationCause.SUSPECTED_APP_ERROR)

    /** Saldo de la app al momento: asientos de la cuenta hasta el chequeo (sin los vinculados) más los vigentes de los vinculados. */
    fun computedBalance(accountAtCheck: Money, linkedCurrent: Money): Money = accountAtCheck + linkedCurrent

    fun remainingDifference(real: Money, computed: Money): Money = real - computed

    fun allowedCauses(difference: Money): Set<ReconciliationCause> = if (difference.isZero) needNoAdjustment else needAdjustment

    fun isCauseAllowed(cause: ReconciliationCause, difference: Money): Boolean = cause in allowedCauses(difference)

    /** `null` con diferencia 0; si no, ingreso con signo positivo (el banco tiene más) o egreso, por `|diferencia|`. */
    fun adjustmentFor(difference: Money): AdjustmentSpec? = when {
        difference.isZero -> null
        difference.isPositive -> AdjustmentSpec(AdjustmentDirection.UNIDENTIFIED_INCOME, difference)
        else -> AdjustmentSpec(AdjustmentDirection.UNIDENTIFIED_EXPENSE, Money(Math.negateExact(difference.amountMinor), difference.currency))
    }
}
