package com.al3xiel.my_finances.feature.privacy.domain.model

/**
 * Un paso del borrado total. Cada feature puede sumar el suyo con `@IntoSet`; corren de menor a mayor
 * [priority], de modo que la clave de la base siempre cae primero.
 */
interface WipeStep {
    /** Nombre estable para informar qué paso falló; nunca contiene datos del usuario. */
    val id: String
    val priority: Int get() = DEFAULT_PRIORITY

    suspend fun wipe()

    companion object {
        const val KEY_PRIORITY = 0
        const val DATABASES_PRIORITY = 10
        const val PREFERENCES_PRIORITY = 20
        const val CACHE_PRIORITY = 30
        const val DEFAULT_PRIORITY = 50
    }
}
