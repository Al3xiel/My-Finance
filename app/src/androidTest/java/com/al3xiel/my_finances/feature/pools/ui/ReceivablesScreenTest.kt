package com.al3xiel.my_finances.feature.pools.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PersonReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ReceivablesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private val state = ReceivablesUiState(
        total = pen(14_000),
        people = listOf(PersonReceivable(PersonId("mom"), "Mamá", pen(11_000), pen(0))),
        pools = listOf(
            PoolReceivable(PoolId("dogs"), "Perros", PoolKind.PERMANENT, PersonId("mom"), "Mamá", pen(6_000), pen(11_000)),
            PoolReceivable(PoolId("trip"), "Viaje", PoolKind.EVENT, PersonId("mom"), "Mamá", pen(0), pen(0)),
        ),
        loans = listOf(
            LoanReceivable(LoanId("l1"), PersonId("pepe"), "Pepe", pen(5_000), pen(2_000), LocalDate.of(2026, 11, 4)),
            LoanReceivable(LoanId("l2"), PersonId("ana"), "Ana", pen(1_000), pen(0), LocalDate.of(2026, 11, 5)),
        ),
    )

    private fun show(onOpenPool: (PoolId) -> Unit = {}) {
        rule.setContent {
            MyfinancesTheme { ReceivablesContent(state, {}, onOpenPool, {}, {}, {}) }
        }
    }

    @Test fun showsTheTotalAndTheThreeSections() {
        show()
        rule.onNodeWithText("Te deben").assertIsDisplayed()
        rule.onNodeWithText("Por persona").assertIsDisplayed()
        rule.onNodeWithText("Cúmulos").assertIsDisplayed()
        rule.onNodeWithText("Deudas abiertas").assertIsDisplayed()
    }

    @Test fun aPoolWithSomethingPendingInThePeriodCarriesTheStatusInText() {
        show()
        rule.onNodeWithText("Pendiente").assertIsDisplayed()
    }

    @Test fun aPoolWithNothingPendingStillShowsAndSaysSo() {
        show()
        // "Viaje" no tiene pendiente: se ve en la lista y lo dice, en vez de desaparecer.
        rule.onNodeWithText("Viaje").assertIsDisplayed()
        rule.onNodeWithText("Sin pendiente").assertIsDisplayed()
    }

    @Test fun aLoanSaysWhetherItIsPartlyRepaidOrJustOpen() {
        show()
        rule.onNodeWithText("Parcial").assertIsDisplayed()
        rule.onNodeWithText("Abierta").assertIsDisplayed()
    }

    @Test fun openingAPoolReportsItsId() {
        var opened: PoolId? = null
        show { opened = it }
        rule.onNodeWithText("Perros").performClick()
        assertEquals(PoolId("dogs"), opened)
    }
}
