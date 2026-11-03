package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.model.SnapshotCut

/**
 * Lo que las proyecciones de una base dicen hoy: saldos, asientos de la cuenta con su saldo acumulado, el estado de la
 * conciliación y las versiones de los proyectores. Sirve para la base real (al exportar) y para el espacio (al comparar).
 */
interface ProjectionSnapshotReader {
    /** `accountId` nulo = todas las cuentas; `cut` recorta saldos y asientos a lo que daría reproducir los eventos elegidos (nulo = hoy); `includeOrigin` agrega `origin_balances` (dato secundario, solo `FULL`). */
    suspend fun read(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean, cut: SnapshotCut? = null): PackageProjections
}
