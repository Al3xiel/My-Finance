package com.al3xiel.my_finances.feature.home.domain.rules

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview

/** Qué muestra la barra de cada fondo en Inicio: lo consumido, y si esa cifra es una estimación. */
object HomeFundRules {
    fun consumed(overview: FundOverview): Money = overview.estimatedSpent ?: (overview.detailedSpent + overview.fund.untrackedSpent)

    /** Solo los modos estimados rotulan «estimado». */
    fun isEstimated(overview: FundOverview): Boolean = overview.estimatedSpent != null

    fun fraction(consumed: Money, allocated: Money): Float =
        if (allocated.amountMinor <= 0) 0f else (consumed.amountMinor.toFloat() / allocated.amountMinor).coerceIn(0f, 1f)
}
