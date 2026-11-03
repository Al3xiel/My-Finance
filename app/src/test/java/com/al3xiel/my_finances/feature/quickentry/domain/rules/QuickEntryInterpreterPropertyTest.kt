package com.al3xiel.my_finances.feature.quickentry.domain.rules

import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogEntry
import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogSnapshot
import com.al3xiel.my_finances.feature.quickentry.domain.model.EntityKind
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickEntryInterpreterPropertyTest {
    private val snapshot = CatalogSnapshot(
        listOf(
            CatalogEntry(EntityKind.ACCOUNT, "a", listOf("BBVA")),
            CatalogEntry(EntityKind.CATEGORY, "c", listOf("Mercado", "Mercado libre")),
            CatalogEntry(EntityKind.TAG, "t", listOf("Mamá")),
        ),
    )
    private val alphabet = "abcmerdoáñ +-.,/S0123456789\t́😀"

    private fun randomText(random: Random): String =
        buildString { repeat(random.nextInt(40)) { append(alphabet[random.nextInt(alphabet.length)]) } }

    @Test fun `never throws and is deterministic for any text`() {
        val random = Random(42)
        repeat(2_000) {
            val text = randomText(random)
            val first = QuickEntryInterpreter.interpret(text, snapshot)
            val second = QuickEntryInterpreter.interpret(text, snapshot)
            assertEquals(text, first, second)
        }
    }

    @Test fun `empty snapshot never matches`() {
        val r = QuickEntryInterpreter.interpret("45 bbva mercado", CatalogSnapshot(emptyList()))
        assertEquals(4500L, r.amountMinor)
        assertEquals("bbva mercado", r.note)
    }
}
