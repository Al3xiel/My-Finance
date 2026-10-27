package com.al3xiel.my_finances.core.navigation

import com.al3xiel.my_finances.feature.accounts.di.AccountsNavigationModule
import com.al3xiel.my_finances.feature.categories.di.CategoriesNavigationModule
import com.al3xiel.my_finances.feature.movements.di.MovementsNavigationModule
import com.al3xiel.my_finances.feature.people.di.PeopleNavigationModule
import com.al3xiel.my_finances.feature.privacy.di.PrivacyNavigationModule
import com.al3xiel.my_finances.feature.tags.di.TagsNavigationModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationEntriesTest {
    private val registerActions = RegisterActionRegistry(
        setOf(MovementsNavigationModule.registerActions(), AccountsNavigationModule.registerActions()),
    )

    private val moreEntries = MoreMenuRegistry(
        setOf(
            PrivacyNavigationModule.moreMenu(),
            AccountsNavigationModule.moreMenu(),
            CategoriesNavigationModule.moreMenu(),
            TagsNavigationModule.moreMenu(),
            PeopleNavigationModule.moreMenu(),
        ),
    )

    @Test
    fun theRegisterSheetOffersExpenseIncomeAndTransferInThatOrder() {
        assertEquals(listOf("expense", "income", "transfer"), registerActions.actions.map { it.id })
        assertEquals(listOf(ExpenseEditor(), IncomeEditor(), TransferEditor), registerActions.actions.map { it.route })
        assertEquals(true, registerActions.hasActions)
    }

    @Test
    fun theMoreTabListsAccountsAndCatalogsBeforePrivacyAndSettings() {
        assertEquals(
            listOf("accounts", "categories", "tags", "people", "income_sources", "privacy", "settings"),
            moreEntries.entries.map { it.id },
        )
        assertEquals(
            listOf(Accounts, Categories, Tags, People, IncomeSources, Privacy, Settings),
            moreEntries.entries.map { it.route },
        )
    }

    @Test
    fun editorsWithoutAnIdCreateAndWithAnIdCorrect() {
        assertEquals(null, ExpenseEditor().id)
        assertEquals("m1", ExpenseEditor("m1").id)
        assertEquals(null, IncomeEditor().id)
        assertEquals("m1", IncomeEditor("m1").id)
    }

    @Test
    fun everyNewRouteSurvivesSavingAndRestoringTheTabState() {
        val routes = listOf<Route>(
            MovementDetail("m1"), ExpenseEditor(), ExpenseEditor("m1"), IncomeEditor("m2"), TransferEditor,
            Accounts, AccountDetail("a1"), Categories, Tags, People, IncomeSources,
        )
        routes.forEach { route ->
            val state = TabBackStacks().select(MainTab.MOVEMENTS).push(route)
            val json = Json.encodeToString(TabBackStacks.serializer(), state)
            val restored = Json.decodeFromString(TabBackStacks.serializer(), json)
            assertEquals(state, restored)
            assertEquals(listOf(Movements, route), restored.activeStack)
        }
    }

    @Test
    fun theNavigatorForwardsPushAndBack() {
        val seen = mutableListOf<String>()
        val navigator = object : Navigator {
            override fun push(route: Route) { seen += "push:$route" }
            override fun back() { seen += "back" }
        }
        navigator.push(MovementDetail("m1"))
        navigator.back()
        assertEquals(listOf("push:MovementDetail(id=m1)", "back"), seen)
    }
}
