package com.al3xiel.my_finances.data.projection.movement

/** DAOs en memoria: el proyector solo ve las interfaces, así que se prueba sin Room. */
class FakeMovementDao : MovementDao {
    val movements = linkedMapOf<String, MovementEntity>()
    val parts = linkedMapOf<String, MovementPartEntity>()
    val partTags = linkedSetOf<MovementPartTagEntity>()

    override suspend fun upsertMovement(movement: MovementEntity) { movements[movement.id] = movement }
    override suspend fun movement(id: String) = movements[id]

    override suspend fun insertParts(parts: List<MovementPartEntity>) { parts.forEach { this.parts[it.id] = it } }
    override suspend fun insertPartTags(tags: List<MovementPartTagEntity>) { partTags += tags }

    override suspend fun deletePartTagsOf(movementId: String) {
        val ids = parts.values.filter { it.movementId == movementId }.map { it.id }.toSet()
        partTags.removeAll { it.partId in ids }
    }

    override suspend fun deletePartsOf(movementId: String) {
        parts.values.removeAll { it.movementId == movementId }
    }

    override suspend fun clearMovements() = movements.clear()
    override suspend fun clearParts() = parts.clear()
    override suspend fun clearPartTags() = partTags.clear()
}

class FakeLedgerDao : LedgerDao {
    val entries = linkedMapOf<String, LedgerEntryEntity>()
    val balances = linkedMapOf<String, AccountBalanceEntity>()

    override suspend fun insertEntries(entries: List<LedgerEntryEntity>) {
        entries.forEach {
            check(it.id !in this.entries) { "duplicate ledger id ${it.id}" }
            this.entries[it.id] = it
        }
    }

    override suspend fun accountIdsOf(movementId: String) =
        entries.values.filter { it.movementId == movementId }.map { it.accountId }.distinct()

    override suspend fun voidEntriesOf(movementId: String) {
        entries.replaceAll { _, e -> if (e.movementId == movementId) e.copy(isVoided = true) else e }
    }

    override suspend fun balanceOf(accountId: String) =
        entries.values.filter { it.accountId == accountId && !it.isVoided }.sumOf { it.deltaMinor }

    override suspend fun upsertBalance(balance: AccountBalanceEntity) { balances[balance.accountId] = balance }

    override suspend fun clearEntries() = entries.clear()
    override suspend fun clearBalances() = balances.clear()
}
