package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ExportedLedgerLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.LedgerSnapshot
import com.al3xiel.my_finances.feature.diagnostics.domain.model.OriginBalanceLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ReconciliationState
import com.al3xiel.my_finances.feature.diagnostics.domain.model.SnapshotCut
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.ProjectionSnapshotReader
import javax.inject.Inject

/**
 * Lee las proyecciones de la base que recibe. Con Hilt es la real (para exportar); el espacio de diagnóstico arma otra
 * instancia con su propia base (para comparar). El saldo acumulado de cada asiento suma solo los asientos no anulados.
 */
class RoomProjectionSnapshotReader @Inject constructor(private val database: AppDatabase) : ProjectionSnapshotReader {
    override suspend fun read(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean, cut: SnapshotCut?): PackageProjections {
        val ledgerDao = database.ledgerQueryDao()
        val raw = ledgerDao.entriesInOrder()
            .filter { accountId == null || it.accountId == accountId.value }
            .map { entry ->
                ExportedLedgerLine(
                    sequence = entry.sequence,
                    movementId = entry.movementId,
                    accountId = entry.accountId,
                    deltaMinor = entry.deltaMinor,
                    entryKind = entry.entryKind.name,
                    occurredOn = entry.occurredOn,
                    isVoided = entry.isVoided,
                    voidedSequence = entry.voidedSequence,
                    balanceMinor = 0L,
                )
            }
        val ledger = LedgerSnapshot.apply(raw, cut)
        val stored = ledgerDao.balances().filter { accountId == null || it.accountId == accountId.value }
        val balances = if (cut == null) {
            stored.associate { it.accountId to it.balanceMinor }
        } else {
            // Las mismas cuentas que tiene la base, con el saldo de los asientos del corte (0 si la cuenta no tiene ninguno).
            val derived = LedgerSnapshot.balances(ledger)
            stored.associate { it.accountId to (derived[it.accountId] ?: 0L) }
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
