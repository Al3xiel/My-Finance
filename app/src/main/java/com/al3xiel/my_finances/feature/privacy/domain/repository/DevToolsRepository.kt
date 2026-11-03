package com.al3xiel.my_finances.feature.privacy.domain.repository

import kotlinx.coroutines.flow.Flow

/** Si las herramientas de desarrollador (S73) están activadas. No es un dato sensible: vive en DataStore. */
interface DevToolsRepository {
    val enabled: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
