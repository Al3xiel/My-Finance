package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.security.KeyWrapper
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.diagnostics.data.PackageManifest
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceFiles
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceKeys
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceMeta
import com.al3xiel.my_finances.feature.diagnostics.domain.model.LedgerLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.OriginBalanceLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ReconciliationState
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspaceReplayer
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildDivergenceReportUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class XorWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x21).toByte() }
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
        override fun deleteKey() = Unit
    }

    private class FakeReplayer(var result: PackageProjections) : WorkspaceReplayer {
        var asked: Triple<AccountId?, ReconciliationId?, Boolean>? = null
        override suspend fun replay(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean): PackageProjections {
            asked = Triple(accountId, reconciliationId, includeOrigin)
            return result
        }
    }

    private fun line(sequence: Long, delta: Long, balance: Long, account: String = "a1") =
        LedgerLine(sequence, "m$sequence", account, delta, "INCOME", "2026-10-25", false, null, balance)

    private val exportedLedger = listOf(line(3, 1_000, 1_000), line(4, -300, 700), line(5, 200, 900))

    private fun projections(
        ledger: List<LedgerLine> = exportedLedger,
        balance: Long = 900,
        computed: Long = 900,
        versions: Map<String, Int> = mapOf("movement" to 2, "origin" to 1),
        origin: List<OriginBalanceLine>? = null,
    ) = PackageProjections(
        mapOf("a1" to balance), ledger,
        ReconciliationState("r1", "a1", CurrencyCode.PEN, 1_100, computed, 5, "OPEN", null, null, false),
        versions, origin,
    )

    private fun setUp(exported: PackageProjections, scope: String = "RECONCILIATION", recalculated: PackageProjections = exported): Pair<BuildDivergenceReportUseCase, FakeReplayer> {
        val workspace = DiagnosticWorkspace(
            WorkspaceFiles(folder.newFolder()), WorkspaceKeys(XorWrapper(), File(folder.root, "k"), SecureRandom()),
        ) { error("not opened") }
        workspace.save(
            WorkspaceMeta(
                PackageManifest(1, "x1", "1.0", "14", "2026-11-02T00:00:00Z", scope, "r1", true, 5, 3, exported.projectorVersions, emptyList(), "c"),
                exported, "hash",
            ),
        )
        val replayer = FakeReplayer(recalculated)
        return BuildDivergenceReportUseCase(workspace, replayer) to replayer
    }

    @Test
    fun noDivergenceWhenTheReplayMatchesWhatWasExported() = runTest {
        val (report, _) = setUp(projections())
        val result = report() as ReportOutcome.Ready
        assertFalse(result.report.diverges)
        assertNull(result.report.firstDivergentSequence)
        assertTrue(result.report.divergentLedger.isEmpty())
    }

    @Test
    fun aDifferentBalanceIsReportedWithTheAccountAndBothAmounts() = runTest {
        val (report, _) = setUp(projections(), recalculated = projections(balance = 1_000))
        val result = (report() as ReportOutcome.Ready).report
        assertTrue(result.diverges)
        val balance = result.balances.single { it.diverges }
        assertEquals(Triple("a1", 900L, 1_000L), Triple(balance.accountId, balance.exportedMinor, balance.recalculatedMinor))
    }

    @Test
    fun theFirstDivergentSequenceIsTheEarliestDifferingEntry() = runTest {
        val changed = listOf(line(3, 1_000, 1_000), line(4, -350, 650), line(5, 250, 900))
        val (report, _) = setUp(projections(), recalculated = projections(ledger = changed))
        val result = (report() as ReportOutcome.Ready).report
        assertEquals(4L, result.firstDivergentSequence)
        assertEquals(listOf(4L, 5L), result.divergentLedger.map { it.sequence }.distinct().sorted().filter { it >= 4 }.take(2))
        val side = result.divergentLedger.first { it.sequence == 4L }
        assertEquals(-300L, side.exported?.deltaMinor)
        assertEquals(-350L, side.recalculated?.deltaMinor)
    }

    @Test
    fun anEntryOnlyOneSideHasIsReported() = runTest {
        val (report, _) = setUp(projections(), recalculated = projections(ledger = exportedLedger.take(2)))
        val result = (report() as ReportOutcome.Ready).report
        val row = result.divergentLedger.single()
        assertEquals(5L, row.sequence)
        assertNull(row.recalculated)
    }

    @Test
    fun differentProjectorVersionsAreShown() = runTest {
        val (report, _) = setUp(projections(), recalculated = projections(versions = mapOf("movement" to 3, "origin" to 1)))
        val result = (report() as ReportOutcome.Ready).report
        assertEquals(listOf("movement"), result.versionDifferences.map { it.projector })
        assertEquals(2 to 3, result.versionDifferences.single().exported to result.versionDifferences.single().current)
    }

    @Test
    fun theReconciliationComputedAmountIsCompared() = runTest {
        val (report, _) = setUp(projections(), recalculated = projections(computed = 800))
        val reconciliation = (report() as ReportOutcome.Ready).report.reconciliation
        assertEquals(900L to 800L, reconciliation?.exportedComputedMinor to reconciliation?.recalculatedComputedMinor)
        assertTrue(reconciliation?.diverges == true)
    }

    @Test
    fun originBalancesAreSecondaryAndOnlyComparedWhenExported() = runTest {
        val exported = projections(origin = listOf(OriginBalanceLine("a1", "s1", 900)))
        val different = projections(origin = listOf(OriginBalanceLine("a1", "s1", 800)))
        val (withOrigin, replayer) = setUp(exported, scope = "FULL", recalculated = different)
        val result = (withOrigin() as ReportOutcome.Ready).report
        assertEquals(true, replayer.asked?.third)
        assertEquals(1, result.originDifferences?.size)
        assertFalse("origin never makes the main verdict diverge", result.diverges)

        val (without, replayer2) = setUp(projections())
        assertNull((without() as ReportOutcome.Ready).report.originDifferences)
        assertEquals(false, replayer2.asked?.third)
    }

    @Test
    fun theReplayIsAskedForTheExportedAccountAndReconciliation() = runTest {
        val (report, replayer) = setUp(projections())
        report()
        assertEquals(Triple(AccountId("a1"), ReconciliationId("r1"), false), replayer.asked)
    }

    @Test
    fun withoutAPackageThereIsNothingToReport() = runTest {
        val workspace = DiagnosticWorkspace(WorkspaceFiles(folder.newFolder()), WorkspaceKeys(XorWrapper(), File(folder.root, "k2"), SecureRandom())) { error("no") }
        assertEquals(ReportOutcome.NoPackage, BuildDivergenceReportUseCase(workspace, FakeReplayer(projections()))())
    }
}
