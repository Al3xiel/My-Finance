package com.al3xiel.my_finances.feature.tags.domain.repository

import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    suspend fun find(id: TagId): Tag?

    suspend fun activeNames(): List<String>

    /** Todas, incluidas las archivadas, ordenadas por nombre. */
    fun observeAll(): Flow<List<Tag>>
}
