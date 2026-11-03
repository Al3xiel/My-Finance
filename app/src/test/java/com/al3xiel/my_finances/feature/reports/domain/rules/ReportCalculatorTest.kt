package com.al3xiel.my_finances.feature.reports.domain.rules

import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.reports.domain.model.LedgerRow
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCalculatorTest {
    private fun part(id: String, amount: Long, cat: String = "food", parent: String? = null, pool: Boolean = false,
                     reimbursed: Long = 0, tags: List<String> = emptyList(), account: String = "acc") =
        PartRow(id, account, cat, parent, amount, pool, reimbursed, tags)

    @Test fun `summary separates own spend from spend on behalf of others`() {
        val s = ReportCalculator.summary(
            parts = listOf(part("p1", 100), part("p2", 120, pool = true, reimbursed = 20)),
            ledger = listOf(LedgerRow("acc", LedgerEntryKind.INCOME, 500), LedgerRow("acc", LedgerEntryKind.ADJUSTMENT, -30)),
        )
        assertEquals(500L, s.income)
        assertEquals(200L, s.ownSpend) // 100 + pending 100
        assertEquals(120L, s.others.total)
        assertEquals(20L, s.others.reimbursed)
        assertEquals(100L, s.others.pending)
        assertEquals(-30L, s.unidentified)
    }

    @Test fun `reimbursement dated after the period leaves the spend fully own`() {
        val s = ReportCalculator.summary(listOf(part("p", 120, pool = true, reimbursed = 0)), emptyList())
        assertEquals(120L, s.ownSpend)
    }

    @Test fun `subcategories add up to the parent and shares total 100 percent`() {
        val rows = ReportCalculator.byCategory(listOf(
            part("a", 60, cat = "bus", parent = "transport"), part("b", 40, cat = "taxi", parent = "transport"),
            part("c", 100, cat = "food"),
        ))
        assertEquals(10_000, rows.sumOf { it.basisPoints })
        val t = rows.first { it.categoryId == "transport" }
        assertEquals(100L, t.amount)
        assertEquals(listOf("bus", "taxi"), t.children.map { it.categoryId })
    }

    @Test fun `tags do not add up and a part counts in each of its tags`() {
        val rows = ReportCalculator.byTag(listOf(part("a", 100, tags = listOf("t1", "t2")), part("b", 50, cat = "x", tags = listOf("t1"))))
        assertEquals(150L, rows.first { it.tagId == "t1" }.amount)
        assertEquals(100L, rows.first { it.tagId == "t2" }.amount)
        assertEquals(listOf("food", "x"), rows.first { it.tagId == "t1" }.byCategory.map { it.first })
    }

    @Test fun `by account classifies entries and nets transfers apart`() {
        val rows = ReportCalculator.byAccount(listOf(
            LedgerRow("a", LedgerEntryKind.INCOME, 300), LedgerRow("a", LedgerEntryKind.EXPENSE, -100),
            LedgerRow("a", LedgerEntryKind.FEE, -5), LedgerRow("a", LedgerEntryKind.TRANSFER_OUT, -50),
            LedgerRow("a", LedgerEntryKind.TRANSFER_IN, 20), LedgerRow("a", LedgerEntryKind.ADJUSTMENT, 7),
            LedgerRow("a", LedgerEntryKind.OPENING, 999),
        ))
        val a = rows.single()
        assertEquals(307L, a.inflow)
        assertEquals(105L, a.outflow)
        assertEquals(-30L, a.transfersNet)
    }
}
