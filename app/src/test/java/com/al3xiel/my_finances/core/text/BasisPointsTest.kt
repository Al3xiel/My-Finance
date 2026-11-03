package com.al3xiel.my_finances.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class BasisPointsTest {
    @Test fun `basis points are formatted with integer arithmetic`() {
        assertEquals("33.34 %", formatBasisPoints(3_334, '.'))
        assertEquals("5,05 %", formatBasisPoints(505, ','))
        assertEquals("100.00 %", formatBasisPoints(10_000, '.'))
        assertEquals("0.00 %", formatBasisPoints(0, '.'))
    }
}
