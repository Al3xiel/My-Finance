package com.al3xiel.my_finances.feature.expectedincome.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ExpectedIncomesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun item(id: String, status: ExpectedIncomeStatus, archived: Boolean = false) = ExpectedIncomeItem(
        ExpectedIncome(
            ExpectedIncomeId(id), IncomeSourceId("s"), AccountId("a"), Money(200_000, CurrencyCode.PEN),
            ExpectedRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 5)), false, status, null, null, null,
        ),
        accountName = "BBVA", sourceName = "Sueldo $id", accountArchived = archived,
    )

    private fun show(items: List<ExpectedIncomeItem>, onConfirm: (ExpectedIncomeId) -> Unit = {}) {
        rule.setContent {
            MyfinancesTheme {
                ExpectedIncomesContent(ExpectedIncomesUiState(loaded = true, items = items), {}, {}, {}, onConfirm)
            }
        }
    }

    @Test fun everyStatusIsABadgeWithItsText() {
        show(
            listOf(
                item("1", ExpectedIncomeStatus.OVERDUE),
                item("2", ExpectedIncomeStatus.SCHEDULED),
                item("3", ExpectedIncomeStatus.CONFIRMED),
                item("4", ExpectedIncomeStatus.CANCELLED),
            ),
        )
        listOf("Vencido", "Programado", "Confirmado", "Cancelado").forEach {
            assertEquals(it, 1, rule.onAllNodesWithText(it).fetchSemanticsNodes().size)
        }
    }

    @Test fun anArchivedAccountBlocksConfirmAndSaysWhy() {
        show(listOf(item("1", ExpectedIncomeStatus.SCHEDULED, archived = true)))
        rule.onNodeWithText("Confirmar").assertIsNotEnabled()
        rule.onNodeWithText("Editar").assertIsEnabled()
    }

    @Test fun confirmingReportsTheId() {
        var confirmed: ExpectedIncomeId? = null
        show(listOf(item("1", ExpectedIncomeStatus.SCHEDULED))) { confirmed = it }
        rule.onNodeWithText("Confirmar").performClick()
        assertEquals(ExpectedIncomeId("1"), confirmed)
    }

    @Test fun aConfirmedIncomeOffersNoActions() {
        show(listOf(item("1", ExpectedIncomeStatus.CONFIRMED)))
        rule.onNodeWithText("Confirmar").assertDoesNotExist()
    }
}
