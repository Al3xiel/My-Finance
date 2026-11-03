package com.al3xiel.my_finances.feature.reports.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.core.text.formatBasisPoints
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.reports.domain.model.CsvReportRow
import com.al3xiel.my_finances.feature.reports.domain.model.PdfReportData
import com.al3xiel.my_finances.feature.reports.domain.model.PdfSection
import com.al3xiel.my_finances.feature.reports.domain.model.ReportExportTexts
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.repository.OriginRepository
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.OriginCalculator
import com.al3xiel.my_finances.feature.reports.domain.rules.ReportCalculator
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Arma lo que se exporta (D38): las filas del CSV, una por parte de gasto, y el PDF con las tablas del reporte. */
class BuildReportDocumentsUseCase @Inject constructor(
    private val reports: ReportRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val origin: OriginRepository,
    private val sources: IncomeSourceRepository,
) {
    suspend fun csvRows(period: ReportPeriod): List<CsvReportRow> {
        val parts = reports.parts(period)
        val details = reports.partDetails(period)
        val accountNames = accounts.observeAccounts().first().associate { it.account.id.value to it.account.name }
        val categoryNames = categories.observeAll().first().associate { it.id.value to it.name }
        val tagNames = tags.observeAll().first().associate { it.id.value to it.name }
        return parts.filter { it.ownAmount > 0 }.mapNotNull { p ->
            val d = details[p.partId] ?: return@mapNotNull null
            CsvReportRow(
                date = d.date,
                account = accountNames[p.accountId] ?: p.accountId,
                category = categoryNames[p.categoryId] ?: p.categoryId,
                tags = p.tagIds.map { tagNames[it] ?: it },
                ownAmountMinor = p.ownAmount,
                type = if (d.kind == PartKind.NORMAL) "EXPENSE" else d.kind.name,
                note = d.note,
            )
        }.sortedWith(compareBy({ it.date }, { it.account }, { it.category }))
    }

    suspend fun pdf(period: ReportPeriod, t: ReportExportTexts): PdfReportData {
        val parts = reports.parts(period)
        val ledger = reports.ledger(period)
        val accountNames = accounts.observeAccounts().first().associate { it.account.id.value to it.account.name }
        val categoryNames = categories.observeAll().first().associate { it.id.value to it.name }
        val tagNames = tags.observeAll().first().associate { it.id.value to it.name }
        val summary = ReportCalculator.summary(parts, ledger)
        fun money(minor: Long) = MoneyFormatter.format(Money(minor, CurrencyCode.PEN))
        val summaryRows = listOf(
            listOf(t.income, money(summary.income)),
            listOf(t.ownSpend, money(summary.ownSpend)),
            listOf(t.othersTotal, money(summary.others.total)),
            listOf(t.othersReimbursed, money(summary.others.reimbursed)),
            listOf(t.othersPending, money(summary.others.pending)),
            listOf(t.unidentified, money(summary.unidentified)),
        )
        return PdfReportData(
            title = t.title,
            subtitle = t.periodLabel,
            notice = t.notice,
            sections = listOf(
                PdfSection(t.summaryHeading, listOf(t.columnName, t.columnAmount), summaryRows),
                PdfSection(
                    t.categoryHeading, listOf(t.columnName, t.columnAmount, t.columnShare),
                    ReportCalculator.byCategory(parts).map { listOf(categoryNames[it.categoryId] ?: it.categoryId, money(it.amount), formatBasisPoints(it.basisPoints)) },
                ),
                PdfSection(
                    t.tagHeading, listOf(t.columnName, t.columnAmount),
                    ReportCalculator.byTag(parts).map { listOf(tagNames[it.tagId] ?: it.tagId, money(it.amount)) },
                ),
                PdfSection(
                    t.accountHeading, listOf(t.columnName, t.columnInflow, t.columnOutflow, t.columnTransfers),
                    ReportCalculator.byAccount(ledger).map {
                        listOf(accountNames[it.accountId] ?: it.accountId, money(it.inflow), money(it.outflow), money(it.transfersNet))
                    },
                ),
            ) + originSections(period, t, accountNames, categoryNames, ::money),
        )
    }

    /** Composición por cuenta y gasto de cada fuente; ambas llevan "estimado" y faltan si no hay datos de origen. */
    private suspend fun originSections(
        period: ReportPeriod,
        t: ReportExportTexts,
        accountNames: Map<String, String>,
        categoryNames: Map<String, String>,
        money: (Long) -> String,
    ): List<PdfSection> {
        val sourceNames = sources.observeAll().first().associate { it.id.value to it.name }
        val records = origin.attributions(period)
        val composition = origin.composition()
        val compositionRows = composition.flatMap { account ->
            account.slices.map {
                listOf(
                    "${accountNames[account.accountId] ?: account.accountId} · ${sourceNames[it.sourceId] ?: it.sourceId}",
                    money(it.amountMinor), formatBasisPoints(it.basisPoints),
                )
            }
        }
        val spendRows = records.map { it.sourceId }.distinct().sortedBy { sourceNames[it] ?: it }.flatMap { source ->
            OriginCalculator.spendOf(source, records).map {
                val target = it.categoryId?.let { c -> categoryNames[c] ?: c }
                    ?: if (it.kind == LedgerEntryKind.LOAN_OUT) t.loans else t.unidentifiedTarget
                listOf("${sourceNames[source] ?: source} · ${accountNames[it.accountId] ?: it.accountId} · $target", money(it.amountMinor))
            }
        }
        return listOfNotNull(
            PdfSection("${t.originHeading} (${t.estimated})", listOf(t.columnName, t.columnAmount, t.columnShare), compositionRows)
                .takeIf { compositionRows.isNotEmpty() },
            PdfSection("${t.originSpendHeading} (${t.estimated})", listOf("${t.columnSource} · ${t.columnName} · ${t.columnTarget}", t.columnAmount), spendRows)
                .takeIf { spendRows.isNotEmpty() },
        )
    }
}
