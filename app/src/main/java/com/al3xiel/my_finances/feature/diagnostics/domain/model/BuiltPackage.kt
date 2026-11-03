package com.al3xiel.my_finances.feature.diagnostics.domain.model

/** `file` es lo que se guarda (el ZIP, o el ZIP dentro del contenedor cifrado); `zip` y `packageHash` son siempre los del ZIP en claro. */
class BuiltPackage(val file: ByteArray, val zip: ByteArray, val packageHash: String, val contentHash: String)
