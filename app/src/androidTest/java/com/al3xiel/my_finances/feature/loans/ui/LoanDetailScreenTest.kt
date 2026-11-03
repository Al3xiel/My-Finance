package com.al3xiel.my_finances.feature.loans.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.loans.domain.model.Loan
import com.al3xiel.my_finances.feature.loans.domain.model.LoanDetail
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LoanDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun state(repaid: Long, status: LoanStatus) = LoanDetailUiState(
        loaded = true,
        detail = LoanDetail(
            Loan(LoanId("l1"), PersonId("pepe"), MovementId("ml1"), pen(5_000), pen(repaid), status, LocalDate.of(2026, 11, 4)),
            "Pepe",
            emptyList(),
        ),
    )

    private fun show(state: LoanDetailUiState, onAction: (LoanDetailUiAction) -> Unit = {}) {
        rule.setContent { MyfinancesTheme { LoanDetailContent(state, onAction) } }
    }

    @Test fun aLoanNothingCameBackFromIsOpen() {
        show(state(0, LoanStatus.OPEN))
        rule.onNodeWithText("Abierta").assertIsDisplayed()
        rule.onNodeWithText("Parcial").assertDoesNotExist()
    }

    @Test fun aLoanPartlyPaidSaysPartial() {
        show(state(2_000, LoanStatus.OPEN))
        rule.onNodeWithText("Parcial").assertIsDisplayed()
    }

    @Test fun aRepaidLoanSaysRepaidAndOffersNoPayment() {
        show(state(5_000, LoanStatus.REPAID))
        rule.onNodeWithText("Pagada").assertIsDisplayed()
        rule.onNodeWithText("Registrar pago").assertDoesNotExist()
    }

    @Test fun registeringAPaymentOpensTheDialogAction() {
        var action: LoanDetailUiAction? = null
        show(state(0, LoanStatus.OPEN)) { action = it }
        rule.onNodeWithText("Registrar pago").performClick()
        assertEquals(LoanDetailUiAction.OpenPay, action)
    }
}
