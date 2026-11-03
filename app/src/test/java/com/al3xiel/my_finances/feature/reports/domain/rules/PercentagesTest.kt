package com.al3xiel.my_finances.feature.reports.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class PercentagesTest {
    @Test fun `shares add up to exactly 10000 basis points`() {
        val r = Percentages.basisPoints(listOf("a" to 1L, "b" to 1L, "c" to 1L))
        assertEquals(10_000, r.values.sum())
        assertEquals(3_334, r.getValue("a")) // tie on remainder goes to the smallest id
        assertEquals(3_333, r.getValue("b"))
    }

    @Test fun `empty or zero total gives zeros`() {
        assertEquals(emptyMap<String, Int>(), Percentages.basisPoints(emptyList()))
        assertEquals(mapOf("a" to 0), Percentages.basisPoints(listOf("a" to 0L)))
    }

    @Test fun `largest remainder wins`() {
        val r = Percentages.basisPoints(listOf("a" to 2L, "b" to 1L))
        assertEquals(6_667, r.getValue("a"))
        assertEquals(3_333, r.getValue("b"))
    }
}
