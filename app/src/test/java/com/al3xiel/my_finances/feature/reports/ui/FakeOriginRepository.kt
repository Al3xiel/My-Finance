package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.feature.reports.domain.model.AccountOrigin
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRecord
import com.al3xiel.my_finances.feature.reports.domain.model.OriginBalanceRow
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.repository.OriginRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.OriginCalculator

class FakeOriginRepository : OriginRepository {
    var balances: List<OriginBalanceRow> = emptyList()
    var records: List<OriginAttributionRecord> = emptyList()
    override suspend fun composition(): List<AccountOrigin> = OriginCalculator.composition(balances)
    override suspend fun attributions(period: ReportPeriod) = records
}
