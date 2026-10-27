package com.al3xiel.my_finances.feature.movements.di

import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface MovementsModule {
    @Binds
    fun movementRepository(impl: MovementRepositoryImpl): MovementRepository
}
