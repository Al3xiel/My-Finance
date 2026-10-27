package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import javax.inject.Inject

/**
 * Movimientos, partes, asientos del libro y saldos. El saldo de una cuenta es siempre la suma de sus asientos
 * no anulados: cada cambio recalcula los saldos de las cuentas afectadas.
 *
 * Una corrección o anulación que no aplica (movimiento inexistente, ya anulado, transferencia o de otro tipo) se
 * ignora a propósito: los casos de uso ya las rechazan, y si una llegara al registro el replay debe dar el mismo
 * resultado que la proyección en vivo en vez de fallar.
 */
class MovementProjector @Inject constructor(
    private val movements: MovementDao,
    private val ledger: LedgerDao,
) : EventProjector {
    override val name = "movement"
    override val version = 1

    override suspend fun apply(event: StoredEvent) {
        val occurredOn = event.occurredOn.toString()
        when (val e = event.event) {
            is AccountCreated ->
                post(event, entry(event, 0, null, e.accountId, e.openingBalance, LedgerEntryKind.OPENING, occurredOn))

            is IncomeRegistered -> {
                val snapshot = MovementSnapshot.Income(e.accountId, e.amount, e.incomeSourceId, e.note)
                post(event, saveSnapshot(event, e.movementId.value, snapshot, revision = 1))
            }

            is ExpenseRegistered -> {
                val snapshot = MovementSnapshot.Expense(e.accountId, e.total, e.parts, e.note)
                post(event, saveSnapshot(event, e.movementId.value, snapshot, revision = 1))
            }

            is TransferRegistered -> post(event, saveTransfer(event, e, occurredOn))

            is MovementCorrected -> correct(event, e)

            is MovementVoided -> void(event, e)

            else -> Unit
        }
    }

    override suspend fun reset() {
        movements.clearPartTags()
        movements.clearParts()
        movements.clearMovements()
        ledger.clearEntries()
        ledger.clearBalances()
    }

    private suspend fun correct(event: StoredEvent, e: MovementCorrected) {
        val id = e.movementId.value
        val current = movements.movement(id) ?: return
        if (current.status == MovementStatus.VOIDED) return
        val sameType = when (e.snapshot) {
            is MovementSnapshot.Income -> current.type == MovementType.INCOME
            is MovementSnapshot.Expense -> current.type == MovementType.EXPENSE
        }
        if (!sameType) return // transferencias no se corrigen (D12) y el tipo no cambia (D15)

        val previousAccounts = ledger.accountIdsOf(id)
        ledger.voidEntriesOf(id)
        movements.deletePartTagsOf(id)
        movements.deletePartsOf(id)
        post(event, saveSnapshot(event, id, e.snapshot, current.revision + 1), previousAccounts)
    }

    private suspend fun void(event: StoredEvent, e: MovementVoided) {
        val id = e.movementId.value
        val current = movements.movement(id) ?: return
        if (current.status == MovementStatus.VOIDED || current.type == MovementType.TRANSFER) return

        val accounts = ledger.accountIdsOf(id)
        ledger.voidEntriesOf(id)
        movements.upsertMovement(current.copy(status = MovementStatus.VOIDED, lastSequence = event.sequence))
        refreshBalances(accounts, event.sequence)
    }

    private suspend fun saveSnapshot(
        event: StoredEvent,
        movementId: String,
        snapshot: MovementSnapshot,
        revision: Int,
    ): List<LedgerEntryEntity> {
        val occurredOn = event.occurredOn.toString()
        return when (snapshot) {
            is MovementSnapshot.Income -> {
                movements.upsertMovement(
                    movement(event, movementId, MovementType.INCOME, snapshot.accountId, null, snapshot.amount, snapshot.incomeSourceId.value, snapshot.note, revision),
                )
                listOf(entry(event, 0, movementId, snapshot.accountId, snapshot.amount, LedgerEntryKind.INCOME, occurredOn))
            }

            is MovementSnapshot.Expense -> {
                movements.upsertMovement(
                    movement(event, movementId, MovementType.EXPENSE, snapshot.accountId, null, snapshot.total, null, snapshot.note, revision),
                )
                saveParts(movementId, snapshot.parts)
                listOf(entry(event, 0, movementId, snapshot.accountId, snapshot.total, LedgerEntryKind.EXPENSE, occurredOn, negate = true))
            }
        }
    }

    private suspend fun saveTransfer(event: StoredEvent, e: TransferRegistered, occurredOn: String): List<LedgerEntryEntity> {
        val id = e.movementId.value
        movements.upsertMovement(
            movement(event, id, MovementType.TRANSFER, e.fromAccountId, e.toAccountId, e.amount, null, e.note, revision = 1),
        )
        val entries = mutableListOf(
            entry(event, 0, id, e.fromAccountId, e.amount, LedgerEntryKind.TRANSFER_OUT, occurredOn, negate = true),
            entry(event, 1, id, e.toAccountId, e.amount, LedgerEntryKind.TRANSFER_IN, occurredOn),
        )
        e.fee?.let { fee ->
            movements.insertParts(
                listOf(MovementPartEntity("$id:fee", id, fee.amount.amountMinor, fee.categoryId.value, PartKind.FEE)),
            )
            entries += entry(event, 2, id, e.fromAccountId, fee.amount, LedgerEntryKind.FEE, occurredOn, negate = true)
        }
        return entries
    }

    private suspend fun saveParts(movementId: String, parts: List<ExpensePartData>) {
        movements.insertParts(
            parts.map { MovementPartEntity(it.partId.value, movementId, it.amount.amountMinor, it.categoryId.value, PartKind.NORMAL) },
        )
        movements.insertPartTags(
            parts.flatMap { part -> part.tagIds.map { MovementPartTagEntity(part.partId.value, it.value) } },
        )
    }

    private fun movement(
        event: StoredEvent,
        id: String,
        type: MovementType,
        account: AccountId,
        counter: AccountId?,
        amount: Money,
        incomeSourceId: String?,
        note: String?,
        revision: Int,
    ) = MovementEntity(
        id = id,
        type = type,
        accountId = account.value,
        counterAccountId = counter?.value,
        amountMinor = amount.amountMinor,
        currency = amount.currency,
        incomeSourceId = incomeSourceId,
        personId = null,
        note = note,
        occurredOn = event.occurredOn.toString(),
        status = MovementStatus.ACTIVE,
        revision = revision,
        lastSequence = event.sequence,
    )

    private fun entry(
        event: StoredEvent,
        index: Int,
        movementId: String?,
        account: AccountId,
        amount: Money,
        kind: LedgerEntryKind,
        occurredOn: String,
        negate: Boolean = false,
    ) = LedgerEntryEntity(
        id = "${event.sequence}-$index",
        sequence = event.sequence,
        movementId = movementId,
        accountId = account.value,
        deltaMinor = if (negate) -amount.amountMinor else amount.amountMinor,
        entryKind = kind,
        occurredOn = occurredOn,
        isVoided = false,
    )

    private suspend fun post(event: StoredEvent, vararg entries: LedgerEntryEntity) = post(event, entries.toList())

    private suspend fun post(event: StoredEvent, entries: List<LedgerEntryEntity>, alsoRefresh: List<String> = emptyList()) {
        ledger.insertEntries(entries)
        refreshBalances((entries.map { it.accountId } + alsoRefresh).distinct(), event.sequence)
    }

    private suspend fun refreshBalances(accountIds: Collection<String>, sequence: Long) {
        accountIds.forEach { ledger.upsertBalance(AccountBalanceEntity(it, ledger.balanceOf(it), sequence)) }
    }
}
