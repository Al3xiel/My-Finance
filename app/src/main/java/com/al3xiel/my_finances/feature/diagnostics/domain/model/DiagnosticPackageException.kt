package com.al3xiel.my_finances.feature.diagnostics.domain.model

import java.io.IOException

/** Por qué no se acepta un `.fdiag`; el mensaje es solo el motivo: nunca el contenido del archivo ni la contraseña. */
class DiagnosticPackageException(val reason: Reason) : IOException(reason.name) {
    enum class Reason {
        NOT_A_PACKAGE,
        TOO_LARGE,
        TOO_MANY_ENTRIES,
        UNEXPECTED_ENTRY,
        DUPLICATE_ENTRY,
        MISSING_ENTRY,
        ENTRY_TOO_LARGE,
        TOO_MANY_EVENTS,
        LINE_TOO_LONG,
        MALFORMED,
        HASH_MISMATCH,
        NEWER_VERSION,

        /** Tipo de evento desconocido o `schemaVersion` mayor al soportado: se muestra el mismo mensaje (DG5). */
        UNKNOWN_EVENT,
        PASSWORD_REQUIRED,
        WRONG_PASSWORD,
    }
}
