package com.al3xiel.my_finances.feature.reconciliation.di

import com.al3xiel.my_finances.feature.reconciliation.data.ReconciliationRepositoryImpl
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import com.al3xiel.my_finances.domain.usecase.AccountArchiveGuard
import com.al3xiel.my_finances.domain.usecase.ReconciliationLinkGuard
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface ReconciliationModule {
    @Binds
    fun reconciliationRepository(impl: ReconciliationRepositoryImpl): ReconciliationRepository

    /** Un movimiento registrado desde la conciliación debe ser de su cuenta y ella estar abierta. */
    @Binds
    @IntoSet
    fun linkGuard(impl: ReconciliationGuards): ReconciliationLinkGuard

    /** Una cuenta con una conciliación abierta no se archiva. */
    @Binds
    @IntoSet
    fun archiveGuard(impl: ReconciliationGuards): AccountArchiveGuard
}
