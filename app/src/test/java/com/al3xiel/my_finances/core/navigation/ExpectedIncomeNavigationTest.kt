package com.al3xiel.my_finances.core.navigation

import com.al3xiel.my_finances.feature.accounts.di.AccountsNavigationModule
import com.al3xiel.my_finances.feature.expectedincome.di.ExpectedIncomeNavigationModule
import com.al3xiel.my_finances.feature.funds.di.FundsNavigationModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpectedIncomeNavigationTest {
    @Test
    fun anIncomeCanBeOpenedFromAnExpectedIncomeWithoutBreakingTheOtherUses() {
        val route = IncomeEditor(expectedIncomeId = "e1")

        assertNull(route.id)
        assertEquals("e1", route.expectedIncomeId)
        assertEquals(IncomeEditor(), IncomeEditor(id = null, expectedIncomeId = null))
        assertEquals("m1", IncomeEditor("m1").id)
        assertNull(IncomeEditor("m1").expectedIncomeId)
    }

    @Test
    fun moreOffersExpectedIncomesAfterFunds() {
        val registry = MoreMenuRegistry(
            setOf(AccountsNavigationModule.moreMenu(), FundsNavigationModule.moreMenu(), ExpectedIncomeNavigationModule.moreMenu()),
        )

        val entry = registry.entries.single { it.id == "expected_incomes" }
        assertEquals(ExpectedIncomes, entry.route)
        assertEquals(true, registry.entries.indexOf(entry) > registry.entries.indexOfFirst { it.id == "funds" })
    }

    @Test
    fun everyExpectedIncomeRouteSurvivesSavingAndRestoringTheTabState() {
        val routes = listOf<Route>(
            ExpectedIncomes, ExpectedIncomeEditor(), ExpectedIncomeEditor("e1"), IncomeEditor(expectedIncomeId = "e1"),
        )
        routes.forEach { route ->
            val state = TabBackStacks().select(MainTab.MORE).push(route)
            val restored = Json.decodeFromString(TabBackStacks.serializer(), Json.encodeToString(TabBackStacks.serializer(), state))
            assertEquals(state, restored)
            assertEquals(listOf(More, route), restored.activeStack)
        }
    }
}
