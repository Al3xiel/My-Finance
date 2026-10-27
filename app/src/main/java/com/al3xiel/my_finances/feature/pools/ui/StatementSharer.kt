package com.al3xiel.my_finances.feature.pools.ui

import android.content.Intent

/**
 * Dibuja la rendición en un archivo temporal y arma el Intent para compartirla. La UI no conoce `data/`: la
 * implementación (`StatementSharerImpl`) se inyecta. Devuelve `null` si no se pudo escribir el archivo.
 */
fun interface StatementSharer {
    suspend fun shareIntent(request: ShareRequest): Intent?
}
