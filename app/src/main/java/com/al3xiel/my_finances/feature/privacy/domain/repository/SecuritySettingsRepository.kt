package com.al3xiel.my_finances.feature.privacy.domain.repository

import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import kotlinx.coroutines.flow.Flow

interface SecuritySettingsRepository {
    val settings: Flow<SecuritySettings>

    suspend fun setLockEnabled(enabled: Boolean)

    /** Ignora valores fuera de [SecuritySettings.AUTO_LOCK_OPTIONS]. */
    suspend fun setAutoLockMinutes(minutes: Int)

    suspend fun setSecureScreen(enabled: Boolean)

    suspend fun setHideAmounts(enabled: Boolean)

    /** Vuelve a los valores por defecto. */
    suspend fun clear()
}
