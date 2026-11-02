package com.al3xiel.my_finances.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class PieChartTest {
    @Test fun `sweeps close the circle when basis points add up to 10000`() {
        val sweeps = pieSweeps(listOf(5_000, 2_500, 2_500))
        assertEquals(listOf(180f, 90f, 90f), sweeps)
        assertEquals(360f, sweeps.sum(), 0.001f)
    }

    @Test fun `no slices draw nothing`() {
        assertEquals(emptyList<Float>(), pieSweeps(emptyList()))
    }
}
