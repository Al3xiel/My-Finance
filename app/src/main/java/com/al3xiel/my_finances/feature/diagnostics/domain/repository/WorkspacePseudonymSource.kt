package com.al3xiel.my_finances.feature.diagnostics.domain.repository

/** Los seudónimos que aparecen en el paquete abierto en el espacio; con ellos se arma una solicitud de diccionario. */
fun interface WorkspacePseudonymSource {
    suspend fun pseudonyms(): List<String>
}
