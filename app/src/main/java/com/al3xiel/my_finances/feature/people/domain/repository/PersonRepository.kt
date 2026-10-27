package com.al3xiel.my_finances.feature.people.domain.repository

import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.people.domain.model.Person
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    suspend fun find(id: PersonId): Person?

    suspend fun activeAliases(): List<String>

    /** Todas, incluidas las archivadas, ordenadas por nombre. */
    fun observeAll(): Flow<List<Person>>
}
