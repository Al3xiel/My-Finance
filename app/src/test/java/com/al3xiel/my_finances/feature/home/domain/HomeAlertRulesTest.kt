package com.al3xiel.my_finances.feature.home.domain

import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert
import com.al3xiel.my_finances.feature.home.domain.rules.HomeAlertRules
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAlertRulesTest {
    private val review = ReconciliationId("r-review")
    private val open = ReconciliationId("r-open")
    private val income = ExpectedIncomeId("e1")

    @Test
    fun noCauseNoAlert() {
        assertEquals(emptyList<HomeAlert>(), HomeAlertRules.build(null, null, null, backupOverdue = false))
    }

    @Test
    fun keepsTheFixedOrder() {
        val alerts = HomeAlertRules.build(review, open, income, backupOverdue = false)

        assertEquals(listOf(HomeAlert.NeedsReview(review), HomeAlert.OpenReconciliation(open), HomeAlert.OverdueIncome(income)), alerts)
    }

    @Test
    fun showsAtMostThreeAndDropsTheLastOne() {
        val alerts = HomeAlertRules.build(review, open, income, backupOverdue = true)

        assertEquals(3, alerts.size)
        assertEquals(listOf(HomeAlert.NeedsReview(review), HomeAlert.OpenReconciliation(open), HomeAlert.OverdueIncome(income)), alerts)
    }

    @Test
    fun theBackupAlertAppearsWhenRoomIsLeft() {
        assertEquals(listOf(HomeAlert.OpenReconciliation(open), HomeAlert.BackupOverdue), HomeAlertRules.build(null, open, null, backupOverdue = true))
    }
}
