package com.al3xiel.my_finances.feature.privacy

import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSecuritySettingsRepository(initial: SecuritySettings = SecuritySettings()) : SecuritySettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<SecuritySettings> = state

    override suspend fun setLockEnabled(enabled: Boolean) = state.update { it.copy(lockEnabled = enabled) }

    override suspend fun setAutoLockMinutes(minutes: Int) {
        if (minutes in SecuritySettings.AUTO_LOCK_OPTIONS) state.update { it.copy(autoLockMinutes = minutes) }
    }

    override suspend fun setSecureScreen(enabled: Boolean) = state.update { it.copy(secureScreen = enabled) }

    override suspend fun setHideAmounts(enabled: Boolean) = state.update { it.copy(hideAmounts = enabled) }

    override suspend fun clear() = state.update { SecuritySettings() }
}
