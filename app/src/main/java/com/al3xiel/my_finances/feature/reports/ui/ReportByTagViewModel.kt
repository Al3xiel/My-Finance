package com.al3xiel.my_finances.feature.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.ReportCalculator
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class TagRowUi(val id: String, val name: String, val amount: Long, val byCategory: List<Pair<String, Long>>)

data class ReportByTagUiState(
    val period: ReportPeriod,
    val rows: List<TagRowUi> = emptyList(),
    /** El concepto cuyo cruce con categorías está abierto. */
    val expandedId: String? = null,
    val loaded: Boolean = false,
)

sealed interface ReportByTagUiAction {
    data class ToggleCrossing(val tagId: String) : ReportByTagUiAction
}

/** S42: monto propio por concepto, cada uno por separado (un gasto con varios conceptos aparece en todos; no suman). */
@HiltViewModel(assistedFactory = ReportByTagViewModel.Factory::class)
class ReportByTagViewModel @AssistedInject constructor(
    @Assisted("from") from: String,
    @Assisted("to") to: String,
    reports: ReportRepository,
    tags: TagRepository,
    categories: CategoryRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("from") from: String, @Assisted("to") to: String): ReportByTagViewModel
    }

    private val period = ReportPeriod.fromTexts(from, to)
    private val expanded = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReportByTagUiState> = combine(
        flow { emit(ReportCalculator.byTag(reports.parts(period))) },
        tags.observeAll(),
        categories.observeAll(),
        expanded,
    ) { totals, tagList, categoryList, open ->
        val tagNames = tagList.associate { it.id.value to it.name }
        val categoryNames = categoryList.associate { it.id.value to it.name }
        ReportByTagUiState(
            period,
            totals.map { t ->
                TagRowUi(t.tagId, tagNames[t.tagId] ?: t.tagId, t.amount, t.byCategory.map { (c, a) -> (categoryNames[c] ?: c) to a })
            },
            open,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportByTagUiState(period))

    fun onAction(action: ReportByTagUiAction) {
        when (action) {
            is ReportByTagUiAction.ToggleCrossing -> expanded.update { if (it == action.tagId) null else action.tagId }
        }
    }
}
