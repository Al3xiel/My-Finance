package com.al3xiel.my_finances.feature.categories.data

import com.al3xiel.my_finances.data.projection.catalog.CatalogDao
import com.al3xiel.my_finances.data.projection.catalog.CatalogQueryDao
import com.al3xiel.my_finances.data.projection.catalog.CategoryEntity
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val catalog: CatalogDao,
    private val catalogQueries: CatalogQueryDao,
) : CategoryRepository {
    override suspend fun find(id: CategoryId): Category? = catalog.category(id.value)?.toDomain()
    override fun observeAll(): Flow<List<Category>> =
        catalogQueries.observeCategories().map { rows -> rows.map { it.toDomain() } }

    override suspend fun activeNames(): List<String> = catalogQueries.activeCategoryNames()
}

private fun CategoryEntity.toDomain() = Category(CategoryId(id), name, parentId?.let(::CategoryId), isArchived)
