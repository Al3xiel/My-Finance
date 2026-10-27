package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import java.time.LocalDate

/** Parte en edición. `key` identifica la fila en pantalla; `partId` solo existe si la parte ya estaba guardada (D15). */
data class PartDraft(
    val key: Int,
    val amountText: String = "",
    val categoryId: CategoryId? = null,
    val tagIds: Set<TagId> = emptySet(),
    val partId: PartId? = null,
    val poolId: PoolId? = null,
)

data class ExpenseEditorUiState(
    val isCorrection: Boolean = false,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val totalText: String = "",
    val accountId: AccountId? = null,
    val occurredOn: LocalDate = LocalDate.ofEpochDay(0),
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val note: String = "",
    val parts: List<PartDraft> = emptyList(),
    val accountOptions: List<EntityOption> = emptyList(),
    val categoryOptions: List<EntityOption> = emptyList(),
    val tagOptions: List<EntityOption> = emptyList(),
    /** Cúmulos activos más los archivados que alguna parte ya usa (para poder verlos al corregir). */
    val poolOptions: List<EntityOption> = emptyList(),
    /** Total menos lo repartido entre las partes (solo con 2 o más partes): > 0 falta, < 0 sobra. */
    val unassignedMinor: Long = 0,
    val error: BusinessError? = null,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val showReasonDialog: Boolean = false,
    val saved: Boolean = false,
)
