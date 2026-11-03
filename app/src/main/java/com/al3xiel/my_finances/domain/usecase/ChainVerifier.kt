package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.EventHasher

/**
 * Verifica la cadena fila a fila sobre los campos crudos (sin decodificar el payload): `sequence` desde 1 sin saltos,
 * `previousHash` enlazado desde `GENESIS`, hash recalculado y `eventId` único. Sirve igual para la base y para un archivo.
 * Tras la primera fila rota las siguientes no son fiables: quien lo usa debe parar.
 */
class ChainVerifier {
    private var expectedSequence = 1L
    private val seenIds = HashSet<String>()

    var lastHash: String = EventHasher.GENESIS
        private set

    val count: Int get() = (expectedSequence - 1).toInt()

    /** `null` si la fila encaja; si no, la primera ruptura. */
    fun accept(row: ChainRow): ChainVerification.Broken? {
        val reason = when {
            row.sequence != expectedSequence -> ChainVerification.Reason.SEQUENCE_GAP
            row.fields.previousHash != lastHash -> ChainVerification.Reason.BROKEN_LINK
            EventHasher.hash(row.fields) != row.hash -> ChainVerification.Reason.HASH_MISMATCH
            !seenIds.add(row.fields.eventId) -> ChainVerification.Reason.DUPLICATE_EVENT_ID
            else -> null
        }
        if (reason != null) return ChainVerification.Broken(row.sequence, reason)
        expectedSequence++
        lastHash = row.hash
        return null
    }

    fun result(): ChainVerification.Valid = ChainVerification.Valid(count)
}
