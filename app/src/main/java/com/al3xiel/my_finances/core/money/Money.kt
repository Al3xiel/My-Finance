package com.al3xiel.my_finances.core.money

import kotlinx.serialization.Serializable

/** Dinero en unidades menores (céntimos); nunca `Double`. El signo lo da el tipo de movimiento, no el monto. */
@Serializable
data class Money(val amountMinor: Long, val currency: CurrencyCode) {
    val isZero: Boolean get() = amountMinor == 0L
    val isPositive: Boolean get() = amountMinor > 0L

    operator fun plus(other: Money): Money {
        requireSame(other)
        return Money(Math.addExact(amountMinor, other.amountMinor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSame(other)
        return Money(Math.subtractExact(amountMinor, other.amountMinor), currency)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(amountMinor), currency)

    private fun requireSame(other: Money) =
        require(currency == other.currency) { "currency mismatch: $currency vs ${other.currency}" }

    companion object {
        fun zero(currency: CurrencyCode) = Money(0, currency)
    }
}
