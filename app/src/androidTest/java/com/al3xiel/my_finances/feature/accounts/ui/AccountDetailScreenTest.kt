package com.al3xiel.my_finances.feature.accounts.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AccountDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun reconciliation(needsReview: Boolean) = Reconciliation(
        ReconciliationId("r1"), AccountId("bcp"), Money(81_240, CurrencyCode.PEN), Money(80_000, CurrencyCode.PEN),
        ReconciliationStatus.RESOLVED, null, null, null, Instant.EPOCH, null, needsReview = needsReview,
    )

    private fun state(needsReview: Boolean) = AccountDetailUiState(
        account = AccountWithBalance(Account(AccountId("bcp"), "BCP", AccountType.BANK, CurrencyCode.PEN, null, false), 80_000),
        canReconcile = true,
        latestReconciliation = reconciliation(needsReview),
        latestReconciledOn = LocalDate.of(2026, 10, 25),
        today = LocalDate.of(2026, 10, 26),
    )

    @Test
    fun aReconciliationThatNeedsReviewCarriesAStatusBadgeAndItsExplanation() {
        rule.setContent { MyfinancesTheme { AccountDetailContent(state(needsReview = true), {}, {}, {}) } }
        rule.onNodeWithText(string(R.string.status_needs_review)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.reconciliation_needs_review)).assertIsDisplayed()
    }

    @Test
    fun aCleanReconciliationHasNoReviewBadge() {
        rule.setContent { MyfinancesTheme { AccountDetailContent(state(needsReview = false), {}, {}, {}) } }
        rule.onNodeWithText(string(R.string.status_needs_review)).assertDoesNotExist()
    }

    @Test
    fun reconcileAndTheLastReconciliationStayReachable() {
        var reconcile = 0
        val opened = mutableListOf<ReconciliationId>()
        rule.setContent {
            MyfinancesTheme { AccountDetailContent(state(needsReview = false), {}, { reconcile++ }, opened::add) }
        }
        rule.onNodeWithText(string(R.string.account_detail_reconcile)).performClick()
        rule.onNodeWithText(string(R.string.account_detail_last_reconciliation)).performClick()
        assertEquals(1, reconcile)
        assertEquals(listOf(ReconciliationId("r1")), opened)
    }
}
