package com.al3xiel.my_finances.feature.reports.domain.rules

import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRecord
import com.al3xiel.my_finances.feature.reports.domain.model.OriginBalanceRow
import com.al3xiel.my_finances.feature.reports.domain.model.OriginSourceIds
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNode
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginCalculatorTest {
    private fun spend(source: String, account: String, amount: Long, category: String? = "food", parent: String? = null, kind: LedgerEntryKind = LedgerEntryKind.EXPENSE) =
        OriginAttributionRecord(source, account, kind, category, parent, amount)

    @Test
    fun compositionPercentagesSumTo10000ByLargestRemainder() {
        val rows = listOf(OriginBalanceRow("a", "s1", 1), OriginBalanceRow("a", "s2", 1), OriginBalanceRow("a", "s3", 1))
        val account = OriginCalculator.composition(rows).single()
        assertEquals(10_000, account.slices.sumOf { it.basisPoints })
        assertEquals(listOf(3334, 3333, 3333), account.slices.map { it.basisPoints }) // el desempate va al id menor
        assertEquals(3L, account.balanceMinor)
    }

    @Test
    fun negativeUnknownAdjustmentGetsNoShareAndRaisesTheShortfallFlag() {
        val rows = listOf(
            OriginBalanceRow("a", "s1", 1000),
            OriginBalanceRow("a", OriginSourceIds.UNKNOWN_ADJUSTMENT, -200),
        )
        val account = OriginCalculator.composition(rows).single()
        assertTrue(account.hasShortfall)
        assertEquals(800L, account.balanceMinor)
        assertEquals(10_000, account.slices.first { it.sourceId == "s1" }.basisPoints)
        assertEquals(0, account.slices.first { it.sourceId == OriginSourceIds.UNKNOWN_ADJUSTMENT }.basisPoints)
    }

    @Test
    fun accountWithoutShortfallDoesNotWarn() {
        assertFalse(OriginCalculator.composition(listOf(OriginBalanceRow("a", "s1", 5))).single().hasShortfall)
    }

    @Test
    fun accountsAreOrderedByIdAndSlicesByAmountDescending() {
        val rows = listOf(OriginBalanceRow("b", "s1", 1), OriginBalanceRow("a", "s1", 1), OriginBalanceRow("a", "s2", 9))
        val result = OriginCalculator.composition(rows)
        assertEquals(listOf("a", "b"), result.map { it.accountId })
        assertEquals(listOf("s2", "s1"), result.first().slices.map { it.sourceId })
    }

    @Test
    fun spendOfASourceGroupsByCategoryAndAccount() {
        val records = listOf(
            spend("s1", "a", 30, "milk"), spend("s1", "a", 20, "milk"), spend("s1", "b", 10, "milk"),
            spend("s1", "a", 5, "bus"), spend("s2", "a", 99, "milk"),
        )
        val rows = OriginCalculator.spendOf("s1", records)
        assertEquals(listOf(50L, 10L, 5L), rows.map { it.amountMinor })
        assertEquals(listOf("a" to "milk", "b" to "milk", "a" to "bus"), rows.map { it.accountId to it.categoryId })
    }

    @Test
    fun sankeyLinksSourcesToAccountsAndAccountsToTopLevelCategories() {
        val records = listOf(
            spend("s1", "a", 30, "milk", parent = "food"),
            spend("s1", "a", 20, "bread", parent = "food"),
            spend("s2", "a", 10, "bus"),
        )
        val links = OriginCalculator.sankey(records)
        val sourceToAccount = links.filter { it.from.kind == SankeyNodeKind.SOURCE }
        assertEquals(mapOf("s1" to 50L, "s2" to 10L), sourceToAccount.associate { it.from.id to it.amountMinor })
        val toCategory = links.filter { it.to.kind == SankeyNodeKind.CATEGORY }.associate { it.to.id to it.amountMinor }
        assertEquals(mapOf("food" to 50L, "bus" to 10L), toCategory)
        assertEquals(60L, sourceToAccount.sumOf { it.amountMinor })
    }

    @Test
    fun loansAndAdjustmentsGoToTheirOwnNodes() {
        val records = listOf(
            spend("s1", "a", 40, category = null, kind = LedgerEntryKind.LOAN_OUT),
            spend("s1", "a", 7, category = null, kind = LedgerEntryKind.ADJUSTMENT),
        )
        val targets = OriginCalculator.sankey(records).filter { it.from.kind == SankeyNodeKind.ACCOUNT }.associate { it.to.kind to it.amountMinor }
        assertEquals(mapOf(SankeyNodeKind.LOANS to 40L, SankeyNodeKind.UNIDENTIFIED to 7L), targets)
    }

    @Test
    fun sankeyFlowIsConservedThroughEveryAccount() {
        val records = listOf(spend("s1", "a", 30), spend("s2", "a", 12, "bus"), spend("s1", "b", 8))
        val links = OriginCalculator.sankey(records)
        listOf("a", "b").forEach { account ->
            val node = SankeyNode(SankeyNodeKind.ACCOUNT, account)
            assertEquals(links.filter { it.to == node }.sumOf { it.amountMinor }, links.filter { it.from == node }.sumOf { it.amountMinor })
        }
    }

    @Test
    fun zeroAmountRecordsAreIgnored() {
        assertTrue(OriginCalculator.sankey(listOf(spend("s1", "a", 0))).isEmpty())
    }
}
