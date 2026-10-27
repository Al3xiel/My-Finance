package com.al3xiel.my_finances.feature.tags.di

import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface TagsModule {
    @Binds
    fun tagRepository(impl: TagRepositoryImpl): TagRepository
}
