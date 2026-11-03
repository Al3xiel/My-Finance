package com.al3xiel.my_finances.feature.onboarding.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.feature.backup.domain.usecase.CanRestoreUseCase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun viewModel(nonSeed: Long) = WelcomeViewModel(
        CanRestoreUseCase(
            object : EventStore by FakeEventStore() {
                override suspend fun countNonSeed(sourceIds: List<String>, categoryIds: List<String>) = nonSeed
            },
        ),
    )

    @Test
    fun offersRestoreOnlyWhenThereAreNoOwnData() {
        assertEquals(true, viewModel(nonSeed = 0).uiState.value.canRestore)
        assertEquals(false, viewModel(nonSeed = 3).uiState.value.canRestore)
    }

    @Test
    fun doesNotOfferRestoreUntilResolved() {
        // Restaurar sobre datos propios está prohibido (D44): mientras no se sabe, no se ofrece.
        assertEquals(false, WelcomeUiState().canRestore)
    }
}
