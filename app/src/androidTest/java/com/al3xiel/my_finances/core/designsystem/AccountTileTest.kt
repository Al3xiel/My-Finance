package com.al3xiel.my_finances.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountTileTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test fun showsNameAndBalanceAndDispatchesClick() {
        var clicks = 0
        rule.setContent {
            MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { AccountTile("BBVA", pen(995_000), onClick = { clicks++ }) } }
        }
        rule.onNodeWithText("S/ 9,950.00").assertIsDisplayed()
        rule.onNodeWithText("BBVA").performClick()
        assertEquals(1, clicks)
    }

    @Test fun aNegativeBalanceKeepsItsMinus() {
        rule.setContent {
            MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { AccountTile("Tarjeta", pen(-5_000), onClick = {}) } }
        }
        rule.onNodeWithText("-S/ 50.00").assertIsDisplayed()
    }

    @Test fun theNameIsNotSplitAtTwiceTheFontSize() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f), LocalHideAmounts provides false) {
                MyfinancesTheme { AccountTile("BBVA", pen(995_000), onClick = {}) }
            }
        }
        val b = rule.onNodeWithText("BBVA", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("nombre partido: ${b.width} x ${b.height}", b.width > b.height)
    }
}
