package com.al3xiel.my_finances.core.di

import com.al3xiel.my_finances.domain.usecase.ArchiveGuard
import com.al3xiel.my_finances.domain.usecase.MovementCorrectionGuard
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.domain.usecase.PoolReferenceGuard
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declara los conjuntos de ganchos para que existan (vacíos) aunque ninguna feature aporte los suyos. */
@Module
@InstallIn(SingletonComponent::class)
abstract class GuardsModule {
    @Multibinds abstract fun movementVoidGuards(): Set<@JvmSuppressWildcards MovementVoidGuard>

    @Multibinds abstract fun movementCorrectionGuards(): Set<@JvmSuppressWildcards MovementCorrectionGuard>

    @Multibinds abstract fun archiveGuards(): Set<@JvmSuppressWildcards ArchiveGuard>

    @Multibinds abstract fun poolReferenceGuards(): Set<@JvmSuppressWildcards PoolReferenceGuard>
}
