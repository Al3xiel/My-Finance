package com.al3xiel.my_finances.core.navigation

import androidx.compose.runtime.Composable
import javax.inject.Inject

/** Una sección que una feature suma a Ajustes (S72): se muestra bajo las generales, ordenada por [order] y luego por [id]. */
data class SettingsSection(
    val id: String,
    val order: Int,
    val content: @Composable () -> Unit,
)

/** Cada feature aporta las suyas a un conjunto Hilt; Ajustes no conoce a ninguna. */
fun interface SettingsSectionContributor {
    fun sections(): List<SettingsSection>
}

class SettingsSectionRegistry @Inject constructor(
    contributors: Set<@JvmSuppressWildcards SettingsSectionContributor>,
) {
    val sections: List<SettingsSection> = contributors
        .flatMap { it.sections() }
        .sortedWith(compareBy({ it.order }, { it.id }))
}
