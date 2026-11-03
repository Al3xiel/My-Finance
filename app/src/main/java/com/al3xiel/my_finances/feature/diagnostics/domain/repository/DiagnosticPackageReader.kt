package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageException
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ParsedPackage
import java.io.InputStream

/** Lee y valida un `.fdiag` que llegó de otra persona (DG5); rechaza con `DiagnosticPackageException`. La contraseña la borra quien llama. */
interface DiagnosticPackageReader {
    fun read(input: InputStream, sizeBytes: Long, password: CharArray?): ParsedPackage
}
