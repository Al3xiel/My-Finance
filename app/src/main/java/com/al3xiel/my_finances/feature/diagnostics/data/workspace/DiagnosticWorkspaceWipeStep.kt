package com.al3xiel.my_finances.feature.diagnostics.data.workspace

import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import javax.inject.Inject

/** "Borrar todos mis datos" también elimina el espacio de diagnóstico, que guarda datos de un tercero (D51). */
class DiagnosticWorkspaceWipeStep @Inject constructor(private val workspace: DiagnosticWorkspace) : WipeStep {
    override val id = "diagnostic_workspace"
    override val priority = WipeStep.KEY_PRIORITY

    override suspend fun wipe() = workspace.delete()
}
