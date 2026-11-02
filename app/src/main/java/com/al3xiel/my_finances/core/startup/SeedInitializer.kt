package com.al3xiel.my_finances.core.startup

import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.domain.usecase.ProjectionVersionGuard
import com.al3xiel.my_finances.domain.usecase.SeedCatalogUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * En cada arranque, primero el replay por cambio de versión (D32) y después el sembrado del catálogo, que es idempotente:
 * ningún evento nuevo entra antes de que las proyecciones estén al día.
 */
@Singleton
class SeedInitializer @Inject constructor(
    private val versionGuard: ProjectionVersionGuard,
    private val seed: SeedCatalogUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    fun start(scope: CoroutineScope) {
        scope.launch(dispatcher) {
            versionGuard()
            seed()
        }
    }
}
