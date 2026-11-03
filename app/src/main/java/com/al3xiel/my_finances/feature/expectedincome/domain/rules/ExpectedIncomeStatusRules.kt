package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import java.time.LocalDate

/** La única regla de «vencido» (D27, D56): la comparten Ingresos esperados e Inicio; el día llega como argumento. */
object ExpectedIncomeStatusRules {
    /** Vencido = hoy es posterior al fin del rango. */
    fun isOverdue(expectedTo: LocalDate, today: LocalDate): Boolean = today.isAfter(expectedTo)
}
