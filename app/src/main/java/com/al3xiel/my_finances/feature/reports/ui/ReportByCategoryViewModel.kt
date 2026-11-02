package com.al3xiel.my_finances.feature.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.reports.domain.model.CategoryShare
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.ReportCalculator
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

data class CategoryRowUi(
    val id: String,
    val name: String,
    val amount: Long,
    val basisPoints: Int,
    val children: List<CategoryRowUi> = emptyList(),
)

data class ReportByCategoryUiState(
    val period: ReportPeriod,
    val rows: List<CategoryRowUi> = emptyList(),
    val total: Long = 0,
    /** La categoría cuyo detalle de subcategorías está abierto. */
    val expandedId: String? = null,
    val loaded: Boolean = false,
)

sealed interface ReportByCategoryUiAction {
    data class ToggleDetail(val categoryId: String) : ReportByCategoryUiAction
}

/** S41: el gasto propio del periodo por categoría; las subcategorías suman a su padre y la torta suma 100%. */
@HiltViewModel(assistedFactory = ReportByCategoryViewModel.Factory::class)
class ReportByCategoryViewModel @AssistedInject constructor(
    @Assisted("from") from: String,
    @Assisted("to") to: String,
    reports: ReportRepository,
    categories: CategoryRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("from") from: String, @Assisted("to") to: String): ReportByCategoryViewModel
    }

    private val period = ReportPeriod.fromTexts(from, to)
    private val expanded = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReportByCategoryUiState> = combine(
        flow { emit(ReportCalculator.byCategory(reports.parts(period))) },
        categories.observeAll(),
        expanded,
    ) { shares, categoryList, open ->
        val names = categoryList.associate { it.id.value to it.name }
        fun CategoryShare.toRow(): CategoryRowUi =
            CategoryRowUi(categoryId, names[categoryId] ?: categoryId, amount, basisPoints, children.map { it.toRow() })
        ReportByCategoryUiState(period, shares.map { it.toRow() }, shares.sumOf { it.amount }, open, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportByCategoryUiState(period))

    fun onAction(action: ReportByCategoryUiAction) {
        when (action) {
            is ReportByCategoryUiAction.ToggleDetail -> expanded.update { if (it == action.categoryId) null else action.categoryId }
        }
    }
}
