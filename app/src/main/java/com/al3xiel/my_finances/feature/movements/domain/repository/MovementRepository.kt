package com.al3xiel.my_finances.feature.movements.domain.repository

import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.model.MovementFilter
import com.al3xiel.my_finances.feature.movements.domain.model.MovementSummary
import kotlinx.coroutines.flow.Flow

interface MovementRepository {
    suspend fun find(id: MovementId): Movement?

    /** El gasto activo registrado más recientemente; sirve para proponer cuenta y categoría al registrar otro. */
    suspend fun lastExpense(): Movement?

    /** Del más reciente al más antiguo (fecha del hecho, luego orden de registro). */
    fun observeMovements(filter: MovementFilter): Flow<List<MovementSummary>>

    /** Movimiento con sus partes y su historial de eventos (registro, correcciones, anulación). */
    fun observeDetail(id: MovementId): Flow<MovementDetail?>
}
