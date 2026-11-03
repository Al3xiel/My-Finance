package com.al3xiel.my_finances.data.projection

import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.catalog.CatalogProjector
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeProjector
import com.al3xiel.my_finances.data.projection.fund.FundProjector
import com.al3xiel.my_finances.data.projection.loan.LoanProjector
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.origin.OriginProjector
import com.al3xiel.my_finances.data.projection.pool.PoolPeriodRefresher
import com.al3xiel.my_finances.data.projection.pool.PoolProjector
import com.al3xiel.my_finances.data.projection.pool.ReimbursementProjector
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationProjector

/**
 * Arma el conjunto completo de proyectores sobre los DAO de **una base dada**: la real y el espacio de diagnóstico
 * (D50) comparten esta lista. Un proyector nuevo se agrega aquí y `ProjectorSetFactoryCoverageTest` falla si falta.
 * Cada llamada crea instancias nuevas: `OriginProjector` guarda estado de replay y no se comparte entre usos.
 */
class ProjectorSetFactory(private val database: AppDatabase) {
    fun create(): Set<EventProjector> {
        val poster = LedgerPoster(database.movementDao(), database.ledgerDao())
        val periods = PoolPeriodRefresher(database.poolDao())
        return setOf(
            CatalogProjector(database.catalogDao()),
            MovementProjector(database.movementDao(), database.ledgerDao()),
            PoolProjector(database.poolDao(), periods),
            ReimbursementProjector(database.reimbursementDao(), poster, periods),
            LoanProjector(database.loanDao(), poster),
            FundProjector(database.fundDao()),
            ExpectedIncomeProjector(database.expectedIncomeDao()),
            ReconciliationProjector(database.reconciliationDao(), poster),
            OriginProjector(database.originDao()),
        )
    }
}
