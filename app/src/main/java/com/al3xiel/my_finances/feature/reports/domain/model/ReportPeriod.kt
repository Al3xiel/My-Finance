package com.al3xiel.my_finances.feature.reports.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** Periodo de un reporte (D36): mes, año o rango propio. Nunca futuro; el cierre del periodo en curso es hoy. */
sealed interface ReportPeriod {
    val from: LocalDate
    val to: LocalDate

    data class Month(val month: YearMonth) : ReportPeriod {
        override val from: LocalDate get() = month.atDay(1)
        override val to: LocalDate get() = month.atEndOfMonth()
    }

    data class Year(val year: Int) : ReportPeriod {
        override val from: LocalDate get() = LocalDate.of(year, 1, 1)
        override val to: LocalDate get() = LocalDate.of(year, 12, 31)
    }

    data class Custom(override val from: LocalDate, override val to: LocalDate) : ReportPeriod {
        init { require(!from.isAfter(to)) { "from must not be after to" } }
    }

    /** Hasta cuándo se mide: el fin del periodo, o hoy si aún no termina. */
    fun closingOn(today: LocalDate): LocalDate = if (to.isAfter(today)) today else to

    fun isFuture(today: LocalDate): Boolean = from.isAfter(today)

    fun previous(): ReportPeriod? = when (this) {
        is Month -> Month(month.minusMonths(1))
        is Year -> Year(year - 1)
        is Custom -> null
    }

    fun next(today: LocalDate): ReportPeriod? {
        val n = when (this) {
            is Month -> Month(month.plusMonths(1))
            is Year -> Year(year + 1)
            is Custom -> null
        }
        return n?.takeUnless { it.isFuture(today) }
    }

    enum class Invalid { FROM_AFTER_TO, FUTURE }

    companion object {
        fun validateCustom(from: LocalDate, to: LocalDate, today: LocalDate): Invalid? = when {
            from.isAfter(to) -> Invalid.FROM_AFTER_TO
            to.isAfter(today) -> Invalid.FUTURE
            else -> null
        }

        /** Arma el periodo desde los textos de una ruta (D37); un mes o año completos vuelven a su forma. */
        fun fromTexts(from: String, to: String): ReportPeriod {
            val f = LocalDate.parse(from)
            val t = LocalDate.parse(to)
            val month = Month(YearMonth.from(f))
            if (f == month.from && t == month.to) return month
            val year = Year(f.year)
            if (f == year.from && t == year.to) return year
            return Custom(f, t)
        }
    }
}
