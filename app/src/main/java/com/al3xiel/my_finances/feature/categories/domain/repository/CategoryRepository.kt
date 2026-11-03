package com.al3xiel.my_finances.feature.categories.domain.repository

import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    suspend fun find(id: CategoryId): Category?

    suspend fun activeNames(): List<String>

    /** Todas, incluidas las archivadas, ordenadas por nombre. */
    fun observeAll(): Flow<List<Category>>
}
