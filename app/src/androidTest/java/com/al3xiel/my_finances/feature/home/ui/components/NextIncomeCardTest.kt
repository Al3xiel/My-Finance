package com.al3xiel.my_finances.feature.home.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class NextIncomeCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val income = ExpectedIncome(
        ExpectedIncomeId("e"), IncomeSourceId("s"), AccountId("a"), Money(250_000, CurrencyCode.PEN),
        ExpectedRange(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 30)), true, ExpectedIncomeStatus.SCHEDULED, null, null, null,
    )

    @Test fun anOverdueIncomeShowsTheOverdueBadge() {
        rule.setContent { MyfinancesTheme { NextIncomeCard(income, overdue = true, onClick = {}) } }
        rule.onNodeWithText("Vencido").assertIsDisplayed()
    }

    @Test fun aScheduledIncomeShowsThePendingBadge() {
        rule.setContent { MyfinancesTheme { NextIncomeCard(income, overdue = false, onClick = {}) } }
        rule.onNodeWithText("Pendiente").assertIsDisplayed()
    }
}
