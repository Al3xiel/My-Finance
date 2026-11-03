package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.diagnostics.domain.model.LedgerLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.OriginBalanceLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ReconciliationState
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.ProjectionSnapshotReader
import javax.inject.Inject

/**
 * Lee las proyecciones de la base que recibe. Con Hilt es la real (para exportar); el espacio de diagnóstico arma otra
 * instancia con su propia base (para comparar). El saldo acumulado de cada asiento suma solo los asientos no anulados.
 */
class RoomProjectionSnapshotReader @Inject constructor(private val database: AppDatabase) : ProjectionSnapshotReader {
    override suspend fun read(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean): PackageProjections {
        val ledgerDao = database.ledgerQueryDao()
        val balances = ledgerDao.balances()
            .filter { accountId == null || it.accountId == accountId.value }
            .associate { it.accountId to it.balanceMinor }
        val running = HashMap<String, Long>()
        val ledger = ledgerDao.entriesInOrder()
            .filter { accountId == null || it.accountId == accountId.value }
            .map { entry ->
                val balance = (running[entry.accountId] ?: 0L) + if (entry.isVoided) 0L else entry.deltaMinor
                running[entry.accountId] = balance
                LedgerLine(
                    sequence = entry.sequence,
                    movementId = entry.movementId,
                    accountId = entry.accountId,
                    deltaMinor = entry.deltaMinor,
                    entryKind = entry.entryKind.name,
                    occurredOn = entry.occurredOn,
                    isVoided = entry.isVoided,
                    voidedSequence = entry.voidedSequence,
                    balanceMinor = balance,
                )
            }
        val reconciliation = reconciliationId?.let { database.reconciliationQueryDao().reconciliation(it.value) }?.let {
            ReconciliationState(
                reconciliationId = it.id,
                accountId = it.accountId,
                currency = it.currency,
                realMinor = it.realMinor,
                snapshotComputedMinor = it.snapshotComputedMinor,
                checkedSequence = it.checkedSequence,
                status = it.status.name,
                cause = it.cause?.name,
                adjustmentMinor = it.adjustmentMinor,
                needsReview = it.needsReview,
            )
        }
        val origin = if (includeOrigin) {
            database.originDao().balances().map { OriginBalanceLine(it.accountId, it.incomeSourceId, it.amountMinor) }
        } else {
            null
        }
        return PackageProjections(
            accountBalances = balances,
            ledger = ledger,
            reconciliation = reconciliation,
            projectorVersions = database.projectionStateDao().all().associate { it.projectorName to it.projectorVersion },
            originBalances = origin,
        )
    }
}
