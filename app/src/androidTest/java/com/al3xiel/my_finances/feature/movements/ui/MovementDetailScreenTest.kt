package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.feature.movements.domain.model.PartReimbursement
import com.al3xiel.my_finances.domain.model.ReimbursementStatus
import com.al3xiel.my_finances.feature.movements.domain.model.HistoryKind
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.model.MovementHistoryEntry
import com.al3xiel.my_finances.feature.movements.domain.model.MovementPart
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MovementDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val movement = Movement(
        id = MovementId("m1"),
        type = MovementType.EXPENSE,
        accountId = AccountId("a1"),
        counterAccountId = null,
        amount = Money(3_000, CurrencyCode.PEN),
        incomeSourceId = null,
        note = "Mercado",
        occurredOn = LocalDate.of(2026, 10, 25),
        status = MovementStatus.ACTIVE,
        revision = 2,
        parts = listOf(MovementPart(PartId("p1"), Money(3_000, CurrencyCode.PEN), CategoryId("c1"), PartKind.NORMAL, emptyList())),
    )

    private val state = MovementDetailUiState(
        detail = MovementDetail(
            movement,
            listOf(
                MovementHistoryEntry(1, HistoryKind.REGISTERED, LocalDate.of(2026, 10, 25), Instant.EPOCH, null),
                MovementHistoryEntry(2, HistoryKind.CORRECTED, LocalDate.of(2026, 10, 25), Instant.EPOCH, "monto mal"),
            ),
        ),
        loaded = true,
        names = CatalogNames(accounts = mapOf("a1" to "BBVA"), categories = mapOf("c1" to "Comida")),
    )

    private fun show(onAction: (MovementDetailUiAction) -> Unit = {}, onCorrect: () -> Unit = {}) =
        rule.setContent { MyfinancesTheme { MovementDetailContent(state, onAction, onCorrect) } }

    private fun withReimbursements(vararg statuses: Pair<Long, ReimbursementStatus>): MovementDetailUiState {
        val parts = statuses.mapIndexed { i, _ ->
            MovementPart(PartId("p$i"), Money(1_000, CurrencyCode.PEN), CategoryId("c1"), PartKind.NORMAL, emptyList(), PoolId("dogs"))
        }
        val reimbursements = statuses.mapIndexed { i, (reimbursed, status) ->
            PartReimbursement(PartId("p$i"), PoolId("dogs"), "Perros", Money(1_000, CurrencyCode.PEN), Money(reimbursed, CurrencyCode.PEN), status)
        }
        return state.copy(detail = MovementDetail(movement.copy(parts = parts), emptyList(), reimbursements))
    }

    @Test
    fun aPartInAPoolShowsItsPoolAndAReimbursementBadgeInText() {
        val withPool = withReimbursements(
            0L to ReimbursementStatus.PENDING,
            400L to ReimbursementStatus.PARTIALLY_REIMBURSED,
            1_000L to ReimbursementStatus.REIMBURSED,
        )
        rule.setContent { MyfinancesTheme { MovementDetailContent(withPool, {}, {}) } }
        rule.onNodeWithText("Pendiente").assertIsDisplayed()
        rule.onNodeWithText("Parcial").assertIsDisplayed()
        rule.onNodeWithText("Reembolsada").assertIsDisplayed()
        rule.onAllNodesWithText("Perros", substring = true).assertCountEquals(3)
    }

    @Test
    fun aPartWithoutAPoolShowsNoReimbursementBadge() {
        show()
        rule.onNodeWithText("Pendiente").assertDoesNotExist()
        rule.onNodeWithText("Reembolsada").assertDoesNotExist()
    }

    @Test
    fun partsAndHistoryComeInTheirOwnSections() {
        show()
        rule.onNodeWithText(string(R.string.movement_detail_parts)).assertIsDisplayed()
        rule.onNodeWithText("Comida").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.movement_detail_history)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.history_reason).replace("%1\$s", "monto mal")).assertIsDisplayed()
    }

    @Test
    fun theHistoryFoldsAndUnfoldsFromItsHeader() {
        show()
        val reason = string(R.string.history_reason).replace("%1\$s", "monto mal")
        rule.onNodeWithText(string(R.string.movement_detail_history)).performClick()
        rule.onNodeWithText(reason).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.movement_detail_history)).performClick()
        rule.onNodeWithText(reason).assertIsDisplayed()
    }

    @Test
    fun correctAndVoidStayAvailableInTheDetail() {
        val actions = mutableListOf<MovementDetailUiAction>()
        var corrected = 0
        show(actions::add) { corrected++ }
        rule.onNodeWithText(string(R.string.movement_detail_correct)).performClick()
        rule.onNodeWithText(string(R.string.movement_detail_void)).performClick()
        assertEquals(1, corrected)
        assertEquals(listOf<MovementDetailUiAction>(MovementDetailUiAction.ShowVoidDialog), actions)
    }
}
