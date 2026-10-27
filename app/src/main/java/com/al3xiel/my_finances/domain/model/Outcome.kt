package com.al3xiel.my_finances.domain.model

import kotlin.coroutines.cancellation.CancellationException

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Failed(val error: BusinessError) : Outcome<Nothing>
}

fun Outcome<*>.errorOrNull(): BusinessError? = (this as? Outcome.Failed)?.error

/**
 * Corre un caso de uso y convierte un fallo técnico inesperado en `Unexpected`, para que la pantalla lo muestre
 * y se pueda reintentar en vez de cerrar la app. La cancelación de la corrutina sí se propaga.
 */
suspend fun <T> attempt(block: suspend () -> Outcome<T>): Outcome<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    Outcome.Failed(BusinessError.Unexpected)
}
