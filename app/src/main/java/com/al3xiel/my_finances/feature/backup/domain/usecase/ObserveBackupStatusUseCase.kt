package com.al3xiel.my_finances.feature.backup.domain.usecase

import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.feature.backup.domain.model.BackupStatus
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import javax.inject.Inject

class ObserveBackupStatusUseCase @Inject constructor(
    private val eventStore: EventStore,
    private val settings: BackupSettingsRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<BackupStatus> =
        combine(eventStore.observeBackupTimes(), settings.intervalDays) { times, days ->
            BackupStatus.from(times, clock.instant(), days)
        }
}
