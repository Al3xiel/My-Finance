package com.al3xiel.my_finances.feature.categories.di

import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface CategoriesModule {
    @Binds
    fun categoryRepository(impl: CategoryRepositoryImpl): CategoryRepository
}
