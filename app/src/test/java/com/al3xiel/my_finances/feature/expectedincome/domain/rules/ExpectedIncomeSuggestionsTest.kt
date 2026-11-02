package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.FakeExpectedIncomeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpectedIncomeSuggestionsTest {
    private val repo = FakeExpectedIncomeRepository()
    private val bbva = AccountId("bbva")
    private val cash = AccountId("cash")
    private val sueldo = IncomeSourceId("s1")
    private val names = mapOf(sueldo to "Sueldo")

    private fun put(id: String, account: AccountId = bbva, source: IncomeSourceId = sueldo, status: ExpectedIncomeStatus = ExpectedIncomeStatus.SCHEDULED) =
        repo.put(id, account, source, status = status)

    @Test
    fun suggestsOnlyPendingOnesOfTheSameAccountAndSource() {
        put("scheduled")
        put("overdue", status = ExpectedIncomeStatus.OVERDUE)
        put("other-account", account = cash)
        put("other-source", source = IncomeSourceId("s2"))
        put("confirmed", status = ExpectedIncomeStatus.CONFIRMED)
        put("cancelled", status = ExpectedIncomeStatus.CANCELLED)

        val suggestions = ExpectedIncomeSuggestions.of(repo.items.values.toList(), names, bbva, sueldo)

        assertEquals(listOf("scheduled", "overdue"), suggestions.map { it.id.value })
    }

    @Test
    fun carriesTheNameTheAmountAndTheRange() {
        put("e1")

        val suggestion = ExpectedIncomeSuggestions.of(repo.items.values.toList(), names, bbva, sueldo).single()

        assertEquals("Sueldo", suggestion.sourceName)
        assertEquals(200_000L, suggestion.estimated.amountMinor)
        assertEquals(LocalDate.of(2026, 10, 28), suggestion.expectedFrom)
        assertEquals(LocalDate.of(2026, 11, 5), suggestion.expectedTo)
    }

    @Test
    fun withoutAccountOrSourceThereIsNothingToSuggest() {
        put("e1")

        assertTrue(ExpectedIncomeSuggestions.of(repo.items.values.toList(), names, null, null).isEmpty())
    }

    @Test
    fun theSelectedOneIsFoundEvenIfItNoLongerMatchesAndNullIfMissing() {
        put("e1", account = cash)
        val all = repo.items.values.toList()

        assertEquals("e1", ExpectedIncomeSuggestions.selected(all, names, ExpectedIncomeId("e1"))?.id?.value)
        assertNull(ExpectedIncomeSuggestions.selected(all, names, ExpectedIncomeId("ghost")))
        assertNull(ExpectedIncomeSuggestions.selected(all, names, null))
    }

    @Test
    fun aMissingSourceNameIsEmptyNotACrash() {
        put("e1")

        assertEquals("", ExpectedIncomeSuggestions.of(repo.items.values.toList(), emptyMap(), bbva, sueldo).single().sourceName)
    }
}
