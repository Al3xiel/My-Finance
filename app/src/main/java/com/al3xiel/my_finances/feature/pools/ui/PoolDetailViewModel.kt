package com.al3xiel.my_finances.feature.pools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.pools.domain.model.PoolDetail
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import com.al3xiel.my_finances.feature.pools.domain.usecase.ArchivePoolUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoolDetailUiState(
    val loaded: Boolean = false,
    val detail: PoolDetail? = null,
    val notice: BusinessError? = null,
) {
    val notFound: Boolean get() = loaded && detail == null
}

sealed interface PoolDetailUiAction {
    data object Archive : PoolDetailUiAction
    data object DismissNotice : PoolDetailUiAction
}

/** S31: periodo actual, historial y acciones del cúmulo. Agregar gasto, rendir y reembolsar los resuelve la navegación. */
@HiltViewModel(assistedFactory = PoolDetailViewModel.Factory::class)
class PoolDetailViewModel @AssistedInject constructor(
    @Assisted poolIdValue: String,
    receivables: ReceivablesRepository,
    private val archivePool: ArchivePoolUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(poolIdValue: String): PoolDetailViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val poolId = PoolId(poolIdValue)
    private val notice = MutableStateFlow<BusinessError?>(null)

    val uiState: StateFlow<PoolDetailUiState> = combine(receivables.observePoolDetail(poolId), notice) { detail, notice ->
        PoolDetailUiState(loaded = true, detail = detail, notice = notice)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PoolDetailUiState())

    fun onAction(action: PoolDetailUiAction) {
        when (action) {
            PoolDetailUiAction.Archive -> viewModelScope.launch { notice.value = archivePool(poolId).errorOrNull() }
            PoolDetailUiAction.DismissNotice -> notice.value = null
        }
    }
}
