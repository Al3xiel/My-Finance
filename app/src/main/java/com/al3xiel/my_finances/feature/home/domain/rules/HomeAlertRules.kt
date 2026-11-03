package com.al3xiel.my_finances.feature.home.domain.rules

import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert

/** Orden fijo de las alertas de Inicio y tope de visibles (docs/05 S10, D58). */
object HomeAlertRules {
    const val MAX_VISIBLE = 3

    fun build(
        needsReviewId: ReconciliationId?,
        openReconciliationId: ReconciliationId?,
        overdueIncomeId: ExpectedIncomeId?,
        backupOverdue: Boolean,
    ): List<HomeAlert> = listOfNotNull(
        needsReviewId?.let(HomeAlert::NeedsReview),
        openReconciliationId?.let(HomeAlert::OpenReconciliation),
        overdueIncomeId?.let(HomeAlert::OverdueIncome),
        HomeAlert.BackupOverdue.takeIf { backupOverdue },
    ).take(MAX_VISIBLE)
}
