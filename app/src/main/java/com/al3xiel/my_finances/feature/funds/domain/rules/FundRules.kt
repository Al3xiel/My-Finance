package com.al3xiel.my_finances.feature.funds.domain.rules

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import java.time.LocalDate

/** Lo que las reglas necesitan saber de un fondo para validar una fecha o una edición. */
data class FundTerms(
    val status: FundStatus,
    val mode: ConsumptionMode,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val lastCountOn: LocalDate?,
)

/** Qué datos de un fondo ya no se pueden cambiar. */
data class EditLocks(val mode: Boolean, val startsOn: Boolean, val latestActivityOn: LocalDate? = null)

/** Los datos bloqueables que el usuario quiere dejar. */
data class FundEdit(val mode: ConsumptionMode, val startsOn: LocalDate, val endsOn: LocalDate? = null)

/** Reglas puras de fondos (docs/02 §5, docs/04 §3.6 y §5.5); todo en céntimos, sin redondeo. */
object FundRules {
    fun termsOf(overview: FundOverview) = with(overview.fund) { FundTerms(status, mode, startsOn, endsOn, overview.lastCountOn) }

    /**
     * Gasto no registrado que carga un arqueo: `asignado − detallado − noRegistradoYaCargado − declarado`. Si es negativo,
     * el usuario declaró más de lo que le queda al fondo y se rechaza.
     */
    fun untrackedSpend(allocated: Money, detailedSpent: Money, alreadyUntracked: Money, declaredRemaining: Money): Outcome<Money> {
        if (declaredRemaining.amountMinor < 0) return Outcome.Failed(BusinessError.NegativeAmount)
        val untracked = allocated - detailedSpent - alreadyUntracked - declaredRemaining
        return if (untracked.amountMinor < 0) Outcome.Failed(BusinessError.DeclaredAboveRemaining) else Outcome.Ok(untracked)
    }

    /** Lo que el fondo aparta del Disponible libre: nunca menos de cero, aunque se haya gastado de más. */
    fun reserved(allocated: Money, detailedSpent: Money, untrackedSpent: Money): Money {
        val left = allocated - detailedSpent - untrackedSpent
        return if (left.amountMinor < 0) Money.zero(left.currency) else left
    }

    /** `días de uso × estimado diario`, solo en modo estimado y con estimado definido. */
    fun estimatedSpent(mode: ConsumptionMode, usageDays: Int, dailyEstimate: Money?): Money? {
        if (mode != ConsumptionMode.DAILY_ESTIMATE || dailyEstimate == null) return null
        return Money(Math.multiplyExact(dailyEstimate.amountMinor, usageDays.toLong()), dailyEstimate.currency)
    }

    /**
     * Restante estimado. En modo estimado cuenta lo que sea mayor entre el estimado y lo ya registrado; en los demás modos,
     * lo registrado. Es una estimación y la pantalla la rotula así.
     */
    fun estimatedRemaining(
        mode: ConsumptionMode,
        allocated: Money,
        detailedSpent: Money,
        untrackedSpent: Money,
        usageDays: Int,
        dailyEstimate: Money?,
    ): Money {
        val accounted = detailedSpent + untrackedSpent
        val spent = estimatedSpent(mode, usageDays, dailyEstimate)?.takeIf { it.amountMinor > accounted.amountMinor } ?: accounted
        return allocated - spent
    }

    /** Recarga con dinero propio: mayor a cero y como máximo el Disponible libre. */
    fun checkOwnTopUp(amount: Money, available: Money): BusinessError? = when {
        !amount.isPositive -> BusinessError.InvalidAmount
        amount.amountMinor > available.amountMinor -> BusinessError.ExceedsAvailable
        else -> null
    }

    /** Recarga vinculada a un ingreso: con las ya hechas desde ese ingreso, como máximo su monto (D24). */
    fun checkIncomeTopUp(amount: Money, incomeAmount: Money, alreadyToppedUp: Money): BusinessError? = when {
        !amount.isPositive -> BusinessError.InvalidAmount
        amount.amountMinor + alreadyToppedUp.amountMinor > incomeAmount.amountMinor -> BusinessError.ExceedsIncome
        else -> null
    }

    /** Un gasto con fondo: fondo activo, fecha dentro de su rango y no anterior al último arqueo (estricto, D21). */
    fun checkExpenseDate(terms: FundTerms, occurredOn: LocalDate): BusinessError? = when {
        terms.status == FundStatus.CLOSED -> BusinessError.FundClosed
        outOfRange(terms, occurredOn) -> BusinessError.DateOutOfFundRange
        terms.lastCountOn != null && occurredOn.isBefore(terms.lastCountOn) -> BusinessError.DateBeforeLastCount
        else -> null
    }

    /** Un día de uso: fondo activo en modo estimado, dentro de su rango y no futuro. */
    fun checkUsageDay(terms: FundTerms, day: LocalDate, today: LocalDate): BusinessError? = when {
        terms.status == FundStatus.CLOSED -> BusinessError.FundClosed
        terms.mode != ConsumptionMode.DAILY_ESTIMATE -> BusinessError.UsageDayNotAllowed
        outOfRange(terms, day) -> BusinessError.DateOutOfFundRange
        day.isAfter(today) -> BusinessError.FutureDate
        else -> null
    }

    /**
     * Nombre y estimado siempre se editan. El modo no cambia con días de uso o arqueos; el inicio no con gastos asignados ni días
     * de uso; el fin no puede quedar antes de la última actividad.
     */
    fun editLocks(hasUsageDays: Boolean, hasCounts: Boolean, hasAssignedExpenses: Boolean, latestActivityOn: LocalDate? = null) =
        EditLocks(mode = hasUsageDays || hasCounts, startsOn = hasAssignedExpenses || hasUsageDays, latestActivityOn = latestActivityOn)

    fun checkEdit(current: FundTerms, edit: FundEdit, locks: EditLocks): BusinessError? = when {
        current.status == FundStatus.CLOSED -> BusinessError.FundClosed
        locks.mode && edit.mode != current.mode -> BusinessError.FieldLocked
        locks.startsOn && edit.startsOn != current.startsOn -> BusinessError.FieldLocked
        edit.endsOn != null && locks.latestActivityOn != null && edit.endsOn.isBefore(locks.latestActivityOn) ->
            BusinessError.DateOutOfFundRange
        else -> null
    }

    private fun outOfRange(terms: FundTerms, day: LocalDate) =
        day.isBefore(terms.startsOn) || (terms.endsOn != null && day.isAfter(terms.endsOn))
}
