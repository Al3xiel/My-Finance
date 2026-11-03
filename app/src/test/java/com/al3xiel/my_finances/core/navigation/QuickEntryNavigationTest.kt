package com.al3xiel.my_finances.core.navigation

import com.al3xiel.my_finances.feature.accounts.di.AccountsNavigationModule
import com.al3xiel.my_finances.feature.movements.di.MovementsNavigationModule
import com.al3xiel.my_finances.feature.pools.di.PoolsNavigationModule
import com.al3xiel.my_finances.feature.quickentry.di.QuickEntryNavigationModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickEntryNavigationTest {
    private val registerActions = RegisterActionRegistry(
        setOf(
            QuickEntryNavigationModule.registerActions(),
            PoolsNavigationModule.registerActions(),
            AccountsNavigationModule.registerActions(),
            MovementsNavigationModule.registerActions(),
        ),
    )

    @Test
    fun theRegisterSheetOffersTheQuickEntryLastAndInOrder() {
        assertEquals(listOf("expense", "income", "transfer", "reimbursement", "quick_entry"), registerActions.actions.map { it.id })
        assertEquals(QuickEntry, registerActions.actions.last().route)
    }

    @Test
    fun theQuickEntryRouteSurvivesSavingAndRestoringTheTabState() {
        val state = TabBackStacks().select(MainTab.RECEIVABLES).push(QuickEntry)
        val restored = Json.decodeFromString(TabBackStacks.serializer(), Json.encodeToString(TabBackStacks.serializer(), state))
        assertEquals(state, restored)
        assertEquals(listOf(Receivables, QuickEntry), restored.activeStack)
    }
}
