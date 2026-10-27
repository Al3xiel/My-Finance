package com.al3xiel.my_finances.feature.accounts.di

import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface AccountsModule {
    @Binds
    fun accountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    fun incomeSourceRepository(impl: IncomeSourceRepositoryImpl): IncomeSourceRepository
}
