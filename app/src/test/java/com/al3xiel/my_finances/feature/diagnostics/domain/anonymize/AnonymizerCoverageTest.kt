package com.al3xiel.my_finances.feature.diagnostics.domain.anonymize

import com.al3xiel.my_finances.domain.model.event.EventCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lista permitida (D52): un texto plano nuevo en cualquier evento obliga a clasificarlo. Si este test falla, el campo nuevo
 * se agregó a un evento y nadie decidió si viaja, se reemplaza por seudónimo o se elimina.
 */
class AnonymizerCoverageTest {
    private val allFields = EventCodec.types.sorted().flatMap { PlainStringFields.of(it) }

    @Test
    fun theWalkFindsTheKnownTextFields() {
        val keys = allFields.map { it.key }.toSet()

        listOf(
            "AccountCreated.name", "PersonCreated.alias", "IncomeRegistered.note", "MovementCorrected.snapshot.note",
            "MovementCorrected.reason", "ExpectedIncomeCancelled.reason", "DiagnosticExportCreated.pseudonymMap.*.entityId",
        ).forEach { assertTrue("no se encontró $it", it in keys) }
    }

    @Test
    fun everyPlainStringOfEveryEventIsClassified() {
        val unclassified = allFields.map { it.key }.filter { FieldClassification.rules[it] == null }

        assertEquals("textos sin clasificar", emptyList<String>(), unclassified)
    }

    @Test
    fun noRuleRefersToAFieldThatNoLongerExists() {
        val keys = allFields.map { it.key }.toSet()

        assertEquals("reglas huérfanas", emptyList<String>(), FieldClassification.rules.keys.filter { it !in keys })
    }

    @Test
    fun everyPseudonymRuleNamesTheKindOfEntityItReplaces() {
        val broken = FieldClassification.rules.filter { (_, rule) -> rule.treatment == Treatment.PSEUDONYM && rule.entityType == null }

        assertTrue(broken.keys.toString(), broken.isEmpty())
    }

    @Test
    fun noteAndReasonFieldsAreNeverKept() {
        val kept = FieldClassification.rules.filter { (key, rule) ->
            rule.treatment == Treatment.KEEP && (key.endsWith(".note") || key.endsWith(".reason") && !key.startsWith("ProjectionsRebuilt"))
        }

        assertTrue(kept.keys.toString(), kept.isEmpty())
    }

    @Test
    fun idsAndDatesAreNotReportedAsText() {
        val keys = allFields.map { it.key }.toSet()

        assertTrue("AccountCreated.accountId" !in keys)
        assertTrue("FundCreated.startsOn" !in keys)
        assertTrue("AccountCreated.openingBalance.currency" !in keys)
    }
}
