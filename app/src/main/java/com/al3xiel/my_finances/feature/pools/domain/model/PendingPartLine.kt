package com.al3xiel.my_finances.feature.pools.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.feature.pools.domain.rules.PendingPart
import java.time.LocalDate

/** Una parte con algo pendiente, con los nombres que muestra el registro de un reembolso (S34). */
data class PendingPartLine(
    val partId: PartId,
    val poolId: PoolId,
    val poolName: String,
    val occurredOn: LocalDate,
    val createdSequence: Long,
    val categoryName: String,
    val tagNames: List<String>,
    val pending: Money,
) {
    fun toPendingPart() = PendingPart(partId, occurredOn, createdSequence, pending)
}
