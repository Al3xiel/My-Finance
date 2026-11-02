package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import java.time.LocalDate

/**
 * Ganchos para que una feature vete una operación de otra sin que se conozcan: cada feature aporta los suyos a un
 * conjunto Hilt y el caso de uso los consulta antes de emitir el evento. `null` = la operación puede seguir.
 */
fun interface MovementVoidGuard {
    suspend fun check(movementId: MovementId): BusinessError?
}

/**
 * Lo que otra feature hace cuando se anula un movimiento suyo: `Failed` veta la anulación y `Ok(eventos)` suma esos
 * eventos al mismo lote (y la misma correlación) que `MovementVoided`, fechados hoy. Los consulta después de los
 * guards, con el movimiento ya validado.
 */
fun interface MovementVoidFollowUp {
    suspend fun followUp(movementId: MovementId): Outcome<List<DomainEvent>>
}

fun interface MovementCorrectionGuard {
    suspend fun check(movementId: MovementId, snapshot: MovementSnapshot): BusinessError?
}

/** Valida el cúmulo de una parte de gasto; `unchanged` = la parte ya lo tenía antes de corregir (aunque hoy esté archivado). */
fun interface PoolReferenceGuard {
    suspend fun check(poolId: PoolId, unchanged: Boolean): BusinessError?
}

/**
 * Valida el fondo de una parte de gasto en su fecha; `unchanged` = la parte ya tenía ese fondo, ese día y no sube de monto
 * (un fondo que se cerró o se arqueó después no impide corregir lo que ya estaba).
 */
fun interface FundReferenceGuard {
    suspend fun check(fundId: FundId, occurredOn: LocalDate, unchanged: Boolean): BusinessError?
}

fun interface ArchiveGuard {
    suspend fun check(personId: PersonId): BusinessError?
}

/** Veta archivar una cuenta (por ejemplo, con una conciliación abierta). */
fun interface AccountArchiveGuard {
    suspend fun check(accountId: AccountId): BusinessError?
}

/**
 * Valida el `reconciliationId` de un movimiento registrado, corregido o anulado desde la conciliación: que exista, esté
 * abierta y sea de una de las cuentas que el movimiento toca.
 */
fun interface ReconciliationLinkGuard {
    suspend fun check(reconciliationId: ReconciliationId, accountIds: Set<AccountId>): BusinessError?
}
