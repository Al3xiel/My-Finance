package com.al3xiel.my_finances.feature.pools.data

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.feature.pools.domain.model.SettlementStatementContent
import com.al3xiel.my_finances.feature.pools.domain.model.StatementLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatementLayoutTest {
    private val labels = StatementLabels(
        responsible = "Responsable: %1\$s",
        period = "Del %1\$s al %2\$s",
        header = "Gasto",
        pendingHeader = "Pendiente",
        periodPending = "Pendiente del periodo",
        previousPending = "Pendiente de rendiciones anteriores",
        grandTotal = "Total a reembolsar",
    )

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun content(previous: Long = 0, vararg lines: StatementLine) = SettlementStatementContent(
        PoolId("dogs"), PoolPeriodId("dogs-p1"), "Perros – mamá", "Mamá", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
        lines.toList(), pen(lines.sumOf { it.pending.amountMinor }), pen(previous),
    )

    private fun line(day: Int, category: String, pending: Long, vararg tags: String) =
        StatementLine(PartId("p$day"), LocalDate.of(2026, 10, day), category, tags.toList(), pen(pending), pen(pending))

    @Test
    fun headerNamesThePoolTheResponsibleAndThePeriod() {
        val rows = StatementLayout.rows(content(0, line(3, "Mascotas", 12_000)), labels)

        assertEquals(RowStyle.TITLE, rows[0].style)
        assertEquals("Perros – mamá", rows[0].left)
        assertEquals("Responsable: Mamá", rows[1].left)
        assertEquals("Del 01/10/2026 al 31/10/2026", rows[2].left)
    }

    @Test
    fun eachLineShowsDateCategoryTagsAndWhatIsPending() {
        val rows = StatementLayout.rows(content(0, line(3, "Mascotas", 12_000, "Perros", "Vet")), labels)

        val line = rows.single { it.style == RowStyle.LINE }
        assertEquals("03/10  Mascotas · Perros · Vet", line.left)
        assertEquals("S/ 120.00", line.right)
    }

    @Test
    fun totalsShowThePreviousPendingApartOnlyWhenThereIsAny() {
        val withPrevious = StatementLayout.rows(content(30_000, line(3, "Mascotas", 12_000)), labels)
        assertEquals(
            listOf("Pendiente del periodo" to "S/ 120.00", "Pendiente de rendiciones anteriores" to "S/ 300.00", "Total a reembolsar" to "S/ 420.00"),
            withPrevious.filter { it.style == RowStyle.TOTAL || it.style == RowStyle.EMPHASIS }.map { it.left to it.right },
        )

        val without = StatementLayout.rows(content(0, line(3, "Mascotas", 12_000)), labels)
        assertEquals(listOf("Pendiente del periodo"), without.filter { it.style == RowStyle.TOTAL }.map { it.left })
        assertFalse(without.any { it.left == "Pendiente de rendiciones anteriores" })
        assertEquals("S/ 120.00", without.single { it.style == RowStyle.EMPHASIS }.right)
    }

    @Test
    fun theLayoutOnlyCarriesWhatTheStatementModelHolds() {
        val rows = StatementLayout.rows(content(0, line(3, "Mascotas", 12_000)), labels)

        // El modelo no tiene notas, cuentas ni saldos: nada de eso puede aparecer en el texto compartido.
        assertTrue(rows.all { row -> "BBVA" !in row.left + row.right && "nota" !in (row.left + row.right).lowercase() })
    }
}
