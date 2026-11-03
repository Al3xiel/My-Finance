package com.al3xiel.my_finances.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MovementRowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun show(kind: MovementRowKind, voided: Boolean = false, onClick: () -> Unit = {}) = rule.setContent {
        MyfinancesTheme {
            CompositionLocalProvider(LocalHideAmounts provides false) {
                MovementRow("Mercado", pen(3_000), kind, onClick = onClick, subtitle = "BBVA", isVoided = voided)
            }
        }
    }

    @Test fun anExpenseShowsTheMinusSign() {
        show(MovementRowKind.EXPENSE)
        rule.onNodeWithText("−S/ 30.00").assertIsDisplayed()
    }

    @Test fun anIncomeShowsThePlusSign() {
        show(MovementRowKind.INCOME)
        rule.onNodeWithText("+S/ 30.00").assertIsDisplayed()
    }

    @Test fun aVoidedMovementIsMarkedWithText() {
        show(MovementRowKind.EXPENSE, voided = true)
        rule.onNodeWithText("BBVA · Anulado").assertIsDisplayed()
    }

    @Test fun tappingDispatchesClickOnce() {
        var clicks = 0
        show(MovementRowKind.TRANSFER, onClick = { clicks++ })
        rule.onNodeWithText("Mercado").performClick()
        assertEquals(1, clicks)
    }
}
