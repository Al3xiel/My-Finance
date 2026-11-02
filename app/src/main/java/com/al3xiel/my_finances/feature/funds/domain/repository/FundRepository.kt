package com.al3xiel.my_finances.feature.funds.domain.repository

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.funds.domain.model.FundCountRecord
import com.al3xiel.my_finances.feature.funds.domain.model.FundEditFacts
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FundRepository {
    suspend fun find(id: FundId): FundOverview?

    /** Activos primero y por nombre; los cerrados al final. */
    fun observeFunds(): Flow<List<FundOverview>>

    fun observeFund(id: FundId): Flow<FundOverview?>

    /** Apartado (suma de lo que aparta cada fondo activo) y Disponible libre (saldo de cuentas − apartado). */
    fun observeSummary(): Flow<FundSummary>

    /** Disponible libre en céntimos, con el que se valida una recarga con dinero propio. */
    suspend fun availableBalanceMinor(): Long

    suspend fun activeNames(): List<String>

    /** Lo ya recargado a fondos desde ese ingreso (D24). */
    suspend fun toppedUpFrom(incomeMovementId: MovementId): Money

    suspend fun editFacts(id: FundId): FundEditFacts

    suspend fun usageDays(id: FundId): Set<LocalDate>

    fun observeUsageDays(id: FundId): Flow<List<LocalDate>>

    fun observeCounts(id: FundId): Flow<List<FundCountRecord>>
}
