package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.event.EventHasher
import com.al3xiel.my_finances.domain.model.event.EventStore
import javax.inject.Inject

sealed interface ChainVerification {
    enum class Reason { SEQUENCE_GAP, BROKEN_LINK, HASH_MISMATCH }

    data class Valid(val eventCount: Int) : ChainVerification

    /** Primer evento que no cuadra; los siguientes no se consideran fiables. */
    data class Broken(val sequence: Long, val reason: Reason) : ChainVerification
}

class VerifyEventChainUseCase @Inject constructor(private val eventStore: EventStore) {
    suspend operator fun invoke(): ChainVerification {
        var expectedSequence = 1L
        var previousHash = EventHasher.GENESIS
        val events = eventStore.readAll()
        for (event in events) {
            val reason = when {
                event.sequence != expectedSequence -> ChainVerification.Reason.SEQUENCE_GAP
                event.previousHash != previousHash -> ChainVerification.Reason.BROKEN_LINK
                EventHasher.hash(event) != event.hash -> ChainVerification.Reason.HASH_MISMATCH
                else -> null
            }
            if (reason != null) return ChainVerification.Broken(event.sequence, reason)
            expectedSequence++
            previousHash = event.hash
        }
        return ChainVerification.Valid(events.size)
    }
}
