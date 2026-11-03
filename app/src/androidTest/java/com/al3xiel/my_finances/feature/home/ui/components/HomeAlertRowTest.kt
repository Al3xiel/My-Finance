package com.al3xiel.my_finances.feature.home.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeAlertRowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun eachAlertSaysWhatIsWrong() {
        val cases = listOf(
            HomeAlert.NeedsReview(ReconciliationId("r")) to R.string.home_alert_needs_review,
            HomeAlert.OpenReconciliation(ReconciliationId("r")) to R.string.home_alert_open_reconciliation,
            HomeAlert.OverdueIncome(ExpectedIncomeId("e")) to R.string.home_alert_overdue_income,
            HomeAlert.BackupOverdue to R.string.home_alert_backup_overdue,
        )
        var current by mutableStateOf<HomeAlert>(cases.first().first)
        rule.setContent { HomeAlertRow(current, onClick = {}) }
        for ((alert, text) in cases) {
            current = alert
            rule.onNodeWithText(string(text)).assertIsDisplayed()
        }
    }

    @Test
    fun tappingTheRowReportsTheAlert() {
        val alert = HomeAlert.BackupOverdue
        val tapped = mutableListOf<HomeAlert>()
        rule.setContent { HomeAlertRow(alert, onClick = { tapped += alert }) }

        rule.onNodeWithText(string(R.string.home_alert_backup_overdue)).performClick()

        assertEquals(listOf(alert), tapped)
    }
}
