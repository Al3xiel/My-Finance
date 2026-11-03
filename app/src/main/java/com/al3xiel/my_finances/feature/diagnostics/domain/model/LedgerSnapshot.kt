package com.al3xiel.my_finances.feature.diagnostics.domain.model

/**
 * Los asientos de un [SnapshotCut], con su saldo acumulado: lo que daría reproducir solo los eventos elegidos. Un asiento
 * anulado por un evento posterior al corte cuenta como no anulado, porque ese evento no viaja en el paquete.
 */
object LedgerSnapshot {
    /** `lines` van en el orden en que se aplicaron; su `balanceMinor` de entrada se ignora. */
    fun apply(lines: List<ExportedLedgerLine>, cut: SnapshotCut): List<ExportedLedgerLine> {
        val running = HashMap<String, Long>()
        return lines
            .filter { it.sequence <= cut.upToSequence || it.movementId in cut.alsoIncludedMovements }
            .map { line ->
                val voided = line.isVoided && (line.movementId in cut.alsoIncludedMovements || (line.voidedSequence ?: Long.MAX_VALUE) <= cut.upToSequence)
                val balance = (running[line.accountId] ?: 0L) + if (voided) 0L else line.deltaMinor
                running[line.accountId] = balance
                line.copy(isVoided = voided, voidedSequence = line.voidedSequence.takeIf { voided }, balanceMinor = balance)
            }
    }

    fun balances(lines: List<ExportedLedgerLine>): Map<String, Long> = lines.groupBy { it.accountId }.mapValues { it.value.last().balanceMinor }
}
