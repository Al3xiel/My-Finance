package com.al3xiel.my_finances.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NameNormalizerTest {
    @Test
    fun ignoresCaseAccentsAndExtraSpaces() {
        assertEquals(NameNormalizer.key("cafe con leche"), NameNormalizer.key("  Café   CON leche "))
        assertEquals("educacion", NameNormalizer.key("Educación"))
    }

    @Test
    fun differentWordsStayDifferent() {
        assertNotEquals(NameNormalizer.key("Casa"), NameNormalizer.key("Caza"))
    }

    @Test
    fun isTakenComparesAgainstActiveNamesExceptTheOwnOne() {
        val active = listOf("BBVA", "Efectivo")
        assertEquals(true, NameNormalizer.isTaken(active, " efectivo "))
        assertEquals(false, NameNormalizer.isTaken(active, "BCP"))
        assertEquals(false, NameNormalizer.isTaken(active, "bbva", own = "BBVA"))
        assertEquals(true, NameNormalizer.isTaken(active, "Efectivo", own = "BBVA"))
    }
}
