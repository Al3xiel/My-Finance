package com.al3xiel.my_finances.core.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Las rutas de conciliación y el `reconciliationId` de los editores: se guardan y se restauran con el estado de las pestañas. */
class ReconciliationNavigationTest {
    @Test
    fun theEditorsTakeAnOptionalReconciliationIdWithoutBreakingTheOldCalls() {
        assertNull(ExpenseEditor().reconciliationId)
        assertNull(IncomeEditor("m1").reconciliationId)
        assertNull(TransferEditor().reconciliationId)
        assertEquals("r1", ExpenseEditor(reconciliationId = "r1").reconciliationId)
        assertEquals("r1", IncomeEditor(reconciliationId = "r1").reconciliationId)
        assertEquals("r1", TransferEditor("r1").reconciliationId)
    }

    @Test
    fun theOtherEditorArgumentsSurviveNextToTheReconciliationId() {
        val expense = ExpenseEditor(id = "m1", presetPoolId = "p", presetFundId = "f", reconciliationId = "r1")
        val income = IncomeEditor(id = "m2", expectedIncomeId = "e1", reconciliationId = "r1")

        assertEquals(listOf("m1", "p", "f", "r1"), listOf(expense.id, expense.presetPoolId, expense.presetFundId, expense.reconciliationId))
        assertEquals(listOf("m2", "e1", "r1"), listOf(income.id, income.expectedIncomeId, income.reconciliationId))
    }

    @Test
    fun leavingS60ForS61DropsS60SoBackFromS61DoesNotBounceToIt() {
        // Lo que hace ReconciliationNavigationModule: back() y luego push(detalle).
        val onS60 = TabBackStacks().select(MainTab.MORE).push(AccountDetail("a1")).push(ReconciliationStart("a1"))
        val onS61 = onS60.back()!!.push(ReconciliationDetail("r1"))

        assertEquals(listOf(More, AccountDetail("a1"), ReconciliationDetail("r1")), onS61.activeStack)
        assertEquals(listOf(More, AccountDetail("a1")), onS61.back()!!.activeStack)
    }

    @Test
    fun everyReconciliationRouteSurvivesSavingAndRestoringTheTabState() {
        val routes = listOf<Route>(
            ReconciliationStart("a1"),
            ReconciliationDetail("r1"),
            ReconciliationResolve("r1"),
            ExpenseEditor(reconciliationId = "r1"),
            ExpenseEditor(id = "m1", reconciliationId = "r1"),
            IncomeEditor(reconciliationId = "r1"),
            TransferEditor("r1"),
            TransferEditor(),
        )
        routes.forEach { route ->
            val state = TabBackStacks().select(MainTab.MOVEMENTS).push(route)
            val json = Json.encodeToString(TabBackStacks.serializer(), state)
            val restored = Json.decodeFromString(TabBackStacks.serializer(), json)
            assertEquals(state, restored)
            assertEquals(listOf(Movements, route), restored.activeStack)
        }
    }
}
