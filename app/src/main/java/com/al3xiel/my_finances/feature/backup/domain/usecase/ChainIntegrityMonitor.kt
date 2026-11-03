package com.al3xiel.my_finances.feature.backup.domain.usecase

import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Estado de la cadena de hashes; `Broken` solo dice dónde y por qué, nunca el contenido de un evento. */
sealed interface ChainStatus {
    data object Unknown : ChainStatus

    data class Intact(val eventCount: Int) : ChainStatus

    data class Broken(val sequence: Long, val reason: ChainVerification.Reason) : ChainStatus
}

/**
 * Verifica la cadena una vez por proceso, cuando la app ya está desbloqueada, y expone el resultado (P6).
 * [recheck] vuelve a mirar (antes de respaldar o restaurar).
 */
@Singleton
class ChainIntegrityMonitor @Inject constructor(
    private val verify: VerifyEventChainUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val _status = MutableStateFlow<ChainStatus>(ChainStatus.Unknown)
    val status: StateFlow<ChainStatus> = _status

    private val lock = Mutex()
    private var checked = false

    suspend fun checkOnce() {
        lock.withLock {
            if (checked) return
            run()
            checked = true
        }
    }

    suspend fun recheck(): ChainStatus = lock.withLock {
        run()
        checked = true
        _status.value
    }

    private suspend fun run() {
        _status.value = withContext(dispatcher) {
            when (val result = verify()) {
                is ChainVerification.Valid -> ChainStatus.Intact(result.eventCount)
                is ChainVerification.Broken -> ChainStatus.Broken(result.sequence, result.reason)
            }
        }
    }
}
