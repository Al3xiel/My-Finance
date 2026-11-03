package com.al3xiel.my_finances.feature.reports.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.feature.reports.domain.model.CsvReportRow
import com.al3xiel.my_finances.feature.reports.domain.model.PdfReportData
import com.al3xiel.my_finances.feature.reports.domain.model.PdfSection
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** La escritura por `ContentResolver` hacia el destino elegido (RX2), probada con un archivo propio de la prueba. */
@RunWith(AndroidJUnit4::class)
class ReportFileRepositoryImplTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = ReportFileRepositoryImpl(context, Dispatchers.Unconfined)
    private lateinit var target: File

    @Before fun setUp() {
        target = File(context.cacheDir, "report-test-${System.nanoTime()}.out")
    }

    @After fun tearDown() {
        target.delete()
    }

    private fun uri() = Uri.fromFile(target).toString()

    private val row = CsvReportRow(LocalDate.of(2026, 10, 5), "BBVA", "Comida", listOf("Viaje"), 1_250, "EXPENSE", "nota")

    @Test fun csvIsWrittenToTheChosenDestination() = runBlocking {
        assertTrue(repository.writeCsv(uri(), listOf(row), includeNotes = false))
        val text = target.readText(Charsets.UTF_8)
        assertTrue(text.startsWith("﻿Fecha,Cuenta"))
        assertTrue(text.contains("2026-10-05,BBVA,Comida,Viaje,12.50,EXPENSE"))
        assertFalse(text.contains("nota"))
    }

    @Test fun pdfIsWrittenToTheChosenDestination() = runBlocking {
        val data = PdfReportData("Reporte", "octubre 2026", "Este archivo no está cifrado", listOf(PdfSection("Resumen", listOf("a", "b"), listOf(listOf("x", "1")))))
        assertTrue(repository.writePdf(uri(), data))
        assertEquals("%PDF", String(target.readBytes(), 0, 4, Charsets.ISO_8859_1))
    }

    @Test fun anUnreachableDestinationReportsFailureAndLeavesNothing() = runBlocking {
        val missing = Uri.fromFile(File(context.cacheDir, "no-such-dir/report.csv")).toString()
        assertFalse(repository.writeCsv(missing, listOf(row), includeNotes = false))
        assertFalse(File(context.cacheDir, "no-such-dir/report.csv").exists())
    }
}
