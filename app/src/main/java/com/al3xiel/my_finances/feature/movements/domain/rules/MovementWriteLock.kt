package com.al3xiel.my_finances.feature.movements.domain.rules

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializa las operaciones que leen el estado de un movimiento, lo validan y luego guardan (corregir, anular):
 * sin esto, dos llamadas simultáneas pasan ambas la validación y se guardan dos eventos. La app es de un solo
 * proceso y un solo usuario, así que un candado en memoria compartido por todos los casos de uso basta.
 */
@Singleton
class MovementWriteLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
