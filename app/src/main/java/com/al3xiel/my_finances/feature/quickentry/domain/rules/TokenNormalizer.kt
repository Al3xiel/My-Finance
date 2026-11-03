package com.al3xiel.my_finances.feature.quickentry.domain.rules

import java.text.Normalizer
import java.util.Locale

/** A diferencia de `core.text.NameNormalizer` (claves de catálogo, idioma del teléfono), fija `Locale.ROOT` (D63). Compara nombres sin tildes ni mayúsculas, igual en cualquier idioma del teléfono. */
object TokenNormalizer {
    private val marks = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()
}
