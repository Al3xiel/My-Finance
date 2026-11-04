package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.feature.movements.domain.rules.PoolDefaults
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import java.time.LocalDate

/** Parte en edición. `key` identifica la fila en pantalla; `partId` solo existe si la parte ya estaba guardada (D15). */
data class PartDraft(
    val key: Int,
    val amountText: String = "",
    val categoryId: CategoryId? = null,
    val tagIds: Set<TagId> = emptySet(),
    val partId: PartId? = null,
    val poolId: PoolId? = null,
    val fundId: FundId? = null,
)

/** Sin cúmulo conserva lo elegido; con cúmulo toma su categoría y suma sus conceptos por defecto (el usuario puede cambiarlos). */
internal fun PartDraft.inPool(pool: Pool?): PartDraft =
    if (pool == null) {
        copy(poolId = null)
    } else {
        copy(
            poolId = pool.id,
            fundId = null,
            categoryId = PoolDefaults.categoryFor(pool, categoryId),
            tagIds = PoolDefaults.tagsFor(pool, tagIds),
        )
    }

data class ExpenseEditorUiState(
    val isCorrection: Boolean = false,
    /** Viene de S61: el gasto queda vinculado a esa conciliación. */
    val linkedToReconciliation: Boolean = false,
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
    /** Fondos activos más los cerrados que alguna parte ya usa. */
    val fundOptions: List<EntityOption> = emptyList(),
    /** Nombres de los fondos a los que este gasto les pasa lo que les queda (avisa, no bloquea). */
    val overspentFunds: List<String> = emptyList(),
    /** Total menos lo repartido entre las partes (solo con 2 o más partes): > 0 falta, < 0 sobra. */
    val unassignedMinor: Long = 0,
    val error: BusinessError? = null,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val showReasonDialog: Boolean = false,
    val saved: Boolean = false,
    /** Hay un guardado en curso: el botón lo dice y no deja tocar otra vez. */
    val saving: Boolean = false,
)

/** Las categorías activas con su padre delante (`Comida › Almuerzo`), como las elige el usuario. */
internal fun categoryOptionsOf(all: List<Category>): List<EntityOption> {
    val active = all.filter { !it.isArchived }
    val names = active.associate { it.id to it.name }
    return active.map { category ->
        val parent = category.parentId?.let(names::get)
        EntityOption(category.id.value, if (parent != null) "$parent › ${category.name}" else category.name)
    }
}
