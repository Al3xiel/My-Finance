package com.al3xiel.my_finances.feature.people.data

import com.al3xiel.my_finances.data.projection.catalog.CatalogDao
import com.al3xiel.my_finances.data.projection.catalog.CatalogQueryDao
import com.al3xiel.my_finances.data.projection.catalog.PersonEntity
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PersonRepositoryImpl @Inject constructor(
    private val catalog: CatalogDao,
    private val catalogQueries: CatalogQueryDao,
) : PersonRepository {
    override suspend fun find(id: PersonId): Person? = catalog.person(id.value)?.toDomain()
    override fun observeAll(): Flow<List<Person>> =
        catalogQueries.observePeople().map { rows -> rows.map { it.toDomain() } }

    override suspend fun activeAliases(): List<String> = catalogQueries.activePersonAliases()
}

private fun PersonEntity.toDomain() = Person(PersonId(id), alias, isArchived)
