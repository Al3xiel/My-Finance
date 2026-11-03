package com.al3xiel.my_finances.core.security.container

enum class BackupPasswordError { TOO_SHORT, TOO_LONG, MISMATCH }

/** Reglas de la contraseña de respaldo (D45). Siempre `CharArray`: el llamador la borra con [wipe] al terminar. */
object BackupPassword {
    const val MIN_LENGTH = 10
    const val MAX_LENGTH = 256

    /** `null` = válida. Sin confirmación solo se revisa el largo (al abrir un archivo existente). */
    fun check(password: CharArray, confirmation: CharArray? = null): BackupPasswordError? = when {
        password.size < MIN_LENGTH -> BackupPasswordError.TOO_SHORT
        password.size > MAX_LENGTH -> BackupPasswordError.TOO_LONG
        confirmation != null && !password.contentEquals(confirmation) -> BackupPasswordError.MISMATCH
        else -> null
    }

    fun wipe(vararg secrets: CharArray) = secrets.forEach { it.fill('\u0000') }
}
