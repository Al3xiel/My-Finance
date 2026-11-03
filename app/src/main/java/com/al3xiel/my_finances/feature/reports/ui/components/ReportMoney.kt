package com.al3xiel.my_finances.feature.reports.ui.components

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money

/** Los reportes trabajan en soles por ahora (no hay otras monedas); los ViewModels entregan céntimos como `Long`. */
internal fun reportMoney(minor: Long) = Money(minor, CurrencyCode.PEN)
