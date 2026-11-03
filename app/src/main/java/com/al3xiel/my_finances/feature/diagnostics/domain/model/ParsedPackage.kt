package com.al3xiel.my_finances.feature.diagnostics.domain.model

/** Un paquete ya validado. `packageHash` es el SHA-256 del ZIP en claro, igual que el del evento de exportación. */
class ParsedPackage(
    val manifest: PackageManifest,
    val projections: PackageProjections,
    val events: List<PackageEventLine>,
    val packageHash: String,
)
