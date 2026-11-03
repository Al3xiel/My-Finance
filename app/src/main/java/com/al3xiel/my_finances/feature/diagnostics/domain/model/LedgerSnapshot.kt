package com.al3xiel.my_finances.feature.diagnostics.domain.model

/**
 * Los asientos de un [SnapshotCut], con su saldo acumulado: lo que daría reproducir solo los eventos elegidos. Un asiento
 * anulado por un evento que no viaja cuenta como no anulado. Uno anulado sin `voidedSequence` (anulado antes de que existiera la
 * columna) se mantiene anulado: no se sabe quién lo anuló y el replay lo deja así.
 */
object LedgerSnapshot {
    /** `lines` van en el orden en que se aplicaron; su `balanceMinor` de entrada se ignora. Con `cut` nulo entran todos. */
    fun apply(lines: List<ExportedLedgerLine>, cut: SnapshotCut?): List<ExportedLedgerLine> {
        val running = HashMap<String, Long>()
        return lines
            .filter { cut == null || it.sequence in cut.includedSequences }
            .map { line ->
                val voided = line.isVoided && (cut == null || line.voidedSequence == null || line.voidedSequence in cut.includedSequences)
                val balance = (running[line.accountId] ?: 0L) + if (voided) 0L else line.deltaMinor
                running[line.accountId] = balance
                line.copy(isVoided = voided, voidedSequence = line.voidedSequence.takeIf { voided }, balanceMinor = balance)
            }
    }

    fun balances(lines: List<ExportedLedgerLine>): Map<String, Long> = lines.groupBy { it.accountId }.mapValues { it.value.last().balanceMinor }
}
