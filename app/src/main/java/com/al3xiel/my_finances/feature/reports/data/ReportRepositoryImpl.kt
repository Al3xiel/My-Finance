package com.al3xiel.my_finances.feature.reports.data

import com.al3xiel.my_finances.data.projection.report.ReportQueryDao
import com.al3xiel.my_finances.feature.reports.domain.model.LedgerRow
import com.al3xiel.my_finances.feature.reports.domain.model.PartDetail
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.ReportSummary
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.ReportCalculator
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class ReportRepositoryImpl @Inject constructor(
    private val dao: ReportQueryDao,
    private val clock: Clock,
) : ReportRepository {
    override suspend fun parts(period: ReportPeriod): List<PartRow> {
        val closing = period.closingOn(LocalDate.now(clock))
        val tags = dao.partTags(period.from.toString(), period.to.toString()).groupBy({ it.partId }, { it.tagId })
        return dao.parts(period.from.toString(), period.to.toString(), closing.toString()).map {
            PartRow(it.partId, it.accountId, it.categoryId, it.parentId, it.amount, it.hasPool, it.reimbursed, tags[it.partId].orEmpty())
        }
    }

    override suspend fun ledger(period: ReportPeriod): List<LedgerRow> =
        dao.ledger(period.from.toString(), period.to.toString()).map { LedgerRow(it.accountId, it.kind, it.delta) }

    override suspend fun partDetails(period: ReportPeriod): Map<String, PartDetail> =
        dao.partDetails(period.from.toString(), period.to.toString())
            .associate { it.partId to PartDetail(LocalDate.parse(it.occurredOn), it.kind, it.note) }

    override suspend fun summary(period: ReportPeriod): ReportSummary = ReportCalculator.summary(parts(period), ledger(period))
}
