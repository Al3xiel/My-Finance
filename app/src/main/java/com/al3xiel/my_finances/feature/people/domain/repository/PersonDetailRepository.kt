package com.al3xiel.my_finances.feature.people.domain.repository

import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.people.domain.model.PersonDetail
import kotlinx.coroutines.flow.Flow

interface PersonDetailRepository {
    /** Detalle de la persona (S35); `null` si no existe. */
    fun observePersonDetail(personId: PersonId): Flow<PersonDetail?>

    /** Lo que la persona debe: pendiente de sus cúmulos activos más sus deudas abiertas (sin anulados). */
    suspend fun balanceMinor(personId: PersonId): Long
}
