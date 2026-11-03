package com.al3xiel.my_finances.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FundProgressCardTest {
    @get:Rule val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test
    fun showsTheThreeFiguresWithTheEstimatedLabel() {
        rule.setContent {
            CompositionLocalProvider(LocalHideAmounts provides false) {
                FundProgressCard("Pasajes", pen(5_000), pen(1_200), pen(3_800), isClosed = false, onClick = {})
            }
        }

        rule.onNodeWithText("Pasajes").assertIsDisplayed()
        rule.onNodeWithText("Asignado").assertIsDisplayed()
        rule.onNodeWithText("Gastado").assertIsDisplayed()
        rule.onNodeWithText("Restante estimado").assertIsDisplayed()
        rule.onNodeWithText("S/ 50.00").assertIsDisplayed()
        rule.onNodeWithText("S/ 12.00").assertIsDisplayed()
        rule.onNodeWithText("S/ 38.00").assertIsDisplayed()
    }

    @Test
    fun aClosedFundSaysSo() {
        rule.setContent { FundProgressCard("Viejo", pen(1_000), pen(1_000), pen(0), isClosed = true, onClick = {}) }

        rule.onNodeWithText("Cerrado").assertIsDisplayed()
    }

    @Test
    fun anOverspentFundStillDrawsAndShowsItsNegativeRemaining() {
        rule.setContent {
            CompositionLocalProvider(LocalHideAmounts provides false) {
                FundProgressCard("Comida", pen(1_000), pen(1_500), pen(-500), isClosed = false, onClick = {})
            }
        }

        rule.onNodeWithText("Comida").assertIsDisplayed()
        rule.onNodeWithText("S/ 15.00").assertIsDisplayed()
    }

    @Test
    fun theRemainingIsLabelledEstimatedWithTheSharedBadge() {
        rule.setContent { FundProgressCard("Pasajes", pen(5_000), pen(1_200), pen(3_800), isClosed = false, onClick = {}) }

        rule.onNodeWithText("Estimado").assertIsDisplayed()
    }

    @Test
    fun anOverspentFundWarnsInTextNotOnlyInRed() {
        rule.setContent { FundProgressCard("Comida", pen(1_000), pen(1_500), pen(-500), isClosed = false, onClick = {}) }

        rule.onNodeWithText("Gastaste más de lo asignado").assertIsDisplayed()
    }

    @Test
    fun aFundWithinItsAllocationShowsNoWarning() {
        rule.setContent { FundProgressCard("Pasajes", pen(5_000), pen(1_200), pen(3_800), isClosed = false, onClick = {}) }

        rule.onNodeWithText("Gastaste más de lo asignado").assertDoesNotExist()
    }

    @Test
    fun hidingAmountsMasksTheFigures() {
        rule.setContent {
            CompositionLocalProvider(LocalHideAmounts provides true) {
                FundProgressCard("Pasajes", pen(5_000), pen(1_200), pen(3_800), isClosed = false, onClick = {})
            }
        }

        rule.onNodeWithText("S/ 50.00").assertDoesNotExist()
    }

    @Test
    fun tappingTheCardDispatchesClick() {
        var clicks = 0
        rule.setContent { FundProgressCard("Pasajes", pen(5_000), pen(0), pen(5_000), isClosed = false, onClick = { clicks++ }) }

        rule.onNodeWithText("Pasajes").performClick()

        assertEquals(1, clicks)
    }
}
