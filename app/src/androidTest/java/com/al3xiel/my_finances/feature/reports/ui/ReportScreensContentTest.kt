package com.al3xiel.my_finances.feature.reports.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.text.formatBasisPoints
import com.al3xiel.my_finances.feature.reports.domain.model.OthersSpend
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.ReportSummary
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Contenido sin estado de S40, S41 y S45: lo que se ve y las acciones que despacha. */
class ReportScreensContentTest {
    @get:Rule val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val period = ReportPeriod.Month(YearMonth.of(2026, 10))
    private val today = LocalDate.of(2026, 11, 2)

    @Test fun summaryShowsTheFiguresAndOpensTheDetailScreens() {
        val opened = mutableListOf<String>()
        rule.setContent {
            ReportSummaryContent(
                state = ReportSummaryUiState(period, today, ReportSummary(50_000, 21_050, OthersSpend(17_000, 0), -3_000)),
                onAction = {},
                onOpenByCategory = { opened += "category" },
                onOpenByTag = { opened += "tag" },
                onOpenByAccount = { opened += "account" },
                onOpenOrigin = { opened += "origin" },
                onExport = { opened += "export" },
            )
        }
        rule.onNodeWithText(string(R.string.report_summary_own_spend)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.report_summary_unidentified)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.report_summary_by_category)).performClick()
        rule.onNodeWithText(string(R.string.report_summary_origin)).performScrollTo().performClick()
        rule.onNodeWithText(string(R.string.report_summary_export)).performScrollTo().performClick()
        assertEquals(listOf("category", "origin", "export"), opened)
    }

    @Test fun summaryHidesTheUnidentifiedLineWhenThereIsNone() {
        rule.setContent {
            ReportSummaryContent(
                state = ReportSummaryUiState(period, today, ReportSummary(0, 0, OthersSpend(0, 0), 0)),
                onAction = {}, onOpenByCategory = {}, onOpenByTag = {}, onOpenByAccount = {}, onOpenOrigin = {}, onExport = {},
            )
        }
        rule.onNodeWithText(string(R.string.report_summary_unidentified)).assertDoesNotExist()
    }

    @Test fun tappingACategoryOpensItsMovementsAndAnEmptyPeriodExplainsIt() {
        var opened: String? = null
        rule.setContent {
            ReportByCategoryContent(
                state = ReportByCategoryUiState(period, listOf(CategoryRowUi("food", "Comida", 60_000, 10_000)), 60_000, loaded = true),
                onAction = {}, onOpenMovements = { opened = it },
            )
        }
        rule.onNodeWithText("Comida").performClick()
        assertEquals("food", opened)
    }

    @Test fun anEmptyCategoryReportShowsTheEmptyState() {
        rule.setContent {
            ReportByCategoryContent(ReportByCategoryUiState(period, loaded = true), onAction = {}, onOpenMovements = {})
        }
        rule.onNodeWithText(string(R.string.report_by_category_empty_title)).assertIsDisplayed()
    }

    @Test fun exportShowsTheUnencryptedNoticeBeforeAnythingIsWritten() {
        val actions = mutableListOf<ReportExportUiAction>()
        rule.setContent {
            ReportExportContent(ReportExportUiState(period, phase = ExportPhase.NOTICE), onAction = { actions += it })
        }
        rule.onNodeWithText(string(R.string.report_export_notice_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.report_export_notice_continue)).performClick()
        assertTrue(actions.single() is ReportExportUiAction.AcceptNotice)
    }

    @Test fun includeNotesIsOnlyOfferedForCsv() {
        rule.setContent {
            ReportExportContent(ReportExportUiState(period, format = ExportFormat.PDF), onAction = {})
        }
        rule.onNodeWithText(string(R.string.report_export_include_notes)).assertDoesNotExist()
    }

    @Test fun eachScreenShowsItsOwnTitleInTheHeader() {
        rule.setContent {
            ReportSummaryContent(
                state = ReportSummaryUiState(period, today, ReportSummary(1, 1, OthersSpend(0, 0), 0)),
                onAction = {}, onOpenByCategory = {}, onOpenByTag = {}, onOpenByAccount = {}, onOpenOrigin = {}, onExport = {},
            )
        }
        rule.onNodeWithText(string(R.string.report_summary_title)).assertIsDisplayed()
    }

    @Test fun theCategoryReportHasItsTitleAndOpensMovementsFromTheChartLabel() {
        var opened: String? = null
        rule.setContent {
            ReportByCategoryContent(
                state = ReportByCategoryUiState(
                    period,
                    listOf(CategoryRowUi("food", "Comida", 60_000, 6_000), CategoryRowUi("home", "Hogar", 40_000, 4_000)),
                    100_000,
                    loaded = true,
                ),
                onAction = {}, onOpenMovements = { opened = it },
            )
        }
        rule.onNodeWithText(string(R.string.report_by_category_title)).assertIsDisplayed()
        rule.onNodeWithText("Hogar " + formatBasisPoints(4_000)).performClick()
        assertEquals("home", opened)
    }

    @Test fun theTagReportHasItsTitle() {
        rule.setContent {
            ReportByTagContent(ReportByTagUiState(period, listOf(TagRowUi("t1", "Viaje", 80_000, emptyList())), loaded = true), onAction = {}, onOpenMovements = {})
        }
        rule.onNodeWithText(string(R.string.report_by_tag_title)).assertIsDisplayed()
    }

    @Test fun theAccountReportHasItsTitle() {
        rule.setContent {
            ReportByAccountContent(
                ReportByAccountUiState(period, listOf(AccountFlowRowUi("bbva", "BBVA", 5, 1, 0)), loaded = true),
                onOpenMovements = {},
            )
        }
        rule.onNodeWithText(string(R.string.report_by_account_title)).assertIsDisplayed()
    }

    @Test fun exportHasItsTitle() {
        rule.setContent { ReportExportContent(ReportExportUiState(period), onAction = {}) }
        rule.onNodeWithText(string(R.string.report_export_title)).assertIsDisplayed()
    }
}
