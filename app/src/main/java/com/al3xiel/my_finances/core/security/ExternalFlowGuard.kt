package com.al3xiel.my_finances.core.security

import com.al3xiel.my_finances.core.time.MonotonicClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El selector de archivos del sistema (SAF) manda la app a segundo plano; ese viaje no debe contar para el
 * re-bloqueo (D48). Quien abre el selector llama [begin] y, al volver el resultado, [end]. Si nadie llama [end]
 * (el resultado se pierde), la guardia vence sola a los [MAX_MILLIS]; vive en memoria, así que morir el proceso la borra.
 */
@Singleton
class ExternalFlowGuard @Inject constructor(private val clock: MonotonicClock) {
    private var beganAtMillis: Long? = null

    fun begin() {
        beganAtMillis = clock.nowMillis()
    }

    fun end() {
        beganAtMillis = null
    }

    fun isActive(): Boolean {
        val began = beganAtMillis ?: return false
        return clock.nowMillis() - began <= MAX_MILLIS
    }

    companion object {
        const val MAX_MILLIS = 10 * 60_000L
    }
}
