package com.al3xiel.my_finances.feature.pools.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import java.time.LocalDate

/** Un cúmulo con lo que falta reembolsar: del periodo abierto y en total (todos sus periodos). */
data class PoolReceivable(
    val poolId: PoolId,
    val name: String,
    val kind: PoolKind,
    val responsiblePersonId: PersonId,
    val responsibleAlias: String,
    val periodPending: Money,
    val totalPending: Money,
)

data class LoanReceivable(
    val loanId: LoanId,
    val personId: PersonId,
    val alias: String,
    val principal: Money,
    val repaid: Money,
    val occurredOn: LocalDate,
) {
    val pending: Money get() = principal - repaid
}

/** Lo que una persona debe: pendiente de sus cúmulos activos más sus deudas abiertas (sin movimientos anulados). */
data class PersonReceivable(
    val personId: PersonId,
    val alias: String,
    val poolsPending: Money,
    val loansPending: Money,
) {
    val total: Money get() = poolsPending + loansPending
}

data class Receivables(
    val total: Money,
    val people: List<PersonReceivable>,
    val pools: List<PoolReceivable>,
    val loans: List<LoanReceivable>,
)

data class PoolDetail(
    val pool: Pool,
    val responsibleAlias: String,
    /** Del más reciente al más antiguo. */
    val periods: List<PoolPeriod>,
    val pendingTotal: Money,
) {
    val currentPeriod: PoolPeriod? get() = periods.firstOrNull { it.closedOn == null }
}
