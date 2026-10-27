package com.al3xiel.my_finances.core.di

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface IdModule {
    @Binds
    fun idGenerator(impl: UuidIdGenerator): IdGenerator
}
