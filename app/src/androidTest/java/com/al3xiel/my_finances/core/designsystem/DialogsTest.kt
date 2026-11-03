package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DialogsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun theNoticeAcceptButtonIsAPillOfAtLeast48dp() {
        var dismissed = 0
        rule.setContent { MyfinancesTheme { NoticeDialog(BusinessError.BalanceNotZero, onDismiss = { dismissed++ }) } }
        rule.onNodeWithText(string(R.string.common_accept)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, dismissed)
    }

    @Test fun theNameDialogCannotSaveABlankName() {
        rule.setContent { MyfinancesTheme { NameDialog("Nueva", name = " ", onNameChange = {}, onConfirm = {}, onDismiss = {}) } }
        rule.onNodeWithText(string(R.string.catalog_save)).assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
    }

    @Test fun theNameDialogSavesAFilledName() {
        var saves = 0
        rule.setContent { MyfinancesTheme { NameDialog("Nueva", name = "Comida", onNameChange = {}, onConfirm = { saves++ }, onDismiss = {}) } }
        rule.onNodeWithText(string(R.string.catalog_save)).assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test fun theReasonDialogNeedsAReason() {
        rule.setContent { MyfinancesTheme { ReasonDialog("Anular", onConfirm = {}, onDismiss = {}) } }
        rule.onNodeWithText(string(R.string.reason_dialog_confirm)).assertIsNotEnabled()
    }

    @Test fun theNegativeBalanceDialogOffersConfirmAndCancel() {
        val calls = mutableListOf<String>()
        rule.setContent {
            MyfinancesTheme {
                NegativeBalanceDialog(BusinessError.WouldGoNegative(-5_000), onConfirm = { calls += "ok" }, onDismiss = { calls += "no" })
            }
        }
        rule.onNodeWithText(string(R.string.editor_negative_confirm)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("ok"), calls)
    }

    @Test fun theDateChooserConfirmIsAPillOfAtLeast48dp() {
        rule.setContent { MyfinancesTheme { DateChooserDialog(onDateSelected = {}, onDismiss = {}, initialDate = java.time.LocalDate.of(2026, 10, 26)) } }
        rule.onNodeWithText(string(R.string.dialog_date_confirm)).assertHeightIsAtLeast(48.dp)
    }
}
