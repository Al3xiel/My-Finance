package com.al3xiel.my_finances.core.money

import kotlin.math.abs

/** Formato `es-PE` (`S/ 1,234.50`) y lectura de lo que se teclea; sin `Locale` para que sea determinista. */
object MoneyFormatter {
    fun format(money: Money): String {
        val abs = abs(money.amountMinor)
        val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        val cents = (abs % 100).toString().padStart(2, '0')
        val sign = if (money.amountMinor < 0) "-" else ""
        return "$sign${money.currency.symbol} $whole.$cents"
    }

    /** Texto tecleado (`12`, `12.5`, `12,50`, `1,234.56`) a céntimos; `null` si no es un monto válido y no negativo. */
    fun parseMinor(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val lastSep = t.indexOfLast { it == '.' || it == ',' }
        val decimals = if (lastSep >= 0 && t.length - lastSep - 1 in 1..2) t.substring(lastSep + 1) else null
        val wholeRaw = if (decimals != null) t.substring(0, lastSep) else t
        val whole = wholeRaw.replace(".", "").replace(",", "")
        if (whole.isEmpty() && decimals == null) return null
        if (!whole.all { it.isDigit() } || decimals?.all { it.isDigit() } == false) return null
        if (decimals == null && lastSep >= 0) return null
        val major = if (whole.isEmpty()) 0L else whole.toLongOrNull() ?: return null
        val cents = decimals?.padEnd(2, '0')?.toLong() ?: 0L
        return try {
            Math.addExact(Math.multiplyExact(major, 100L), cents)
        } catch (_: ArithmeticException) {
            null
        }
    }
}
