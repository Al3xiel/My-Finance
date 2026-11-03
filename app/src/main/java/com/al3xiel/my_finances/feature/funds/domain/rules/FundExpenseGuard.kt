package com.al3xiel.my_finances.feature.funds.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.usecase.FundReferenceGuard
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import java.time.LocalDate
import javax.inject.Inject

/** Lo que un fondo le exige a un gasto: activo, fecha dentro de su rango y no anterior a su último arqueo (D21). */
class FundExpenseGuard @Inject constructor(
    private val funds: FundRepository,
) : FundReferenceGuard {
    override suspend fun check(fundId: FundId, occurredOn: LocalDate, unchanged: Boolean): BusinessError? {
        val fund = funds.find(fundId) ?: return BusinessError.NotFound
        return if (unchanged) null else FundRules.checkExpenseDate(FundRules.termsOf(fund), occurredOn)
    }
}
