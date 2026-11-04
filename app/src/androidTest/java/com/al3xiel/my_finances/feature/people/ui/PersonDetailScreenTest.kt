package com.al3xiel.my_finances.feature.people.ui

import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.feature.people.domain.model.PersonPartLine
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.model.PersonDetail
import com.al3xiel.my_finances.feature.people.domain.model.PersonLoanLine
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PersonDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private val mom = PersonId("mom")

    private fun loan(id: String, repaid: Long, status: LoanStatus, day: Int) =
        PersonLoanLine(LoanReceivable(LoanId(id), mom, "Mamá", pen(5_000), pen(repaid), LocalDate.of(2026, 11, day)), status)

    private val state = PersonDetailUiState(
        loaded = true,
        detail = PersonDetail(
            person = Person(mom, "Mamá", false),
            balance = pen(11_000),
            pools = emptyList(),
            loans = listOf(
                loan("l1", 0, LoanStatus.OPEN, 1),
                loan("l2", 2_000, LoanStatus.OPEN, 2),
                loan("l3", 5_000, LoanStatus.REPAID, 3),
            ),
            reimbursements = emptyList(),
            parts = emptyList(),
        ),
    )

    private fun show(onReceive: (PersonId) -> Unit = {}) {
        rule.setContent { MyfinancesTheme { PersonDetailContent(state, {}, {}, onReceive, {}) } }
    }

    @Test fun eachLoanCarriesItsStatusInText() {
        show()
        rule.onNodeWithText("Abierta").assertIsDisplayed()
        rule.onNodeWithText("Parcial").assertIsDisplayed()
        rule.onNodeWithText("Pagada").assertIsDisplayed()
    }

    @Test fun aPartOfTheirPoolsShowsItsDetail() {
        val withPart = state.copy(
            detail = state.detail!!.copy(
                parts = listOf(PersonPartLine(PartId("p4"), "Perros", LocalDate.of(2026, 11, 2), "Mascotas", emptyList(), pen(6_000), pen(6_000), "Pastillas de Boby")),
            ),
        )
        rule.setContent { MyfinancesTheme { PersonDetailContent(withPart, {}, {}, {}, {}) } }
        rule.onNodeWithText("Pastillas de Boby", substring = true).assertIsDisplayed()
    }

    @Test fun receivingAReimbursementReportsThePerson() {
        var person: PersonId? = null
        show { person = it }
        rule.onNodeWithText("Reembolso recibido").performClick()
        assertEquals(mom, person)
    }
}
