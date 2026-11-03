package com.al3xiel.my_finances.feature.home.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.home.domain.model.HomeSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun summary(withAccount: Boolean) = HomeSummary(
        accountsBalance = pen(if (withAccount) 80_000 else 0),
        reserved = pen(0),
        freeAvailable = pen(if (withAccount) 80_000 else 0),
        receivable = pen(0),
        accounts = if (withAccount) {
            listOf(AccountWithBalance(Account(AccountId("bbva"), "BBVA", AccountType.BANK, CurrencyCode.PEN, null, false), 80_000))
        } else {
            emptyList()
        },
        funds = emptyList(),
        nextIncome = null,
        alerts = emptyList(),
    )

    private fun show(state: HomeUiState, onAction: (HomeUiAction) -> Unit = {}, onOpenAccount: (AccountId) -> Unit = {}) = rule.setContent {
        CompositionLocalProvider(LocalHideAmounts provides state.hideAmounts) {
            HomeContent(state, onAction, onOpenAccount = onOpenAccount, onOpenFund = {}, onOpenAlert = {}, onOpenNextIncome = {})
        }
    }

    @Test
    fun withoutAccountsEveryBlockShowsItsEmptyState() {
        show(HomeUiState(summary = summary(withAccount = false)))

        rule.onNodeWithText(string(R.string.home_empty_accounts)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.home_empty_funds)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.home_empty_next_income)).assertIsDisplayed()
    }

    @Test
    fun tappingAnAccountRowOpensItsDetail() {
        val opened = mutableListOf<AccountId>()
        show(HomeUiState(summary = summary(withAccount = true)), onOpenAccount = { opened += it })

        rule.onNodeWithText("BBVA").performClick()

        assertEquals(listOf(AccountId("bbva")), opened)
    }

    @Test
    fun theEyeAsksToToggleAndHidingMasksTheAmounts() {
        val actions = mutableListOf<HomeUiAction>()
        show(HomeUiState(summary = summary(withAccount = true), hideAmounts = true), onAction = { actions += it })

        rule.onNodeWithText(MoneyFormatter.format(pen(80_000))).assertDoesNotExist()
        rule.onNodeWithContentDescription(string(R.string.home_show_amounts)).performClick()

        assertEquals(listOf<HomeUiAction>(HomeUiAction.ToggleHideAmounts), actions)
    }
}
