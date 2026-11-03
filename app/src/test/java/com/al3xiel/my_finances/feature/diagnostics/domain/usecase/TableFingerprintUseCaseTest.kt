package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.feature.diagnostics.domain.model.TableFingerprint
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.TableFingerprintSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableFingerprintUseCaseTest {
    private class Source(var tables: List<TableFingerprint>) : TableFingerprintSource {
        override suspend fun fingerprints() = tables
    }

    private val before = listOf(TableFingerprint("account_balances", 2, "aa"), TableFingerprint("ledger_entries", 5, "bb"))

    @Test
    fun takesTheSnapshotInTableOrder() = runTest {
        val source = Source(before.reversed())
        assertEquals(before.map { it.table }, TableFingerprintUseCase(source).snapshot().map { it.table })
    }

    @Test
    fun identicalSnapshotsHaveNoChanges() {
        assertTrue(TableFingerprintUseCase(Source(before)).compare(before, before).none { it.changed })
    }

    @Test
    fun aChangedHashOrCountIsReportedPerTable() {
        val after = listOf(TableFingerprint("account_balances", 2, "zz"), TableFingerprint("ledger_entries", 6, "bb"))
        val changes = TableFingerprintUseCase(Source(after)).compare(before, after)
        assertEquals(listOf("account_balances", "ledger_entries"), changes.filter { it.changed }.map { it.table })
        assertEquals(5 to 6, changes.single { it.table == "ledger_entries" }.let { it.beforeRows to it.afterRows })
    }

    @Test
    fun aTableThatAppearsOrDisappearsCounts() {
        val after = listOf(TableFingerprint("account_balances", 2, "aa"), TableFingerprint("new_table", 1, "cc"))
        val changes = TableFingerprintUseCase(Source(after)).compare(before, after)
        assertEquals(listOf("ledger_entries", "new_table"), changes.filter { it.changed }.map { it.table })
        assertEquals(null, changes.single { it.table == "ledger_entries" }.afterRows)
    }
}
