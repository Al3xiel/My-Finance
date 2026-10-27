package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.ReimbursementId
import com.al3xiel.my_finances.domain.model.RepaymentId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.TagId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

/** Una muestra por evento de cúmulos y deudas; la suite de serialización general las suma. */
val poolLoanSamples: List<DomainEvent> = listOf(
    ReimbursementPoolCreated(PoolId("pl1"), "Perros – mamá", PersonId("pe1"), PoolKind.PERMANENT, CategoryId("c1"), listOf(TagId("t1"))),
    ReimbursementPoolRenamed(PoolId("pl1"), "Perros"),
    ReimbursementPoolArchived(PoolId("pl1")),
    SettlementStatementGenerated(StatementId("st1"), PoolId("pl1"), PoolPeriodId("pp1"), listOf(PartId("p1")), pen(12_000), StatementFormat.PDF),
    ReimbursementReceived(
        ReimbursementId("r1"), MovementId("m9"), PersonId("pe1"), AccountId("a1"), pen(12_000),
        AllocationStrategy.OLDEST_FIRST, listOf(AllocationData(PartId("p1"), pen(12_000))), null,
    ),
    LoanGranted(LoanId("l1"), MovementId("m7"), PersonId("pe2"), AccountId("a1"), pen(5_000), "nota"),
    LoanRepaymentReceived(RepaymentId("rp1"), MovementId("m8"), LoanId("l1"), AccountId("a1"), pen(2_000)),
)

class PoolLoanEventSerializationTest {
    private val samples = poolLoanSamples

    @Test
    fun everyEventRoundTrips() {
        samples.forEach { assertEquals(it, EventCodec.decode(it.type, it.schemaVersion, EventCodec.encode(it))) }
    }

    @Test
    fun typesAreRegistered() {
        assertTrue(EventCodec.types.containsAll(samples.map { it.type }))
    }

    @Test
    fun reimbursementAndLoanPayloadsCarryMovementId() {
        samples.filter { it is ReimbursementReceived || it is LoanGranted || it is LoanRepaymentReceived }
            .forEach { assertTrue(it.type, "movementId" in EventCodec.encode(it)) }
    }

    @Test
    fun poolAggregateIsThePool() {
        assertEquals(AggregateType.POOL, samples.first().aggregateType)
        assertEquals("pl1", samples.first().aggregateId)
    }
}
