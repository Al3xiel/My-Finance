package com.al3xiel.my_finances.feature.quickentry.ui

import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.feature.quickentry.domain.model.Ambiguity
import com.al3xiel.my_finances.feature.quickentry.domain.model.Conflict

/** S25. Todo se pierde al cerrar la pantalla: el texto no se guarda ni se restaura (D65). */
data class QuickEntryUiState(
    val line: String = "",
    val isIncome: Boolean = false,
    val amountText: String = "",
    val accountId: String? = null,
    val accountSuggested: Boolean = false,
    val categoryId: String? = null,
    val categorySuggested: Boolean = false,
    val tagNames: List<String> = emptyList(),
    val poolId: String? = null,
    val fundId: String? = null,
    val sourceId: String? = null,
    val note: String = "",
    val ambiguities: List<Ambiguity> = emptyList(),
    val conflicts: Set<Conflict> = emptySet(),
    val accountOptions: List<EntityOption> = emptyList(),
    val categoryOptions: List<EntityOption> = emptyList(),
    val poolOptions: List<EntityOption> = emptyList(),
    val fundOptions: List<EntityOption> = emptyList(),
    val sourceOptions: List<EntityOption> = emptyList(),
    val canSave: Boolean = false,
    val error: BusinessError? = null,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val saved: Boolean = false,
) {
    /** Hay algo escrito que interpretar; sin eso la vista previa no se muestra. */
    val hasLine: Boolean get() = line.isNotBlank()
}
