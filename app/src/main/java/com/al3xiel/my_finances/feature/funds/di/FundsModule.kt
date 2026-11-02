package com.al3xiel.my_finances.feature.funds.di

import com.al3xiel.my_finances.domain.usecase.FundReferenceGuard
import com.al3xiel.my_finances.domain.usecase.MovementCorrectionGuard
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.feature.funds.data.FundRepositoryImpl
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundExpenseGuard
import com.al3xiel.my_finances.feature.funds.domain.rules.FundMovementGuards
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface FundsModule {
    @Binds
    fun fundRepository(impl: FundRepositoryImpl): FundRepository

    /** Un gasto con fondo exige que el fondo esté activo, en rango y no anterior al último arqueo (T11). */
    @Binds
    @IntoSet
    fun fundReferenceGuard(impl: FundExpenseGuard): FundReferenceGuard

    /** Un ingreso con recargas y el gasto no registrado no se anulan ni bajan (T12). */
    @Binds
    @IntoSet
    fun voidGuard(impl: FundMovementGuards): MovementVoidGuard

    @Binds
    @IntoSet
    fun correctionGuard(impl: FundMovementGuards): MovementCorrectionGuard
}
