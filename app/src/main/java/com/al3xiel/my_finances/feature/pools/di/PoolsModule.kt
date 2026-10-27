package com.al3xiel.my_finances.feature.pools.di

import com.al3xiel.my_finances.domain.usecase.MovementCorrectionGuard
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.domain.usecase.PoolReferenceGuard
import com.al3xiel.my_finances.feature.pools.data.PoolRepositoryImpl
import com.al3xiel.my_finances.feature.pools.di.StatementSharerImpl
import com.al3xiel.my_finances.feature.pools.data.ReceivablesRepositoryImpl
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolExpenseGuard
import com.al3xiel.my_finances.feature.pools.ui.StatementSharer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface PoolsModule {
    @Binds
    fun poolRepository(impl: PoolRepositoryImpl): PoolRepository

    @Binds
    fun receivablesRepository(impl: ReceivablesRepositoryImpl): ReceivablesRepository

    @Binds
    fun statementSharer(impl: StatementSharerImpl): StatementSharer

    /** Los gastos con cúmulo exigen estas reglas a anular, corregir y registrar (T10). */
    @Binds
    @IntoSet
    fun voidGuard(impl: PoolExpenseGuard): MovementVoidGuard

    @Binds
    @IntoSet
    fun correctionGuard(impl: PoolExpenseGuard): MovementCorrectionGuard

    @Binds
    @IntoSet
    fun poolReferenceGuard(impl: PoolExpenseGuard): PoolReferenceGuard
}
