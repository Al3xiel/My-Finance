package com.al3xiel.my_finances.feature.home.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.money.MoneyFormatter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeIndicatorsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun show(free: Long, hidden: Boolean = false) = rule.setContent {
        CompositionLocalProvider(LocalHideAmounts provides hidden) {
            HomeIndicators(accountsBalance = pen(100_000), reserved = pen(15_000), freeAvailable = pen(free), receivable = pen(12_345))
        }
    }

    @Test
    fun showsTheFourIndicatorsWithTheirAmounts() {
        show(free = 85_000)

        listOf(R.string.home_indicator_balance, R.string.home_indicator_reserved, R.string.home_indicator_free, R.string.home_indicator_receivable)
            .forEach { rule.onNodeWithText(string(it)).assertIsDisplayed() }
        rule.onNodeWithText(MoneyFormatter.format(pen(100_000))).assertIsDisplayed()
        rule.onNodeWithText(MoneyFormatter.format(pen(12_345))).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.home_free_negative_warning)).assertDoesNotExist()
    }

    @Test
    fun aNegativeFreeBalanceIsWarnedAboutAndKeepsItsSign() {
        show(free = -20_000)

        rule.onNodeWithText(string(R.string.home_free_negative_warning)).assertIsDisplayed()
        rule.onNodeWithText(MoneyFormatter.format(pen(-20_000))).assertIsDisplayed()
    }

    @Test
    fun hidingAmountsMasksAllFourNumbers() {
        show(free = 85_000, hidden = true)

        rule.onNodeWithText(MoneyFormatter.format(pen(100_000))).assertDoesNotExist()
        rule.onNodeWithText(MoneyFormatter.format(pen(15_000))).assertDoesNotExist()
        rule.onNodeWithText(MoneyFormatter.format(pen(85_000))).assertDoesNotExist()
        rule.onNodeWithText(MoneyFormatter.format(pen(12_345))).assertDoesNotExist()
    }

    @Test
    fun theBalanceIsTheHeroAndTheOthersAreSmaller() {
        show(free = 85_000)

        val hero = rule.onNodeWithText(MoneyFormatter.format(pen(100_000))).getUnclippedBoundsInRoot().height
        val small = rule.onNodeWithText(MoneyFormatter.format(pen(15_000))).getUnclippedBoundsInRoot().height
        assertTrue("el héroe debía ser mayor: $hero vs $small", hero > small)
    }

    @Test
    fun aNegativeTotalBalanceKeepsItsSign() {
        rule.setContent {
            CompositionLocalProvider(LocalHideAmounts provides false) {
                HomeIndicators(accountsBalance = pen(-30_000), reserved = pen(0), freeAvailable = pen(-30_000), receivable = pen(0))
            }
        }
        rule.onAllNodesWithText(MoneyFormatter.format(pen(-30_000))).onFirst().assertIsDisplayed()
    }
}
