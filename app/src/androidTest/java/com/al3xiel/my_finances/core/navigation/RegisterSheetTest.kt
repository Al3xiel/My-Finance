package com.al3xiel.my_finances.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RegisterSheetTest {
    @Test fun everyKnownActionHasItsOwnIcon() {
        val ids = listOf("expense", "income", "transfer", "reimbursement", "quick_entry")
        assertEquals(ids.size, ids.map(::registerActionIcon).toSet().size)
    }

    @Test fun anUnknownActionFallsBackToTheAddIcon() {
        assertEquals(registerActionIcon("income"), registerActionIcon("something_new"))
        assertNotEquals(registerActionIcon("expense"), registerActionIcon("something_new"))
    }
}
