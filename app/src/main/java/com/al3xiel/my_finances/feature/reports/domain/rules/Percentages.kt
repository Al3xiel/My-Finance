package com.al3xiel.my_finances.feature.reports.domain.rules

import com.al3xiel.my_finances.core.money.LargestRemainder

/** Porcentajes en puntos base (10 000 = 100%) por el método del mayor residuo; desempate por id ascendente. */
object Percentages {
    const val FULL = 10_000

    fun basisPoints(values: List<Pair<String, Long>>): Map<String, Int> {
        val total = values.sumOf { it.second }
        if (values.isEmpty() || total <= 0L) return values.associate { it.first to 0 }
        return LargestRemainder.distribute(FULL.toLong(), values).mapValues { it.value.toInt() }
    }
}
