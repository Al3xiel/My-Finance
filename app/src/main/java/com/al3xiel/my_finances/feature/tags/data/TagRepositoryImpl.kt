package com.al3xiel.my_finances.feature.tags.data

import com.al3xiel.my_finances.data.projection.catalog.CatalogDao
import com.al3xiel.my_finances.data.projection.catalog.CatalogQueryDao
import com.al3xiel.my_finances.data.projection.catalog.TagEntity
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val catalog: CatalogDao,
    private val catalogQueries: CatalogQueryDao,
) : TagRepository {
    override suspend fun find(id: TagId): Tag? = catalog.tag(id.value)?.toDomain()
    override fun observeAll(): Flow<List<Tag>> = catalogQueries.observeTags().map { rows -> rows.map { it.toDomain() } }

    override suspend fun activeNames(): List<String> = catalogQueries.activeTagNames()
}

private fun TagEntity.toDomain() = Tag(TagId(id), name, kind, personId?.let(::PersonId), isArchived)
