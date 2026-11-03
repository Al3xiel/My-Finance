package com.al3xiel.my_finances.core.navigation

import com.al3xiel.my_finances.core.designsystem.AppIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RegisterSheetTest {
    @Test fun anActionWithoutItsOwnIconUsesTheAddIcon() {
        assertEquals(AppIcons.Add, RegisterAction("income", 1, Privacy, order = 1).iconRes)
    }

    @Test fun anActionCarriesItsOwnIcon() {
        val action = RegisterAction("expense", 1, Privacy, order = 1, iconRes = AppIcons.ActionExpense)
        assertNotEquals(AppIcons.Add, action.iconRes)
    }
}
