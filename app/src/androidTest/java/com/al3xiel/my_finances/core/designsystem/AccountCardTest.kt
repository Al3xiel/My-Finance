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

class AccountCardTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test fun showsNameTypeAndBalance() {
        rule.setContent {
            MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { AccountCard("BBVA", pen(1_095_000), onClick = {}, typeLabel = "Banco") } }
        }
        rule.onNodeWithText("BBVA").assertIsDisplayed()
        rule.onNodeWithText("Banco").assertIsDisplayed()
        rule.onNodeWithText("S/ 10,950.00").assertIsDisplayed()
    }

    @Test fun anArchivedAccountSaysSo() {
        rule.setContent { MyfinancesTheme { AccountCard("Antigua", pen(0), onClick = {}, isArchived = true) } }
        rule.onNodeWithText("Archivada").assertIsDisplayed()
    }

    @Test fun hidingAmountsMasksTheBalance() {
        rule.setContent { MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides true) { AccountCard("BBVA", pen(1_095_000), onClick = {}) } } }
        rule.onNodeWithText("S/ 10,950.00").assertDoesNotExist()
    }

    @Test fun aNegativeBalanceKeepsItsMinus() {
        rule.setContent { MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { AccountCard("Tarjeta", pen(-5_000), onClick = {}) } } }
        rule.onNodeWithText("-S/ 50.00").assertIsDisplayed()
    }

    @Test fun tappingDispatchesClickOnce() {
        var clicks = 0
        rule.setContent { MyfinancesTheme { AccountCard("BBVA", pen(1), onClick = { clicks++ }) } }
        rule.onNodeWithText("BBVA").performClick()
        assertEquals(1, clicks)
    }
}
