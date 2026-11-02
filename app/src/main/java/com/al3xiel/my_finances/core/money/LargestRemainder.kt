package com.al3xiel.my_finances.core.money

import java.math.BigInteger

/**
 * Reparto proporcional por el método del **mayor residuo**: la suma de las porciones es exactamente `total`. Desempate
 * por id ascendente. Aritmética con `BigInteger`, sin `Double`. El resultado conserva el orden de entrada.
 */
object LargestRemainder {
    fun distribute(total: Long, weights: List<Pair<String, Long>>): Map<String, Long> {
        val sum = weights.fold(BigInteger.ZERO) { acc, w -> acc + BigInteger.valueOf(w.second.coerceAtLeast(0)) }
        if (weights.isEmpty() || total <= 0L || sum.signum() == 0) return weights.associate { it.first to 0L }
        val t = BigInteger.valueOf(total)
        val rows = weights.map { (id, w) ->
            val (q, r) = (t * BigInteger.valueOf(w.coerceAtLeast(0))).divideAndRemainder(sum)
            Triple(id, q.toLong(), r)
        }
        val left = (total - rows.sumOf { it.second }).toInt()
        val bonus = rows.sortedWith(compareByDescending<Triple<String, Long, BigInteger>> { it.third }.thenBy { it.first })
            .take(left).map { it.first }.toSet()
        return rows.associate { (id, q, _) -> id to q + if (id in bonus) 1 else 0 }
    }
}
