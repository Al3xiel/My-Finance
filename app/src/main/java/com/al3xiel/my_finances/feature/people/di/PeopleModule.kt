package com.al3xiel.my_finances.feature.people.di

import com.al3xiel.my_finances.feature.people.data.PersonDetailRepositoryImpl
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.people.domain.repository.PersonDetailRepository
import com.al3xiel.my_finances.feature.people.domain.rules.PersonBalanceGuard
import com.al3xiel.my_finances.domain.usecase.ArchiveGuard
import dagger.multibindings.IntoSet
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

    @Binds
    fun personDetailRepository(impl: PersonDetailRepositoryImpl): PersonDetailRepository

    /** Una persona con saldo pendiente no se archiva. */
    @Binds
    @IntoSet
    fun archiveGuard(impl: PersonBalanceGuard): ArchiveGuard
}
