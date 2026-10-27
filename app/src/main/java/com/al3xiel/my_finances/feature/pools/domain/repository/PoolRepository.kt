package com.al3xiel.my_finances.feature.pools.domain.repository

import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.feature.pools.domain.model.PendingPartLine
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.model.ReimbursedPart
import com.al3xiel.my_finances.feature.pools.domain.model.StatementPart
import com.al3xiel.my_finances.feature.pools.domain.rules.PendingPart
import kotlinx.coroutines.flow.Flow

interface PoolRepository {
    suspend fun find(id: PoolId): Pool?

    suspend fun activeNames(): List<String>

    /** Todos los cúmulos, incluidos los archivados, por nombre: el selector de un gasto necesita el que ya usa. */
    fun observePools(): Flow<List<Pool>>

    /** Lo que falta reembolsar en todo el cúmulo (todos sus periodos), sin movimientos anulados. */
    suspend fun pendingMinor(id: PoolId): Long

    /** Las partes del movimiento que ya recibieron algún reembolso. */
    suspend fun reimbursedParts(movementId: MovementId): List<ReimbursedPart>

    /**
     * Partes con algo pendiente de los cúmulos de la persona (o solo de `poolId`), de cualquier periodo y sin
     * movimientos anulados (D18).
     */
    suspend fun pendingParts(personId: PersonId, poolId: PoolId?): List<PendingPart>

    /** Lo mismo que [pendingParts] con los nombres para mostrarlas, en el mismo orden (S34). */
    suspend fun pendingPartLines(personId: PersonId, poolId: PoolId?): List<PendingPartLine>

    /** El periodo abierto del cúmulo, o `null` si no tiene. */
    suspend fun currentPeriod(poolId: PoolId): PoolPeriod?

    /** Todas las partes del periodo (sin movimientos anulados), con lo que falta reembolsar de cada una. */
    suspend fun periodParts(periodId: PoolPeriodId): List<StatementPart>

    /** Lo pendiente en los periodos anteriores del mismo cúmulo. */
    suspend fun pendingBefore(periodId: PoolPeriodId): Long
}
