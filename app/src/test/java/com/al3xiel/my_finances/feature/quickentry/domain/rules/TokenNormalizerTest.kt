package com.al3xiel.my_finances.feature.quickentry.domain.rules

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenNormalizerTest {
    @Test fun `strips accents and lowercases`() {
        assertEquals("mama", TokenNormalizer.normalize("Mamá"))
        assertEquals("cafe", TokenNormalizer.normalize("CAFÉ"))
    }

    @Test fun `enie loses its tilde like any other mark`() {
        assertEquals("ano nuevo", TokenNormalizer.normalize("Año Nuevo"))
    }

    @Test fun `trims and keeps inner text`() {
        assertEquals("pasajes ida", TokenNormalizer.normalize("  Pasajes Ida "))
    }

    @Test fun `ignores the phone language`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("inicio", TokenNormalizer.normalize("INICIO"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
