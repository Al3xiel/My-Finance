package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.event.EventStore
import javax.inject.Inject

sealed interface ChainVerification {
    enum class Reason { SEQUENCE_GAP, BROKEN_LINK, HASH_MISMATCH, DUPLICATE_EVENT_ID }

    data class Valid(val eventCount: Int) : ChainVerification

    /** Primer evento que no cuadra; los siguientes no se consideran fiables. */
    data class Broken(val sequence: Long, val reason: Reason) : ChainVerification
}

/** Recorre el registro por páginas de filas crudas: nunca lo carga entero ni decodifica los payloads. */
class VerifyEventChainUseCase @Inject constructor(private val eventStore: EventStore) {
    suspend operator fun invoke(): ChainVerification {
        val verifier = ChainVerifier()
        var after = 0L
        while (true) {
            val page = eventStore.readChainPage(after, PAGE_SIZE)
            if (page.isEmpty()) return verifier.result()
            for (row in page) verifier.accept(row)?.let { return it }
            after = page.last().sequence
        }
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
