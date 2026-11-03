package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Rule
import org.junit.Test

class MoneyInputRenderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun theLargeAmountFieldShowsLabelCurrencyAndValue() {
        rule.setContent { MyfinancesTheme { MoneyInput(value = "12.5", onValueChange = {}, label = "Monto", large = true) } }
        rule.onNodeWithText("Monto").assertIsDisplayed()
        rule.onNodeWithText("S/").assertIsDisplayed()
        rule.onNodeWithText("12.5").assertIsDisplayed()
    }
}
