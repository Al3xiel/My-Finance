package com.al3xiel.my_finances.feature.reports.domain.rules

import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRow
import com.al3xiel.my_finances.feature.reports.domain.model.OriginEntry
import com.al3xiel.my_finances.feature.reports.domain.model.OriginPart
import com.al3xiel.my_finances.feature.reports.domain.model.OriginSourceIds

import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.LedgerEntryKind.ADJUSTMENT
import com.al3xiel.my_finances.domain.model.LedgerEntryKind.EXPENSE
import com.al3xiel.my_finances.domain.model.LedgerEntryKind.INCOME
import com.al3xiel.my_finances.domain.model.LedgerEntryKind.OPENING
import com.al3xiel.my_finances.domain.model.LedgerEntryKind.TRANSFER_IN
import com.al3xiel.my_finances.domain.model.LedgerEntryKind.TRANSFER_OUT
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginEngineTest {
    private val unknown = OriginSourceIds.UNKNOWN_ADJUSTMENT
    private val opening = OriginSourceIds.OPENING_BALANCE
    private var seq = 0L

    private fun entry(
        account: String, delta: Long, kind: LedgerEntryKind, source: String? = null, movement: String? = null,
        parts: List<OriginPart> = emptyList(), orderKey: Long? = null,
    ): OriginEntry {
        seq++
        return OriginEntry("e$seq", seq, orderKey ?: seq, account, delta, kind, movement, source, parts)
    }

    private fun run(vararg entries: OriginEntry) = OriginEngine().also { e -> entries.forEach { e.apply(it) } }

    @Test fun `an expense is taken in proportion from the positive sources`() {
        val engine = OriginEngine()
        engine.apply(entry("A", 700, INCOME, "s1"))
        engine.apply(entry("A", 300, INCOME, "s2"))
        val rows = engine.apply(entry("A", -100, EXPENSE, parts = listOf(OriginPart("p1", 100))))
        assertEquals(listOf(OriginAttributionRow("e3", "p1", "s1", 70), OriginAttributionRow("e3", "p1", "s2", 30)), rows)
        assertEquals(mapOf(("A" to "s1") to 630L, ("A" to "s2") to 270L), engine.balances())
    }

    @Test fun `rounding goes by largest remainder with ties to the smallest source id`() {
        val engine = OriginEngine()
        listOf("s3", "s1", "s2").forEach { engine.apply(entry("A", 100, INCOME, it)) }
        val rows = engine.apply(entry("A", -100, EXPENSE, parts = listOf(OriginPart("p", 100))))
        assertEquals(100L, rows.sumOf { it.amountMinor })
        assertEquals(mapOf("s1" to 34L, "s2" to 33L, "s3" to 33L), rows.associate { it.sourceId to it.amountMinor })
    }

    @Test fun `what exceeds the balance goes to the unknown source and may leave it negative`() {
        val engine = OriginEngine()
        engine.apply(entry("A", 100, INCOME, "s1"))
        val rows = engine.apply(entry("A", -130, EXPENSE, parts = listOf(OriginPart("p", 130))))
        assertEquals(mapOf("s1" to 100L, unknown to 30L), rows.associate { it.sourceId to it.amountMinor })
        assertEquals(mapOf(("A" to unknown) to -30L), engine.balances())
    }

    @Test fun `a negative unknown source does not take part in later deductions`() {
        val engine = OriginEngine()
        engine.apply(entry("A", 100, INCOME, "s1"))
        engine.apply(entry("A", -130, EXPENSE, parts = listOf(OriginPart("p", 130))))
        engine.apply(entry("A", 200, INCOME, "s2"))
        val rows = engine.apply(entry("A", -50, EXPENSE, parts = listOf(OriginPart("q", 50))))
        assertEquals(mapOf("s2" to 50L), rows.associate { it.sourceId to it.amountMinor })
        assertEquals(120L, engine.balances().values.sum())
    }

    @Test fun `opening and positive adjustment have their own sources`() {
        val engine = run(entry("A", 500, OPENING), entry("A", 40, ADJUSTMENT))
        assertEquals(mapOf(("A" to opening) to 500L, ("A" to unknown) to 40L), engine.balances())
    }

    @Test fun `a negative adjustment is attributed without a part`() {
        val engine = OriginEngine()
        engine.apply(entry("A", 100, INCOME, "s1"))
        val rows = engine.apply(entry("A", -40, ADJUSTMENT))
        assertEquals(listOf(OriginAttributionRow("e2", "", "s1", 40)), rows)
    }

    @Test fun `a transfer moves the mix to the destination and has no attributions`() {
        val engine = OriginEngine()
        engine.apply(entry("A", 700, INCOME, "s1"))
        engine.apply(entry("A", 300, INCOME, "s2"))
        val out = engine.apply(entry("A", -500, TRANSFER_OUT, movement = "m"))
        val inn = engine.apply(entry("B", 500, TRANSFER_IN, movement = "m"))
        assertEquals(emptyList<OriginAttributionRow>(), out + inn)
        assertEquals(
            mapOf(("A" to "s1") to 350L, ("A" to "s2") to 150L, ("B" to "s1") to 350L, ("B" to "s2") to 150L),
            engine.balances(),
        )
    }

    @Test fun `a transfer that exceeds the balance carries the shortfall as unknown`() {
        val engine = OriginEngine()
        engine.apply(entry("A", 100, INCOME, "s1"))
        engine.apply(entry("A", -150, TRANSFER_OUT, movement = "m"))
        engine.apply(entry("B", 150, TRANSFER_IN, movement = "m"))
        assertEquals(mapOf(("B" to "s1") to 100L, ("B" to unknown) to 50L, ("A" to unknown) to -50L), engine.balances())
    }

    @Test fun `a multi part expense is deducted part by part in order`() {
        val engine = OriginEngine()
        engine.apply(entry("A", 100, INCOME, "s1"))
        engine.apply(entry("A", 100, INCOME, "s2"))
        val rows = engine.apply(entry("A", -100, EXPENSE, parts = listOf(OriginPart("p1", 60), OriginPart("p2", 40))))
        assertEquals(listOf("p1", "p1", "p2", "p2"), rows.map { it.partId })
        assertEquals(100L, rows.sumOf { it.amountMinor })
        assertEquals(60L, rows.filter { it.partId == "p1" }.sumOf { it.amountMinor })
    }

    @Test fun `order key then sequence then id decide the processing order`() {
        val a = OriginEntry("a", 5, 2, "A", 1, INCOME, null, "s", emptyList())
        val b = OriginEntry("b", 3, 3, "A", 1, INCOME, null, "s", emptyList())
        val c = OriginEntry("c", 4, 2, "A", 1, INCOME, null, "s", emptyList())
        assertEquals(listOf("c", "a", "b"), listOf(a, b, c).sortedWith(OriginEngine.ORDER).map { it.id })
    }
}
