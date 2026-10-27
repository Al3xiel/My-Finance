package com.al3xiel.my_finances.feature.people.di

import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface PeopleModule {
    @Binds
    fun personRepository(impl: PersonRepositoryImpl): PersonRepository
}
