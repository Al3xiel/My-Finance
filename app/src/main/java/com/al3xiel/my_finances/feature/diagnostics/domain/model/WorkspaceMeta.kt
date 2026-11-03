package com.al3xiel.my_finances.feature.diagnostics.domain.model

import kotlinx.serialization.Serializable

/** Lo que se guarda del paquete abierto: su manifiesto, lo que decían sus proyecciones y el hash de su ZIP en claro. */
@Serializable
data class WorkspaceMeta(
    val manifest: PackageManifest,
    val projections: PackageProjections,
    val packageHash: String,
)
