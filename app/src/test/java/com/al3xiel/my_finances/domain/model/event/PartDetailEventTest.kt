package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * El detalle de una parte del gasto (`ExpensePartData.note`) es un cambio de un evento existente: los eventos nuevos se
 * escriben con `schemaVersion` 2 y los guardados con la versión 1 se leen con su upcaster, sin reescribirlos.
 */
class PartDetailEventTest {
    private val pen = CurrencyCode.PEN

    private fun part(id: String, note: String?) =
        ExpensePartData(PartId(id), Money(300, pen), CategoryId("c1"), emptyList(), null, null, note)

    private val registered = ExpenseRegistered(
        MovementId("m1"), AccountId("a1"), Money(600, pen), listOf(part("p1", "Pastillas de Boby"), part("p2", null)), null, null,
    )

    /** El payload como lo escribía la versión 1: ninguna parte llevaba `note`. */
    private fun asVersionOne(payload: String): String = Json.encodeToString(JsonElement.serializer(), withoutPartNotes(Json.parseToJsonElement(payload)))

    private fun withoutPartNotes(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { !(it == "note" && element["partId"] != null) }.mapValues { withoutPartNotes(it.value) },
        )
        is JsonArray -> JsonArray(element.map(::withoutPartNotes))
        else -> element
    }

    @Test fun `new events are written with schema version two`() {
        assertEquals(2, registered.schemaVersion)
        assertEquals(2, DomainEvent.CURRENT_SCHEMA_VERSION)
    }

    @Test fun `a part detail survives the codec`() {
        val decoded = EventCodec.decode(registered.type, 2, EventCodec.encode(registered)) as ExpenseRegistered

        assertEquals(listOf("Pastillas de Boby", null), decoded.parts.map { it.note })
        assertEquals(registered, decoded)
    }

    @Test fun `a version one expense decodes with no detail in any part`() {
        val v1 = asVersionOne(EventCodec.encode(registered))
        assertFalse("la v1 no tiene detalle en las partes", v1.contains("Boby"))

        val decoded = EventCodec.decode(registered.type, 1, v1) as ExpenseRegistered

        assertEquals(listOf(null, null), decoded.parts.map { it.note })
        assertEquals(registered.parts.map { it.partId }, decoded.parts.map { it.partId })
        assertEquals(registered.total, decoded.total)
    }

    @Test fun `a version one correction with an expense snapshot decodes with no detail`() {
        val snapshot = MovementSnapshot.Expense(AccountId("a1"), Money(600, pen), registered.parts, "nota del gasto")
        val corrected = MovementCorrected(MovementId("m1"), snapshot, "monto mal", null)
        val v1 = asVersionOne(EventCodec.encode(corrected))

        val decoded = EventCodec.decode(corrected.type, 1, v1) as MovementCorrected
        val expense = decoded.snapshot as MovementSnapshot.Expense

        assertEquals(listOf(null, null), expense.parts.map { it.note })
        assertEquals("nota del gasto", expense.note)
        assertEquals("monto mal", decoded.reason)
    }

    @Test fun `a version one correction of an income is left alone`() {
        val corrected = MovementCorrected(
            MovementId("m1"), MovementSnapshot.Income(AccountId("a1"), Money(500, pen), IncomeSourceId("s1"), "sueldo"), "monto mal", null,
        )

        assertEquals(corrected, EventCodec.decode(corrected.type, 1, EventCodec.encode(corrected)))
    }

    @Test fun `events that did not change decode from version one exactly as before`() {
        val untouched = IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(500, pen), IncomeSourceId("s1"), null, "sueldo")

        assertEquals(untouched, EventCodec.decode(untouched.type, 1, EventCodec.encode(untouched)))
    }
}
