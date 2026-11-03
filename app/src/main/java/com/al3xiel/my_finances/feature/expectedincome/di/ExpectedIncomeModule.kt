package com.al3xiel.my_finances.feature.expectedincome.di

import com.al3xiel.my_finances.domain.usecase.MovementVoidFollowUp
import com.al3xiel.my_finances.feature.expectedincome.data.ExpectedIncomeReminderNotifier
import com.al3xiel.my_finances.feature.expectedincome.data.ExpectedIncomeRepositoryImpl
import com.al3xiel.my_finances.feature.expectedincome.data.ReminderPreferences
import com.al3xiel.my_finances.feature.expectedincome.data.ReminderWipeStep
import com.al3xiel.my_finances.feature.expectedincome.data.WorkManagerReminderScheduler
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderNotifier
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderScheduler
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeVoidHooks
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface ExpectedIncomeModule {
    @Binds
    fun expectedIncomeRepository(impl: ExpectedIncomeRepositoryImpl): ExpectedIncomeRepository

    /** Anular el ingreso que confirmó un esperado cancela el siguiente que generó (D28). */
    @Binds
    @IntoSet
    fun voidFollowUp(impl: ExpectedIncomeVoidHooks): MovementVoidFollowUp

    @Binds
    fun reminderRepository(impl: ReminderPreferences): ReminderRepository

    @Binds
    fun reminderNotifier(impl: ExpectedIncomeReminderNotifier): ReminderNotifier

    @Binds
    fun reminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    /** Cancela el Worker antes de borrar la clave (prioridad menor que la de la clave). */
    @Binds
    @IntoSet
    fun reminderWipeStep(impl: ReminderWipeStep): WipeStep
}
