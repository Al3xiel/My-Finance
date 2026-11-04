package com.al3xiel.my_finances.feature.home.ui

import com.al3xiel.my_finances.core.designsystem.SCREEN_SCAFFOLD_BAR_TAG
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
import org.junit.Assert.assertTrue
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
    fun theHeaderIsTheSharedTabHeaderWithTheHideAmountsAction() {
        show(HomeUiState(summary = summary(withAccount = true)))
        rule.onNodeWithTag(SCREEN_SCAFFOLD_BAR_TAG).assertExists()
        rule.onNodeWithText(string(R.string.home_title)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.home_hide_amounts)).assertIsDisplayed()
    }

    @Test
    fun whileLoadingTheHeaderIsStillThere() {
        show(HomeUiState(summary = null))
        rule.onNodeWithTag(SCREEN_SCAFFOLD_BAR_TAG).assertExists()
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

    @Test
    fun accountsAreACarouselInOneRow() {
        val two = summary(withAccount = true).let {
            it.copy(accounts = it.accounts + AccountWithBalance(Account(AccountId("cash"), "Efectivo", AccountType.CASH, CurrencyCode.PEN, null, false), 5_000))
        }
        show(HomeUiState(summary = two))

        val first = rule.onNodeWithText("BBVA", useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val second = rule.onNodeWithText("Efectivo", useUnmergedTree = true).getUnclippedBoundsInRoot().top
        assertEquals(first, second)
    }

    @Test
    fun anAccountNameStaysReadableAtTwiceTheFontSize() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalHideAmounts provides false,
            ) {
                HomeContent(HomeUiState(summary = summary(withAccount = true)), {}, {}, {}, {}, {})
            }
        }
        val bounds = rule.onNodeWithText("BBVA", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("el nombre se partió letra por letra: ${bounds.width} x ${bounds.height}", bounds.width > bounds.height)
    }
}
