package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.feature.diagnostics.domain.model.TableFingerprint

/** Resume cada tabla de proyección de la base real (no el registro de eventos). */
fun interface TableFingerprintSource {
    suspend fun fingerprints(): List<TableFingerprint>
}
