package com.al3xiel.my_finances.feature.reports.data

import com.al3xiel.my_finances.data.projection.origin.OriginQueryDao
import com.al3xiel.my_finances.feature.reports.domain.model.AccountOrigin
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRecord
import com.al3xiel.my_finances.feature.reports.domain.model.OriginBalanceRow
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.repository.OriginRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.OriginCalculator
import javax.inject.Inject

class OriginRepositoryImpl @Inject constructor(private val dao: OriginQueryDao) : OriginRepository {
    override suspend fun composition(): List<AccountOrigin> =
        OriginCalculator.composition(dao.balances().map { OriginBalanceRow(it.accountId, it.incomeSourceId, it.amountMinor) })

    override suspend fun attributions(period: ReportPeriod): List<OriginAttributionRecord> =
        dao.attributions(period.from.toString(), period.to.toString()).map {
            OriginAttributionRecord(it.sourceId, it.accountId, it.kind, it.categoryId, it.parentId, it.amountMinor)
        }
}
