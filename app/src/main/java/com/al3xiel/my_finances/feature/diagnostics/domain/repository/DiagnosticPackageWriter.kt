package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.feature.diagnostics.domain.model.BuiltPackage
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageInput

/** Arma el `.fdiag` (04 §3.3.1): bytes deterministas, con o sin el contenedor cifrado. Borra la contraseña que recibe. */
interface DiagnosticPackageWriter {
    fun build(input: DiagnosticPackageInput, password: CharArray?): BuiltPackage
}
