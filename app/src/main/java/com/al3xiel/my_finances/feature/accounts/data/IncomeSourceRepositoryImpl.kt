package com.al3xiel.my_finances.feature.accounts.data

import com.al3xiel.my_finances.data.projection.catalog.CatalogDao
import com.al3xiel.my_finances.data.projection.catalog.CatalogQueryDao
import com.al3xiel.my_finances.data.projection.catalog.IncomeSourceEntity
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class IncomeSourceRepositoryImpl @Inject constructor(
    private val catalog: CatalogDao,
    private val catalogQueries: CatalogQueryDao,
) : IncomeSourceRepository {
    override suspend fun find(id: IncomeSourceId): IncomeSource? = catalog.incomeSource(id.value)?.toDomain()
    override fun observeAll(): Flow<List<IncomeSource>> =
        catalogQueries.observeIncomeSources().map { rows -> rows.map { it.toDomain() } }

    override suspend fun activeNames(): List<String> = catalogQueries.incomeSourceNames()
}

private fun IncomeSourceEntity.toDomain() = IncomeSource(IncomeSourceId(id), name, kind, personId?.let(::PersonId))
