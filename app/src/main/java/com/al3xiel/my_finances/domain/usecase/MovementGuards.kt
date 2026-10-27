package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot

/**
 * Ganchos para que una feature vete una operación de otra sin que se conozcan: cada feature aporta los suyos a un
 * conjunto Hilt y el caso de uso los consulta antes de emitir el evento. `null` = la operación puede seguir.
 */
fun interface MovementVoidGuard {
    suspend fun check(movementId: MovementId): BusinessError?
}

fun interface MovementCorrectionGuard {
    suspend fun check(movementId: MovementId, snapshot: MovementSnapshot): BusinessError?
}

/** Valida el cúmulo de una parte de gasto; `unchanged` = la parte ya lo tenía antes de corregir (aunque hoy esté archivado). */
fun interface PoolReferenceGuard {
    suspend fun check(poolId: PoolId, unchanged: Boolean): BusinessError?
}

fun interface ArchiveGuard {
    suspend fun check(personId: PersonId): BusinessError?
}
