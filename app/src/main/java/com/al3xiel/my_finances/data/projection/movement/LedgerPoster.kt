package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.domain.model.MovementStatus
import javax.inject.Inject

/**
 * Registra y anula movimientos con su asiento en el libro, y deja los saldos de las cuentas afectadas al día. Lo usan
 * los proyectores de reembolsos y deudas, que escriben movimientos propios (el saldo siempre es la suma de asientos no
 * anulados).
 */
class LedgerPoster @Inject constructor(
    private val movements: MovementDao,
    private val ledger: LedgerDao,
) {
    suspend fun post(movement: MovementEntity, entry: LedgerEntryEntity) {
        movements.upsertMovement(movement)
        ledger.insertEntries(listOf(entry))
        refreshBalances(listOf(entry.accountId), entry.sequence)
    }

    /** `false` si el movimiento no existe o ya estaba anulado: no se toca nada. */
    suspend fun void(movementId: String, sequence: Long): Boolean {
        val current = movements.movement(movementId) ?: return false
        if (current.status == MovementStatus.VOIDED) return false
        val accounts = ledger.accountIdsOf(movementId)
        ledger.voidEntriesOf(movementId)
        movements.upsertMovement(current.copy(status = MovementStatus.VOIDED, lastSequence = sequence))
        refreshBalances(accounts, sequence)
        return true
    }

    /**
     * La secuencia del saldo nunca retrocede: en el replay cada proyector recorre todo el registro por separado y puede
     * llegar con eventos anteriores a los que otro ya anotó; así el resultado es el mismo que en vivo.
     */
    private suspend fun refreshBalances(accountIds: Collection<String>, sequence: Long) {
        accountIds.distinct().forEach {
            val latest = maxOf(sequence, ledger.lastSequenceOf(it) ?: 0L)
            ledger.upsertBalance(AccountBalanceEntity(it, ledger.balanceOf(it), latest))
        }
    }
}
