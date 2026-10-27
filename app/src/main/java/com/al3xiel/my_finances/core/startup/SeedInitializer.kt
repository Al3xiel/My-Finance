package com.al3xiel.my_finances.core.startup

import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.domain.usecase.SeedCatalogUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Dispara el sembrado del catálogo en cada arranque; el caso de uso es idempotente. */
@Singleton
class SeedInitializer @Inject constructor(
    private val seed: SeedCatalogUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    fun start(scope: CoroutineScope) {
        scope.launch(dispatcher) { seed() }
    }
}
