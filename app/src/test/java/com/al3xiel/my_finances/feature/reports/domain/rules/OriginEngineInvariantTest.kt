package com.al3xiel.my_finances.feature.reports.domain.rules

import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRow
import com.al3xiel.my_finances.feature.reports.domain.model.OriginEntry
import com.al3xiel.my_finances.feature.reports.domain.model.OriginPart

import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginEngineInvariantTest {
    @Test fun `the sum of the mix of each account is its balance for any sequence`() {
        repeat(30) { seed ->
            val rnd = Random(seed)
            val engine = OriginEngine()
            val balance = mutableMapOf("A" to 0L, "B" to 0L, "C" to 0L)
            var n = 0L
            fun next(
                account: String, delta: Long, kind: LedgerEntryKind, src: String? = null, mv: String? = null,
                parts: List<OriginPart> = emptyList(),
            ): List<OriginAttributionRow> {
                n++
                balance[account] = balance.getValue(account) + delta
                return engine.apply(OriginEntry("e$n", n, n, account, delta, kind, mv, src, parts))
            }
            repeat(200) {
                val acc = balance.keys.random(rnd)
                when (rnd.nextInt(4)) {
                    0 -> next(acc, rnd.nextLong(1, 100_000), LedgerEntryKind.INCOME, "s${rnd.nextInt(4)}")
                    1 -> {
                        val amount = rnd.nextLong(1, 150_000)
                        val cut = rnd.nextLong(0, amount)
                        val rows = next(
                            acc, -amount, LedgerEntryKind.EXPENSE,
                            parts = listOf(OriginPart("a$n", cut), OriginPart("b$n", amount - cut)),
                        )
                        assertEquals(amount, rows.sumOf { it.amountMinor })
                    }
                    2 -> {
                        val amount = rnd.nextLong(1, 120_000)
                        val to = (balance.keys - acc).random(rnd)
                        next(acc, -amount, LedgerEntryKind.TRANSFER_OUT, mv = "m$n")
                        next(to, amount, LedgerEntryKind.TRANSFER_IN, mv = "m$n")
                    }
                    else -> {
                        val amount = rnd.nextLong(1, 5_000)
                        val rows = next(acc, -amount, LedgerEntryKind.ADJUSTMENT)
                        assertEquals(amount, rows.sumOf { it.amountMinor })
                    }
                }
                val mix = engine.balances().entries.groupBy({ it.key.first }, { it.value }).mapValues { it.value.sum() }
                balance.forEach { (a, v) -> assertEquals("seed $seed account $a", v, mix[a] ?: 0L) }
            }
        }
    }

    @Test fun `the same entries give the same state twice`() {
        fun build(): Map<Pair<String, String>, Long> {
            val e = OriginEngine()
            listOf(
                OriginEntry("1", 1, 1, "A", 333, LedgerEntryKind.INCOME, null, "x", emptyList()),
                OriginEntry("2", 2, 2, "A", 333, LedgerEntryKind.INCOME, null, "y", emptyList()),
                OriginEntry("3", 3, 3, "A", -101, LedgerEntryKind.EXPENSE, null, null, listOf(OriginPart("p", 101))),
            ).forEach { e.apply(it) }
            return e.balances()
        }
        assertEquals(build(), build())
    }
}
