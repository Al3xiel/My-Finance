package com.al3xiel.my_finances.feature.movements.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.movements.domain.model.MovementFilter
import com.al3xiel.my_finances.feature.movements.domain.model.MovementSummary
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class MovementRowUi(
    val id: MovementId,
    val type: MovementType,
    val accountName: String,
    val counterAccountName: String?,
    val note: String?,
    val amount: Money,
    val isVoided: Boolean,
)

data class DaySection(val day: LocalDate, val rows: List<MovementRowUi>)

data class FilterOptions(
    val accounts: List<EntityOption> = emptyList(),
    val categories: List<EntityOption> = emptyList(),
    val tags: List<EntityOption> = emptyList(),
)

data class MovementsUiState(
    val days: List<DaySection> = emptyList(),
    val filter: MovementFilter = MovementFilter(),
    /** Lo que el usuario tecleó en la búsqueda, sin recortar (el filtro lleva la versión recortada). */
    val searchText: String = "",
    val options: FilterOptions = FilterOptions(),
    val isFiltered: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface MovementsUiAction {
    data class SetAccount(val id: AccountId?) : MovementsUiAction
    data class SetType(val type: MovementType?) : MovementsUiAction
    data class SetCategory(val id: CategoryId?) : MovementsUiAction
    data class SetTag(val id: TagId?) : MovementsUiAction
    data class SetFrom(val day: LocalDate?) : MovementsUiAction
    data class SetTo(val day: LocalDate?) : MovementsUiAction
    data class SetText(val text: String) : MovementsUiAction
    data object ClearFilters : MovementsUiAction
}

/**
 * Filtro con que se abre la lista desde otra pantalla (S41–S43 → `MovementsFiltered`, D37). Lleva textos porque una ruta
 * no puede llevar value classes; el vacío es la pestaña Movimientos sin filtro.
 */
data class MovementsPreset(
    val from: String? = null,
    val to: String? = null,
    val categoryId: String? = null,
    val tagId: String? = null,
    val accountId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MovementsViewModel.Factory::class)
class MovementsViewModel @AssistedInject constructor(
    @Assisted preset: MovementsPreset,
    movements: MovementRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    tags: TagRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(preset: MovementsPreset): MovementsViewModel
    }

    private data class Query(val searchText: String = "", val filter: MovementFilter = MovementFilter())

    private val query = MutableStateFlow(
        Query(
            filter = MovementFilter(
                accountId = preset.accountId?.let(::AccountId),
                categoryId = preset.categoryId?.let(::CategoryId),
                tagId = preset.tagId?.let(::TagId),
                from = preset.from?.let(LocalDate::parse),
                to = preset.to?.let(LocalDate::parse),
            ),
        ),
    )

    init {
        // Una categoría padre filtra por ella y sus subcategorías (D37); mientras llega el árbol se muestra la exacta.
        val parent = query.value.filter.categoryId
        if (parent != null) {
            viewModelScope.launch {
                val all = categories.observeAll().first()
                val family = all.filter { it.parentId == parent }.map { it.id }.toSet() + parent
                query.update { q -> if (q.filter.categoryId == parent) q.copy(filter = q.filter.copy(categoryIds = family)) else q }
            }
        }
    }

    val uiState: StateFlow<MovementsUiState> = combine(
        query,
        query.flatMapLatest { movements.observeMovements(it.filter) },
        accounts.observeAccounts(),
        categories.observeAll(),
        tags.observeAll(),
    ) { query, summaries, accountList, categoryList, tagList ->
        val names = accountList.associate { it.account.id to it.account.name }
        MovementsUiState(
            days = summaries.groupBy { it.occurredOn }.map { (day, rows) -> DaySection(day, rows.map { it.toRow(names) }) },
            filter = query.filter,
            searchText = query.searchText,
            options = FilterOptions(
                accounts = accountList.filter { !it.account.isArchived }.map { EntityOption(it.account.id.value, it.account.name) },
                categories = categoryList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.name) },
                tags = tagList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.name) },
            ),
            isFiltered = query.filter != MovementFilter(),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MovementsUiState())

    fun onAction(action: MovementsUiAction) {
        when (action) {
            is MovementsUiAction.SetAccount -> update { copy(accountId = action.id) }
            is MovementsUiAction.SetType -> update { copy(type = action.type) }
            is MovementsUiAction.SetCategory -> update { copy(categoryId = action.id, categoryIds = emptySet()) }
            is MovementsUiAction.SetTag -> update { copy(tagId = action.id) }
            is MovementsUiAction.SetFrom -> update { copy(from = action.day) }
            is MovementsUiAction.SetTo -> update { copy(to = action.day) }
            is MovementsUiAction.SetText -> query.update {
                Query(searchText = action.text, filter = it.filter.copy(text = action.text.trim().ifEmpty { null }))
            }
            MovementsUiAction.ClearFilters -> query.value = Query()
        }
    }

    private fun update(change: MovementFilter.() -> MovementFilter) = query.update { it.copy(filter = it.filter.change()) }

    private fun MovementSummary.toRow(names: Map<AccountId, String>) = MovementRowUi(
        id = id,
        type = type,
        accountName = names[accountId].orEmpty(),
        counterAccountName = counterAccountId?.let { names[it].orEmpty() },
        note = note,
        amount = amount,
        isVoided = status == MovementStatus.VOIDED,
    )
}
