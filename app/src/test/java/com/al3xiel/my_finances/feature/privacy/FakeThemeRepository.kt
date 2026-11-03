package com.al3xiel.my_finances.feature.privacy

import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.feature.privacy.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeThemeRepository(initial: ThemeMode = ThemeMode.SYSTEM) : ThemeRepository {
    val state = MutableStateFlow(initial)
    val dynamic = MutableStateFlow(false)
    override val themeMode: Flow<ThemeMode> = state
    override val dynamicColor: Flow<Boolean> = dynamic

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dynamic.value = enabled
    }
}
