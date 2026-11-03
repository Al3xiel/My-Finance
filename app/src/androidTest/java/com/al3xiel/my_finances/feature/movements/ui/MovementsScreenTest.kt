package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import androidx.compose.runtime.CompositionLocalProvider
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MovementsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun row(id: String, type: MovementType, minor: Long, note: String, correct: Boolean = true, void: Boolean = true) =
        MovementRowUi(MovementId(id), type, "BBVA", null, note, Money(minor, CurrencyCode.PEN), false, canCorrect = correct, canVoid = void)

    private val state = MovementsUiState(
        days = listOf(
            DaySection(
                LocalDate.of(2026, 10, 26),
                listOf(row("1", MovementType.EXPENSE, 12_500, "Mercado"), row("2", MovementType.INCOME, 50_000, "Sueldo")),
                Money(37_500, CurrencyCode.PEN),
            ),
        ),
        loaded = true,
    )

    private fun show(
        s: MovementsUiState = state,
        hide: Boolean = false,
        onCorrect: (MovementId, MovementType) -> Unit = { _, _ -> },
        onAction: (MovementsUiAction) -> Unit = {},
    ) = rule.setContent {
        MyfinancesTheme {
            CompositionLocalProvider(LocalHideAmounts provides hide) {
                MovementsContent(s, onAction, onOpenMovement = {}, onCorrect = onCorrect)
            }
        }
    }

    @Test
    fun eachDayShowsItsSubtotalInTheHeader() {
        show()
        rule.onNodeWithText("375.00", substring = true).assertIsDisplayed()
    }

    @Test
    fun hiddenAmountsAlsoHideTheSubtotal() {
        show(hide = true)
        rule.onNodeWithText("375.00", substring = true).assertDoesNotExist()
    }

    @Test
    fun swipingTowardTheEndAsksToVoidTheMovement() {
        val actions = mutableListOf<MovementsUiAction>()
        show(onAction = actions::add)
        rule.onNodeWithText("Mercado").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf<MovementsUiAction>(MovementsUiAction.AskVoid(MovementId("1"))), actions)
    }

    @Test
    fun swipingTowardTheStartOpensTheEditorOfThatMovement() {
        val corrected = mutableListOf<Pair<MovementId, MovementType>>()
        show(onCorrect = { id, type -> corrected += id to type })
        rule.onNodeWithText("Mercado").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(listOf(MovementId("1") to MovementType.EXPENSE), corrected)
    }

    @Test
    fun aRowThatCannotBeVoidedIgnoresTheSwipe() {
        val actions = mutableListOf<MovementsUiAction>()
        val locked = state.copy(days = listOf(state.days.single().copy(rows = listOf(row("t", MovementType.TRANSFER, 100, "Retiro", correct = false, void = false)))))
        show(locked, onAction = actions::add)
        rule.onNodeWithText("Retiro").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(emptyList<MovementsUiAction>(), actions)
    }

    @Test
    fun voidingAsksForAReasonInTheList() {
        show(state.copy(voidingId = MovementId("1")))
        rule.onNodeWithText(string(R.string.movement_detail_void_title)).assertIsDisplayed()
    }
}
