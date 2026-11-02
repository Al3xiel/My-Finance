package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.feature.reports.domain.model.LedgerRow
import com.al3xiel.my_finances.feature.reports.domain.model.PartDetail
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.ReportSummary
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.ReportCalculator

class FakeReportRepository : ReportRepository {
    var parts: List<PartRow> = emptyList()
    var ledger: List<LedgerRow> = emptyList()
    override suspend fun parts(period: ReportPeriod) = parts
    override suspend fun ledger(period: ReportPeriod) = ledger
    var details: Map<String, PartDetail> = emptyMap()
    override suspend fun partDetails(period: ReportPeriod) = details
    override suspend fun summary(period: ReportPeriod): ReportSummary = ReportCalculator.summary(parts, ledger)
}
