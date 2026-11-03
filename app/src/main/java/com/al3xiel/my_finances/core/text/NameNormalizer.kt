package com.al3xiel.my_finances.core.text

import java.text.Normalizer

/** Clave para comparar nombres de catálogo: sin tildes, sin mayúsculas y con los espacios colapsados. */
object NameNormalizer {
    private val diacritics = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun key(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace(spaces, " ")
            .lowercase()

    /** ¿Alguno de los nombres activos ya usa `candidate`? `own` es el nombre actual de la entidad que se renombra. */
    fun isTaken(activeNames: List<String>, candidate: String, own: String? = null): Boolean {
        val key = key(candidate)
        return key != own?.let(::key) && activeNames.any { key(it) == key }
    }
}
