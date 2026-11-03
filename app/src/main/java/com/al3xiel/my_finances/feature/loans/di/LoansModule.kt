package com.al3xiel.my_finances.feature.loans.di

import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.feature.loans.data.LoanRepositoryImpl
import com.al3xiel.my_finances.feature.loans.domain.repository.LoanRepository
import com.al3xiel.my_finances.feature.loans.domain.rules.LoanVoidGuard
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface LoansModule {
    @Binds
    fun loanRepository(impl: LoanRepositoryImpl): LoanRepository

    /** Un préstamo con pagos no se anula hasta anular los pagos (D20). */
    @Binds
    @IntoSet
    fun voidGuard(impl: LoanVoidGuard): MovementVoidGuard
}
