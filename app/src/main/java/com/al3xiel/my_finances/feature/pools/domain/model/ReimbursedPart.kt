package com.al3xiel.my_finances.feature.pools.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId

/** Parte de un gasto que ya recibió algo de reembolso: no se puede quitar ni cambiar de cúmulo. */
data class ReimbursedPart(
    val partId: PartId,
    val poolId: PoolId,
    val reimbursed: Money,
)
