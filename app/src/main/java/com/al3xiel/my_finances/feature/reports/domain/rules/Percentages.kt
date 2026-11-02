package com.al3xiel.my_finances.feature.reports.domain.rules

/** Porcentajes en puntos base (10 000 = 100%) por el método del mayor residuo; desempate por id ascendente. */
object Percentages {
    const val FULL = 10_000

    fun basisPoints(values: List<Pair<String, Long>>): Map<String, Int> {
        val total = values.sumOf { it.second }
        if (values.isEmpty() || total <= 0L) return values.associate { it.first to 0 }
        val floors = values.map { (id, v) -> Triple(id, (v * FULL / total).toInt(), (v * FULL) % total) }
        val left = FULL - floors.sumOf { it.second }
        val bonus = floors.sortedWith(compareByDescending<Triple<String, Int, Long>> { it.third }.thenBy { it.first })
            .take(left).map { it.first }.toSet()
        return floors.associate { (id, f, _) -> id to f + if (id in bonus) 1 else 0 }
    }
}
