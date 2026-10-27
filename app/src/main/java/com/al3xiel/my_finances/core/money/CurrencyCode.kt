package com.al3xiel.my_finances.core.money

import kotlinx.serialization.Serializable

/** Solo `PEN` se registra (D3); `USD` existe para que la suma pueda rechazar monedas mezcladas. */
@Serializable
enum class CurrencyCode(val symbol: String) {
    PEN("S/"),
    USD("US$"),
}
