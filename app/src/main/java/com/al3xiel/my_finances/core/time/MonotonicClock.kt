package com.al3xiel.my_finances.core.time

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/** Reloj que no depende de la hora de pared: el usuario no lo adelanta ni lo atrasa. */
interface MonotonicClock {
    fun nowMillis(): Long
}

@Singleton
class SystemMonotonicClock @Inject constructor() : MonotonicClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}
