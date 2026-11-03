package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections

/**
 * Reconstruye las proyecciones del espacio de diagnóstico con el código actual (sin `append`, sin eventos nuevos) y devuelve lo
 * que dicen. Solo toca el espacio, nunca la base real.
 */
interface WorkspaceReplayer {
    suspend fun replay(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean): PackageProjections
}
