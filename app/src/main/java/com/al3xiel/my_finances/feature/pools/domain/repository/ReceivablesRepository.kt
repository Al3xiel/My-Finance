package com.al3xiel.my_finances.feature.pools.domain.repository

import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.feature.pools.domain.model.PoolDetail
import com.al3xiel.my_finances.feature.pools.domain.model.Receivables
import kotlinx.coroutines.flow.Flow

interface ReceivablesRepository {
    /** Por cobrar (S30): total, saldo por persona, cúmulos con pendiente y deudas abiertas. */
    fun observeReceivables(): Flow<Receivables>

    /** Detalle del cúmulo (S31) con sus periodos; `null` si no existe. */
    fun observePoolDetail(poolId: PoolId): Flow<PoolDetail?>
}
