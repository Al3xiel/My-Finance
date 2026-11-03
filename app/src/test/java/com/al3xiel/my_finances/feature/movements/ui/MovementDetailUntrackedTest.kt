package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.feature.movements.domain.model.HistoryKind
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.model.MovementHistoryEntry
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class MovementDetailUntrackedTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()

    private fun viewModel(id: String) = MovementDetailViewModel(
        id, f.movements, f.accounts, f.categories, f.tags, f.sources,
        VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock),
    )

    private fun show(type: MovementType) {
        val movement = f.expenseMovement("m1", f.bbva, "m1-untracked" to 500L).copy(type = type)
        f.movements.details[movement.id] = MovementDetail(movement, listOf(MovementHistoryEntry(1, HistoryKind.REGISTERED, f.today, Instant.EPOCH, null)))
    }

    @Test
    fun theUntrackedSpendOfACountCanNeitherBeCorrectedNorVoided() {
        show(MovementType.FUND_UNTRACKED_SPEND)

        val state = viewModel("m1").uiState.value

        assertTrue(state.isUntracked)
        assertFalse(state.canEdit)
    }

    @Test
    fun anOrdinaryExpenseStillCan() {
        show(MovementType.EXPENSE)

        val state = viewModel("m1").uiState.value

        assertFalse(state.isUntracked)
        assertTrue(state.canEdit)
    }
}
