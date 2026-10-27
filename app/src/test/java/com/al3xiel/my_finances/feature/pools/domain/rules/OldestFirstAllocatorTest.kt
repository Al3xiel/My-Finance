package com.al3xiel.my_finances.feature.pools.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.AllocationData
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class OldestFirstAllocatorTest {
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun part(id: String, day: Int, seq: Long, pending: Long) =
        PendingPart(PartId(id), LocalDate.of(2026, 10, day), seq, pen(pending))

    private fun ok(result: Outcome<List<AllocationData>>) = (result as Outcome.Ok).value

    @Test
    fun settlesOldestOccurredOnFirst() {
        val parts = listOf(part("new", 20, 1, 100), part("old", 5, 2, 100))
        val result = ok(OldestFirstAllocator.allocate(parts, pen(100)))
        assertEquals(listOf(AllocationData(PartId("old"), pen(100))), result)
    }

    @Test
    fun tiesOnDateBreakByCreatedSequence() {
        val parts = listOf(part("b", 5, 8, 100), part("a", 5, 3, 100))
        val result = ok(OldestFirstAllocator.allocate(parts, pen(100)))
        assertEquals(listOf(PartId("a")), result.map { it.partId })
    }

    @Test
    fun partialPaymentLeavesTheLastPartPartiallySettled() {
        val parts = listOf(part("a", 1, 1, 100), part("b", 2, 2, 100))
        val result = ok(OldestFirstAllocator.allocate(parts, pen(150)))
        assertEquals(listOf(AllocationData(PartId("a"), pen(100)), AllocationData(PartId("b"), pen(50))), result)
    }

    @Test
    fun allocationsSumToTheAmountToTheCent() {
        val parts = listOf(part("a", 1, 1, 33), part("b", 2, 2, 33), part("c", 3, 3, 33))
        val result = ok(OldestFirstAllocator.allocate(parts, pen(70)))
        assertEquals(70L, result.sumOf { it.amount.amountMinor })
    }

    @Test
    fun amountAbovePendingIsRejected() {
        val result = OldestFirstAllocator.allocate(listOf(part("a", 1, 1, 100)), pen(101))
        assertEquals(Outcome.Failed(BusinessError.ExceedsPending), result)
    }

    @Test
    fun nonPositiveAmountIsRejected() {
        val result = OldestFirstAllocator.allocate(listOf(part("a", 1, 1, 100)), pen(0))
        assertEquals(Outcome.Failed(BusinessError.InvalidAmount), result)
    }

    @Test
    fun partsWithNothingPendingAreSkipped() {
        val parts = listOf(part("paid", 1, 1, 0), part("open", 2, 2, 40))
        val result = ok(OldestFirstAllocator.allocate(parts, pen(40)))
        assertEquals(listOf(PartId("open")), result.map { it.partId })
    }
}
