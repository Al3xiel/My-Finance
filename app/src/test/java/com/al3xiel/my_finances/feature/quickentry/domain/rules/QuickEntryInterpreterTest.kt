package com.al3xiel.my_finances.feature.quickentry.domain.rules

import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogEntry
import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogSnapshot
import com.al3xiel.my_finances.feature.quickentry.domain.model.Conflict
import com.al3xiel.my_finances.feature.quickentry.domain.model.EntityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryInterpreterTest {
    private fun entry(kind: EntityKind, id: String, vararg names: String) = CatalogEntry(kind, id, names.toList())

    private val snapshot = CatalogSnapshot(
        listOf(
            entry(EntityKind.ACCOUNT, "bbva", "BBVA"),
            entry(EntityKind.ACCOUNT, "bcp", "BCP"),
            entry(EntityKind.ACCOUNT, "efectivo", "Efectivo"),
            entry(EntityKind.CATEGORY, "mercado", "Mercado"),
            entry(EntityKind.CATEGORY, "comida", "Comida"),
            entry(EntityKind.CATEGORY, "comida-rapida", "Comida rápida"),
            entry(EntityKind.TAG, "pareja", "Pareja"),
            entry(EntityKind.TAG, "mama-tag", "Mamá"),
            entry(EntityKind.POOL, "perros", "Perros", "Luis"),
            entry(EntityKind.POOL, "mama-pool", "Casa de mamá", "Mamá"),
            entry(EntityKind.FUND, "pasajes", "Pasajes"),
            entry(EntityKind.INCOME_SOURCE, "sueldo", "Sueldo"),
        ),
    )

    private fun run(line: String) = QuickEntryInterpreter.interpret(line, snapshot)

    @Test fun `expense with account category and note`() {
        val r = run("45 bbva mercado huevos y pan")
        assertEquals(false, r.isIncome)
        assertEquals(4500L, r.amountMinor)
        assertEquals(listOf("bbva"), r.accountIds)
        assertEquals(listOf("mercado"), r.categoryIds)
        assertEquals("huevos y pan", r.note)
        assertTrue(r.ambiguities.isEmpty())
    }

    @Test fun `order is free`() {
        val r = run("mercado bbva 12,50")
        assertEquals(1250L, r.amountMinor)
        assertEquals(listOf("bbva"), r.accountIds)
        assertEquals(listOf("mercado"), r.categoryIds)
        assertNull(r.note)
    }

    @Test fun `plus makes an income and brings the source token`() {
        val r = run("+50 bcp sueldo")
        assertEquals(true, r.isIncome)
        assertEquals(5000L, r.amountMinor)
        assertEquals(listOf("bcp"), r.accountIds)
        assertEquals(listOf("sueldo"), r.sourceIds)
    }

    @Test fun `a source name in an expense is only a note`() {
        val r = run("45 bbva sueldo")
        assertTrue(r.sourceIds.isEmpty())
        assertEquals("sueldo", r.note)
    }

    @Test fun `pool and tag with the same name are ambiguous, never guessed`() {
        val r = run("45 bbva mercado mamá")
        assertTrue(r.tagIds.isEmpty() && r.poolIds.isEmpty())
        val ambiguity = r.ambiguities.single()
        assertEquals("mamá", ambiguity.text)
        assertEquals(setOf("mama-tag", "mama-pool"), ambiguity.candidates.map { it.id }.toSet())
        assertEquals(setOf(EntityKind.TAG, EntityKind.POOL), ambiguity.candidates.map { it.kind }.toSet())
    }

    @Test fun `responsible alias reaches the pool`() {
        assertEquals(listOf("perros"), run("45 luis").poolIds)
    }

    @Test fun `prefix needs at least three letters`() {
        assertEquals(listOf("mercado"), run("45 mer").categoryIds)
        val short = run("45 me")
        assertTrue(short.categoryIds.isEmpty())
        assertEquals("me", short.note)
    }

    @Test fun `exact beats prefix`() {
        // "comida" is a prefix of "comida rápida" but also an exact name: only the exact one wins.
        val r = run("45 comida")
        assertEquals(listOf("comida"), r.categoryIds)
        assertTrue(r.ambiguities.isEmpty())
    }

    @Test fun `prefix with two matches is ambiguous`() {
        val r = run("45 com")
        assertTrue(r.categoryIds.isEmpty())
        assertEquals(setOf("comida", "comida-rapida"), r.ambiguities.single().candidates.map { it.id }.toSet())
    }

    @Test fun `longest exact multi word name wins over the single words`() {
        val r = run("45 comida rápida bbva")
        assertEquals(listOf("comida-rapida"), r.categoryIds)
        assertEquals(listOf("bbva"), r.accountIds)
        assertNull(r.note)
    }

    @Test fun `two categories are a conflict`() {
        val r = run("45 mercado comida")
        assertEquals(setOf("mercado", "comida"), r.categoryIds.toSet())
        assertTrue(Conflict.CATEGORIES in r.conflicts)
    }

    @Test fun `tags can repeat but not duplicate`() {
        val r = run("45 pareja pareja")
        assertEquals(listOf("pareja"), r.tagIds)
    }

    @Test fun `pool plus fund is a conflict`() {
        val r = run("45 perros pasajes")
        assertTrue(Conflict.POOL_AND_FUND in r.conflicts)
    }

    @Test fun `two accounts are a conflict`() {
        val r = run("45 bbva bcp")
        assertTrue(Conflict.ACCOUNTS in r.conflicts)
    }

    @Test fun `second number goes to the note`() {
        val r = run("45 bbva 3 kilos")
        assertEquals(4500L, r.amountMinor)
        assertEquals("3 kilos", r.note)
    }

    @Test fun `currency prefix with a space`() {
        assertEquals(4500L, run("S/ 45 bbva").amountMinor)
    }

    @Test fun `missing amount stays null`() {
        assertNull(run("bbva mercado").amountMinor)
    }

    @Test fun `unknown names stay in the note`() {
        assertEquals("xyzzy", run("45 bbva xyzzy").note)
    }

    @Test fun `blank line is empty`() {
        val r = run("   ")
        assertNull(r.amountMinor)
        assertNull(r.note)
    }
}
