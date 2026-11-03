package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections

/**
 * Lo que las proyecciones de una base dicen hoy: saldos, asientos de la cuenta con su saldo acumulado, el estado de la
 * conciliación y las versiones de los proyectores. Sirve para la base real (al exportar) y para el espacio (al comparar).
 */
interface ProjectionSnapshotReader {
    /** `accountId` nulo = todas las cuentas; `includeOrigin` agrega `origin_balances` (dato secundario, solo `FULL`). */
    suspend fun read(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean): PackageProjections
}
