package com.al3xiel.my_finances.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Rule
import org.junit.Test

class MoneyTextRenderTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test fun theHeroVariantShowsTheAmount() {
        rule.setContent {
            MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { MoneyText(pen(123_456), variant = MoneyVariant.HERO) } }
        }
        rule.onNodeWithText("S/ 1,234.56").assertIsDisplayed()
    }

    @Test fun aToneNeverBringsBackAHiddenAmount() {
        rule.setContent {
            MyfinancesTheme {
                CompositionLocalProvider(LocalHideAmounts provides true) {
                    MoneyText(pen(-300), tone = MoneyTone.AUTO, variant = MoneyVariant.HERO)
                }
            }
        }
        rule.onNodeWithText("-S/ 3.00").assertDoesNotExist()
        rule.onNodeWithText("S/ ••••").assertIsDisplayed()
    }

    @Test fun aNegativeBalanceKeepsItsMinusSign() {
        rule.setContent {
            MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { MoneyText(pen(-300), tone = MoneyTone.AUTO) } }
        }
        rule.onNodeWithText("-S/ 3.00").assertIsDisplayed()
    }
}
