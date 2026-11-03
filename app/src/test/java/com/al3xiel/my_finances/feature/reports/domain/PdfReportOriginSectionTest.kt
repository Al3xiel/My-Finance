package com.al3xiel.my_finances.feature.reports.domain

import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRecord
import com.al3xiel.my_finances.feature.reports.domain.model.OriginBalanceRow
import com.al3xiel.my_finances.feature.reports.domain.model.ReportExportTexts
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.usecase.BuildReportDocumentsUseCase
import com.al3xiel.my_finances.feature.reports.ui.FakeOriginRepository
import com.al3xiel.my_finances.feature.reports.ui.FakeReportRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReportOriginSectionTest {
    private val f = MovementFixture()
    private val origin = FakeOriginRepository()
    private val period = ReportPeriod.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val texts = ReportExportTexts(
        "T", "P", "N", "S", "C", "Tg", "A", "i", "o", "t", "r", "p", "u", "n", "m", "%", "in", "out", "tr",
        originHeading = "Origen", originSpendHeading = "Gasto por fuente", estimated = "estimado",
        columnSource = "Fuente", columnTarget = "Destino", loans = "Préstamos", unidentifiedTarget = "No identificado",
    )

    init {
        f.sources.sources[IncomeSourceId("s2")] = IncomeSource(IncomeSourceId("s2"), "Freelance", IncomeSourceKind.REGULAR, null)
    }

    private fun build() = BuildReportDocumentsUseCase(FakeReportRepository(), f.accounts, f.categories, f.tags, origin, f.sources)

    private fun sections() = runBlocking { build().pdf(period, texts).sections }

    @Test fun `without origin data the pdf has no origin sections`() {
        assertEquals(4, sections().size)
    }

    @Test fun `composition lists account and source with amount and share, labelled estimated`() {
        origin.balances = listOf(OriginBalanceRow("bbva", "s1", 750), OriginBalanceRow("bbva", "s2", 250))
        val section = sections().first { it.heading.startsWith("Origen") }
        assertTrue(section.heading.contains("estimado"))
        assertEquals("BBVA · Sueldo", section.rows[0][0])
        assertTrue(section.rows[0][1].contains("7.50") || section.rows[0][1].contains("7,50"))
        assertTrue(section.rows[0][2].startsWith("75"))
        assertEquals("BBVA · Freelance", section.rows[1][0])
    }

    @Test fun `spend of each source lists account and destination, with loans and unidentified named`() {
        origin.records = listOf(
            OriginAttributionRecord("s1", "bbva", LedgerEntryKind.EXPENSE, "food", null, 500),
            OriginAttributionRecord("s1", "bbva", LedgerEntryKind.LOAN_OUT, null, null, 300),
            OriginAttributionRecord("s2", "cash", LedgerEntryKind.ADJUSTMENT, null, null, 100),
        )
        val section = sections().first { it.heading.startsWith("Gasto por fuente") }
        assertTrue(section.heading.contains("estimado"))
        assertEquals(
            listOf("Freelance · Efectivo · No identificado", "Sueldo · BBVA · Comida", "Sueldo · BBVA · Préstamos"),
            section.rows.map { it[0] },
        )
    }
}
