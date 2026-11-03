package com.al3xiel.my_finances.feature.diagnostics.domain.anonymize

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReconciliationCancelled
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.model.event.ReconciliationResolved
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolCreated
import com.al3xiel.my_finances.domain.model.event.TagCreated
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El paquete anonimizado lo abre el código actual: cada evento reescrito debe seguir decodificando a su tipo. */
class AnonymizedEventsStillDecodeTest {
    private val pen = CurrencyCode.PEN

    private val samples: List<DomainEvent> = listOf(
        AccountCreated(AccountId("a1"), "BBVA", AccountType.BANK, pen, null, Money(1, pen)),
        CategoryCreated(CategoryId("c1"), "Comida", "icon", "#fff", null),
        TagCreated(TagId("t1"), "Viaje", TagKind.TOPIC, null),
        PersonCreated(PersonId("p1"), "Ana"),
        IncomeSourceCreated(IncomeSourceId("s1"), "Empresa", PersonId("p1")),
        IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(5, pen), IncomeSourceId("s1"), null, "nota"),
        MovementCorrected(MovementId("m1"), MovementSnapshot.Income(AccountId("a1"), Money(6, pen), IncomeSourceId("s1"), "otra"), "motivo", null),
        MovementVoided(MovementId("m1"), "motivo", null),
        ReimbursementPoolCreated(PoolId("pool1"), "Casa", PersonId("p1"), PoolKind.PERMANENT, null, emptyList()),
        ExpectedIncomeCancelled(ExpectedIncomeId("e1"), "motivo"),
        ReconciliationResolved(ReconciliationId("r1"), ReconciliationCause.UNKNOWN, null, "nota"),
        ReconciliationCancelled(ReconciliationId("r1"), "motivo"),
    )

    @Test
    fun everyRewrittenEventDecodesToTheSameTypeAndAggregate() {
        val store = FakeEventStore()
        runBlocking { store.append("c", samples.map { NewEvent(it, LocalDate.of(2026, 10, 27)) }) }

        val result = EventAnonymizer().anonymize(store.events)

        result.events.forEach { out ->
            val decoded = EventCodec.decode(out.source.event.type, out.source.schemaVersion, out.payload)
            assertEquals(out.source.event.type, decoded.type)
            assertEquals(out.source.event.aggregateId, decoded.aggregateId)
        }
        assertTrue(result.events.size == samples.size)
    }
}
