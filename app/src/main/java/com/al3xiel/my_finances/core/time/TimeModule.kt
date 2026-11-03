package com.al3xiel.my_finances.core.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId

/** `Clock` y `ZoneId` se inyectan: ningún caso de uso llama a `Instant.now()` ni a `LocalDate.now()`. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun provideClock(zoneId: ZoneId): Clock = Clock.system(zoneId)
}
