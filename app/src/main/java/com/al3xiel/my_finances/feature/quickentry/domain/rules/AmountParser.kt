package com.al3xiel.my_finances.feature.quickentry.domain.rules

import java.math.BigDecimal

/** Un monto escrito a mano: `45`, `12.50`, `12,50`, `S/45`; sin separador de miles y con 2 decimales como máximo. */
object AmountParser {
    private val shape = Regex("^\\+?(?:[sS]/\\.?)?(\\d{1,12})(?:[.,](\\d{1,2}))?$")

    fun parseMinor(token: String): Long? {
        val match = shape.matchEntire(token) ?: return null
        val whole = match.groupValues[1]
        val fraction = match.groupValues[2]
        return BigDecimal("$whole.${fraction.ifEmpty { "0" }}").movePointRight(2).longValueExact()
    }

    /** `S/` suelto, para unirlo al número que sigue. */
    fun isCurrencyPrefix(token: String): Boolean = token.equals("S/", ignoreCase = true) || token.equals("S/.", ignoreCase = true)
}
