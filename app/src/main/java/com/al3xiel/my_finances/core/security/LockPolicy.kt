package com.al3xiel.my_finances.core.security

object LockPolicy {
    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * @param backgroundedAtMillis instante monótono en que la app pasó a segundo plano; `null` en arranque en frío.
     * @param autoLockMinutes 0 = bloquear siempre al volver.
     */
    fun shouldLock(
        lockEnabled: Boolean,
        autoLockMinutes: Int,
        backgroundedAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (!lockEnabled) return false
        if (backgroundedAtMillis == null || autoLockMinutes <= 0) return true
        val elapsed = nowMillis - backgroundedAtMillis
        return elapsed < 0 || elapsed >= autoLockMinutes * MILLIS_PER_MINUTE
    }
}
