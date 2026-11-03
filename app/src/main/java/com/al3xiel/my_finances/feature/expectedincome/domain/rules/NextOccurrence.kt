package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import java.time.LocalDate
import java.time.YearMonth

/** Rango de un ingreso esperado. */
data class ExpectedRange(val from: LocalDate, val to: LocalDate)

/**
 * Siguiente ocurrencia mensual (D29): +1 mes en `expectedFrom` y en `expectedTo`, sin importar cuándo se cobró. Una
 * fecha que es el último día de su mes cae en el último día del mes siguiente; el resto, el mismo día (o el último si
 * el mes siguiente es más corto). Un rango que cruza de mes conserva ese cruce.
 */
object NextOccurrence {
    fun of(range: ExpectedRange) = ExpectedRange(shift(range.from), shift(range.to))

    fun shift(date: LocalDate): LocalDate {
        val next = YearMonth.from(date).plusMonths(1)
        val isLastDay = date.dayOfMonth == date.lengthOfMonth()
        return next.atDay(if (isLastDay) next.lengthOfMonth() else minOf(date.dayOfMonth, next.lengthOfMonth()))
    }
}
