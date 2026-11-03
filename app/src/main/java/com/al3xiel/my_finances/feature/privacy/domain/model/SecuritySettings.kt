package com.al3xiel.my_finances.feature.privacy.domain.model

data class SecuritySettings(
    val lockEnabled: Boolean = true,
    val autoLockMinutes: Int = DEFAULT_AUTO_LOCK_MINUTES,
    val secureScreen: Boolean = true,
    val hideAmounts: Boolean = false,
) {
    companion object {
        const val DEFAULT_AUTO_LOCK_MINUTES = 5
        val AUTO_LOCK_OPTIONS = listOf(0, 1, 5, 15)
    }
}
