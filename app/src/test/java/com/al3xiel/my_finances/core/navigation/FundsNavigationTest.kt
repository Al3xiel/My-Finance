package com.al3xiel.my_finances.core.navigation

import com.al3xiel.my_finances.feature.accounts.di.AccountsNavigationModule
import com.al3xiel.my_finances.feature.funds.di.FundsNavigationModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FundsNavigationTest {
    @Test
    fun anExpenseEditorCanBeOpenedFromAFundWithItPreselected() {
        val route = ExpenseEditor(presetFundId = "fare")

        assertNull(route.id)
        assertNull(route.presetPoolId)
        assertEquals("fare", route.presetFundId)
        assertEquals(ExpenseEditor(), ExpenseEditor(id = null, presetPoolId = null, presetFundId = null))
    }

    @Test
    fun moreOffersFundsAmongTheOtherEntries() {
        val registry = MoreMenuRegistry(setOf(AccountsNavigationModule.moreMenu(), FundsNavigationModule.moreMenu()))

        val funds = registry.entries.single { it.id == "funds" }
        assertEquals(Funds, funds.route)
        assertEquals(true, registry.entries.indexOf(funds) > registry.entries.indexOfFirst { it.id == "accounts" })
    }

    @Test
    fun everyFundRouteSurvivesSavingAndRestoringTheTabState() {
        val routes = listOf<Route>(
            Funds, FundDetail("fare"), FundCount("fare"), FundEditor(), FundEditor("fare"), ExpenseEditor(presetFundId = "fare"),
        )
        routes.forEach { route ->
            val state = TabBackStacks().select(MainTab.MORE).push(route)
            val restored = Json.decodeFromString(TabBackStacks.serializer(), Json.encodeToString(TabBackStacks.serializer(), state))
            assertEquals(state, restored)
            assertEquals(listOf(More, route), restored.activeStack)
        }
    }
}
