package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspaceReplayer
import javax.inject.Inject

class RoomWorkspaceReplayer @Inject constructor(
    private val workspace: DiagnosticWorkspace,
    private val projector: WorkspaceProjector,
) : WorkspaceReplayer {
    override suspend fun replay(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean): PackageProjections {
        val database = workspace.database()
        projector.replay(database)
        return RoomProjectionSnapshotReader(database).read(accountId, reconciliationId, includeOrigin)
    }
}
