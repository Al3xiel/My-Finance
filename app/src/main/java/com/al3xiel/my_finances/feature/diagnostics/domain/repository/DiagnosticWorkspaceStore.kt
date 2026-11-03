package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.feature.diagnostics.domain.model.WorkspaceMeta

/** El paquete abierto en el espacio de diagnóstico y el diccionario aplicado; ambos sellados y borrados juntos (D51). Funciones bloqueantes. */
interface DiagnosticWorkspaceStore {
    fun hasPackage(): Boolean

    fun save(meta: WorkspaceMeta)

    /** `null` si no hay paquete o su metadato no se puede abrir. */
    fun meta(): WorkspaceMeta?

    fun saveDictionary(plain: ByteArray)

    fun dictionary(): ByteArray?

    /** Borra todo lo del espacio; repetirlo no hace daño. */
    fun delete()
}
