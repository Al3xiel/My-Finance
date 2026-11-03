package com.al3xiel.my_finances.feature.privacy.domain.repository

import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** Usar los colores del teléfono (Android 12+) en lugar de la paleta turquesa; apagado por defecto (D67). */
    val dynamicColor: Flow<Boolean>

    suspend fun setDynamicColor(enabled: Boolean)
}
