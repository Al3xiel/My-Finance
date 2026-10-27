package com.al3xiel.my_finances.feature.pools.data

import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.feature.pools.domain.model.SettlementStatementContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class RowStyle { TITLE, SUBTITLE, HEADER, LINE, TOTAL, EMPHASIS }

/** Una fila de texto de la rendición; `right` va alineado a la derecha (montos). */
data class StatementRow(val left: String, val right: String, val style: RowStyle)

/** Textos de la rendición; las plantillas usan `%1$s`, `%2$s` como los recursos de Android. */
data class StatementLabels(
    val responsible: String,
    val period: String,
    val header: String,
    val pendingHeader: String,
    val periodPending: String,
    val previousPending: String,
    val grandTotal: String,
)

/**
 * Pasa el contenido de la rendición a filas de texto, igual para imagen y PDF. Solo usa el modelo de la rendición, que
 * no tiene notas ni saldos de cuentas (docs/02 S7).
 */
object StatementLayout {
    private val fullDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val shortDate = DateTimeFormatter.ofPattern("dd/MM")

    fun rows(content: SettlementStatementContent, labels: StatementLabels): List<StatementRow> = buildList {
        add(StatementRow(content.poolName, "", RowStyle.TITLE))
        add(StatementRow(labels.responsible.format(content.responsibleAlias), "", RowStyle.SUBTITLE))
        add(StatementRow(labels.period.format(content.periodFrom.text(), content.generatedOn.text()), "", RowStyle.SUBTITLE))
        add(StatementRow(labels.header, labels.pendingHeader, RowStyle.HEADER))
        content.lines.forEach { line ->
            val concept = (listOf(line.categoryName) + line.tagNames).joinToString(" · ")
            add(StatementRow("${line.occurredOn.format(shortDate)}  $concept", MoneyFormatter.format(line.pending), RowStyle.LINE))
        }
        add(StatementRow(labels.periodPending, MoneyFormatter.format(content.pendingTotal), RowStyle.TOTAL))
        if (content.previousPending.isPositive) {
            add(StatementRow(labels.previousPending, MoneyFormatter.format(content.previousPending), RowStyle.TOTAL))
        }
        add(StatementRow(labels.grandTotal, MoneyFormatter.format(content.grandTotal), RowStyle.EMPHASIS))
    }

    private fun LocalDate.text() = format(fullDate)
}
