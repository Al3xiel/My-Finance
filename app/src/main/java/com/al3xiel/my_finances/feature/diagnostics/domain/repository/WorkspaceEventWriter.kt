package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageEventLine

/** Escribe los eventos de un paquete ya validado en el espacio. */
fun interface WorkspaceEventWriter {
    suspend fun write(events: List<PackageEventLine>)
}
