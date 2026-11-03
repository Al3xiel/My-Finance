package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticWorkspaceStore
import com.al3xiel.my_finances.feature.diagnostics.domain.model.BalanceComparison
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DivergenceReport
import com.al3xiel.my_finances.feature.diagnostics.domain.model.LedgerComparison
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ExportedLedgerLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.OriginDifference
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ReconciliationComparison
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ReconciliationState
import com.al3xiel.my_finances.feature.diagnostics.domain.model.VersionDifference
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspaceReplayer
import javax.inject.Inject

sealed interface ReportOutcome {
    data class Ready(val report: DivergenceReport) : ReportOutcome
    data object NoPackage : ReportOutcome
}

/** Re-proyecta el espacio y compara con `projections.json`: saldo, asientos, primer `sequence` que difiere y versiones. */
class BuildDivergenceReportUseCase @Inject constructor(
    private val workspace: DiagnosticWorkspaceStore,
    private val replayer: WorkspaceReplayer,
) {
    suspend operator fun invoke(): ReportOutcome {
        val meta = workspace.meta() ?: return ReportOutcome.NoPackage
        val exported = meta.projections
        val full = meta.manifest.scope == DiagnosticScope.FULL.name
        val account = if (full) null else (exported.reconciliation?.accountId ?: exported.accountBalances.keys.singleOrNull())?.let { AccountId(it) }
        val reconciliation = meta.manifest.reconciliationId?.let { ReconciliationId(it) }
        val now = replayer.replay(account, reconciliation, includeOrigin = exported.originBalances != null)

        val balances = (exported.accountBalances.keys + now.accountBalances.keys).sorted()
            .map { BalanceComparison(it, exported.accountBalances[it], now.accountBalances[it]) }
        val ledger = compareLedgers(exported.ledger, now.ledger)
        return ReportOutcome.Ready(
            DivergenceReport(
                exportId = meta.manifest.exportId,
                balances = balances,
                firstDivergentSequence = ledger.minOfOrNull { it.sequence },
                divergentLedger = ledger,
                versionDifferences = (exported.projectorVersions.keys + now.projectorVersions.keys).sorted()
                    .filter { exported.projectorVersions[it] != now.projectorVersions[it] }
                    .map { VersionDifference(it, exported.projectorVersions[it], now.projectorVersions[it]) },
                reconciliation = exported.reconciliation?.let {
                    ReconciliationComparison(it.reconciliationId, it.realMinor, it.snapshotComputedMinor, now.reconciliation?.snapshotComputedMinor, differingFields(it, now.reconciliation))
                },
                originDifferences = exported.originBalances?.let { origin(it, now.originBalances.orEmpty()) },
            ),
        )
    }

    /** Los campos del estado de la conciliación que difieren; vacío = coinciden. Si falta del lado recalculado, difiere todo. */
    private fun differingFields(exported: ReconciliationState, now: ReconciliationState?): List<String> {
        if (now == null) return listOf("missing")
        return buildList {
            if (exported.realMinor != now.realMinor) add("realMinor")
            if (exported.snapshotComputedMinor != now.snapshotComputedMinor) add("snapshotComputedMinor")
            if (exported.checkedSequence != now.checkedSequence) add("checkedSequence")
            if (exported.status != now.status) add("status")
            if (exported.cause != now.cause) add("cause")
            if (exported.adjustmentMinor != now.adjustmentMinor) add("adjustmentMinor")
            if (exported.currency != now.currency) add("currency")
        }
    }

    /** Agrupa por (sequence, cuenta) y compara en orden; lo que falta de un lado queda como `null`. */
    private fun compareLedgers(exported: List<ExportedLedgerLine>, now: List<ExportedLedgerLine>): List<LedgerComparison> {
        val before = exported.groupBy { it.sequence to it.accountId }
        val after = now.groupBy { it.sequence to it.accountId }
        return (before.keys + after.keys).sortedWith(compareBy({ it.first }, { it.second })).flatMap { key ->
            val left = before[key].orEmpty()
            val right = after[key].orEmpty()
            (0 until maxOf(left.size, right.size)).mapNotNull { i ->
                val a = left.getOrNull(i)
                val b = right.getOrNull(i)
                if (a == b) null else LedgerComparison(key.first, key.second, a, b)
            }
        }
    }

    private fun origin(
        exported: List<com.al3xiel.my_finances.feature.diagnostics.domain.model.OriginBalanceLine>,
        now: List<com.al3xiel.my_finances.feature.diagnostics.domain.model.OriginBalanceLine>,
    ): List<OriginDifference> {
        val before = exported.associate { (it.accountId to it.sourceId) to it.amountMinor }
        val after = now.associate { (it.accountId to it.sourceId) to it.amountMinor }
        return (before.keys + after.keys).sortedWith(compareBy({ it.first }, { it.second }))
            .filter { before[it] != after[it] }
            .map { OriginDifference(it.first, it.second, before[it], after[it]) }
    }
}
