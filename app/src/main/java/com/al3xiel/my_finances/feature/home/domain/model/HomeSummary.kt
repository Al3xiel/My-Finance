package com.al3xiel.my_finances.feature.home.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview

/** Lo que muestra Inicio (S10), todo derivado de proyecciones ya consistentes (docs/04 §5.5). */
data class HomeSummary(
    val accountsBalance: Money,
    val reserved: Money,
    /** Puede ser negativo: los fondos apartan más de lo que hay en cuentas. */
    val freeAvailable: Money,
    val receivable: Money,
    /** Solo cuentas no archivadas. */
    val accounts: List<AccountWithBalance>,
    /** Solo fondos `ACTIVE`. */
    val funds: List<FundOverview>,
    val nextIncome: ExpectedIncome?,
    val alerts: List<HomeAlert>,
)

/** Aviso derivado y no descartable; desaparece solo cuando se resuelve su causa (D58). */
sealed interface HomeAlert {
    data class NeedsReview(val reconciliationId: ReconciliationId) : HomeAlert

    data class OpenReconciliation(val reconciliationId: ReconciliationId) : HomeAlert

    data class OverdueIncome(val expectedIncomeId: ExpectedIncomeId) : HomeAlert

    data object BackupOverdue : HomeAlert
}
