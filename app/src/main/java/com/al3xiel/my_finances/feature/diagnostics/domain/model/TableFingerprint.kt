package com.al3xiel.my_finances.feature.diagnostics.domain.model

/** Cuántas filas tiene una tabla de proyección y un resumen (hash) de su contenido, independiente del orden de las filas. */
data class TableFingerprint(val table: String, val rows: Int, val hash: String)

/** Antes y después de re-proyectar; `null` en un lado = la tabla no existía ahí. */
data class FingerprintChange(val table: String, val beforeRows: Int?, val afterRows: Int?, val changed: Boolean)

/** Un evento tal como está guardado, para la lista de eventos crudos de S73. */
data class RawEventLine(
    val sequence: Long,
    val type: String,
    val aggregateType: String,
    val aggregateId: String,
    val occurredOn: String,
    val payload: String,
)

data class RawEventPage(val items: List<RawEventLine>, val hasMore: Boolean)
