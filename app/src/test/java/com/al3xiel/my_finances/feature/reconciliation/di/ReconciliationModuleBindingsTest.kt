package com.al3xiel.my_finances.feature.reconciliation.di

import com.al3xiel.my_finances.domain.usecase.AccountArchiveGuard
import com.al3xiel.my_finances.domain.usecase.ReconciliationLinkGuard
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import dagger.Binds
import dagger.multibindings.IntoSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los casos de uso reciben sus ganchos con `= emptySet()` por defecto: si el `@IntoSet` se perdiera, compilarían igual y
 * la validación pasaría en silencio. Esta prueba fija que el módulo los aporta.
 */
class ReconciliationModuleBindingsTest {
    private val contributions = ReconciliationModule::class.java.methods
        .filter { it.isAnnotationPresent(Binds::class.java) && it.isAnnotationPresent(IntoSet::class.java) }

    @Test
    fun theLinkGuardIsContributedToItsSet() {
        val binding = contributions.single { it.returnType == ReconciliationLinkGuard::class.java }

        assertEquals(listOf<Class<*>>(ReconciliationGuards::class.java), binding.parameterTypes.toList())
    }

    @Test
    fun theArchiveGuardIsContributedToItsSet() {
        val binding = contributions.single { it.returnType == AccountArchiveGuard::class.java }

        assertEquals(listOf<Class<*>>(ReconciliationGuards::class.java), binding.parameterTypes.toList())
    }

    @Test
    fun theRepositoryIsBound() {
        assertTrue(ReconciliationModule::class.java.methods.any { it.isAnnotationPresent(Binds::class.java) && !it.isAnnotationPresent(IntoSet::class.java) })
    }
}
