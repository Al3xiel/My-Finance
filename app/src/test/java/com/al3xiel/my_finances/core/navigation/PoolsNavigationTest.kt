package com.al3xiel.my_finances.core.navigation

import com.al3xiel.my_finances.feature.accounts.di.AccountsNavigationModule
import com.al3xiel.my_finances.feature.movements.di.MovementsNavigationModule
import com.al3xiel.my_finances.feature.pools.di.PoolsNavigationModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoolsNavigationTest {
    private val registerActions = RegisterActionRegistry(
        setOf(MovementsNavigationModule.registerActions(), AccountsNavigationModule.registerActions(), PoolsNavigationModule.registerActions()),
    )

    @Test
    fun theRegisterSheetOffersTheReimbursementAfterTheTransfer() {
        assertEquals(listOf("expense", "income", "transfer", "reimbursement"), registerActions.actions.map { it.id })
        assertEquals(ReimbursementEditor(), registerActions.actions.last().route)
    }

    @Test
    fun theReimbursementActionStartsWithoutAPersonOrAPool() {
        val route = registerActions.actions.single { it.id == "reimbursement" }.route as ReimbursementEditor

        assertNull(route.personId)
        assertNull(route.poolId)
    }

    @Test
    fun anExpenseEditorCanBeOpenedFromAPoolWithItPreselected() {
        val route = ExpenseEditor(presetPoolId = "dogs")

        assertNull(route.id)
        assertEquals("dogs", route.presetPoolId)
        assertEquals(ExpenseEditor(), ExpenseEditor(id = null, presetPoolId = null))
    }

    @Test
    fun everyPoolRouteSurvivesSavingAndRestoringTheTabState() {
        val routes = listOf<Route>(
            PoolDetail("dogs"), PoolEditor(), PoolEditor("dogs"), SettlementStatement("dogs"),
            ReimbursementEditor(), ReimbursementEditor("mom", "dogs"), PersonDetail("mom"),
            LoanEditor(), LoanEditor("pepe"), LoanDetail("l1"), ExpenseEditor(presetPoolId = "dogs"),
        )
        routes.forEach { route ->
            val state = TabBackStacks().select(MainTab.RECEIVABLES).push(route)
            val restored = Json.decodeFromString(TabBackStacks.serializer(), Json.encodeToString(TabBackStacks.serializer(), state))
            assertEquals(state, restored)
            assertEquals(listOf(Receivables, route), restored.activeStack)
        }
    }
}
