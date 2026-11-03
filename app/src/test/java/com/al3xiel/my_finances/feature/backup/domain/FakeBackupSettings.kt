package com.al3xiel.my_finances.feature.backup.domain

import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class FakeBackupSettings(
    enabled: Boolean = true,
    intervalDays: Int = BackupSettingsRepository.DEFAULT_INTERVAL_DAYS,
    var lastReminded: Instant? = null,
) : BackupSettingsRepository {
    private val enabledState = MutableStateFlow(enabled)
    private val intervalState = MutableStateFlow(intervalDays)

    override val enabled get() = enabledState
    override val intervalDays get() = intervalState

    override suspend fun isEnabled() = enabledState.value

    override suspend fun setEnabled(enabled: Boolean) {
        enabledState.value = enabled
    }

    override suspend fun setIntervalDays(days: Int) {
        intervalState.value = days
    }

    override suspend fun lastRemindedAt() = lastReminded

    override suspend fun markReminded(at: Instant) {
        lastReminded = at
    }
}
