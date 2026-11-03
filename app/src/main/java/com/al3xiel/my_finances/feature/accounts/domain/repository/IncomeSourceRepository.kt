package com.al3xiel.my_finances.feature.accounts.domain.repository

import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import kotlinx.coroutines.flow.Flow

interface IncomeSourceRepository {
    suspend fun find(id: IncomeSourceId): IncomeSource?

    suspend fun activeNames(): List<String>

    /** Todas, incluidas las archivadas, ordenadas por nombre. */
    fun observeAll(): Flow<List<IncomeSource>>
}
