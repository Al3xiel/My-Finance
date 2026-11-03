package com.al3xiel.my_finances.feature.privacy.domain.repository

import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
