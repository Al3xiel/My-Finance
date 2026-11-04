package com.al3xiel.my_finances.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun `an angle from the top picks the slice that owns it`() {
        val bp = listOf(5_000, 2_500, 2_500)
        assertEquals(0, pieSliceIndexAt(bp, 10f))
        assertEquals(0, pieSliceIndexAt(bp, 179.9f))
        assertEquals(1, pieSliceIndexAt(bp, 180.1f))
        assertEquals(1, pieSliceIndexAt(bp, 269f))
        assertEquals(2, pieSliceIndexAt(bp, 271f))
        assertEquals(2, pieSliceIndexAt(bp, 359.9f))
    }

    @Test fun `angles wrap and empty or zero-width slices are skipped`() {
        val bp = listOf(5_000, 2_500, 2_500)
        assertEquals(2, pieSliceIndexAt(bp, -10f))
        assertEquals(0, pieSliceIndexAt(bp, 370f))
        assertEquals(null, pieSliceIndexAt(emptyList(), 10f))
        assertEquals(1, pieSliceIndexAt(listOf(0, 10_000), 123f))
    }

    @Test fun `only slices wide enough get a direct label`() {
        assertEquals(listOf(0, 1, 3), directLabelIndices(listOf(6_000, 3_000, 399, 601), minBasisPoints = 600))
        assertEquals(listOf(0), directLabelIndices(listOf(10_000)))
        assertEquals(emptyList<Int>(), directLabelIndices(emptyList()))
    }

    @Test fun `the direct label is the name and the percentage`() {
        assertEquals("Comida " + com.al3xiel.my_finances.core.text.formatBasisPoints(6_000), pieLabelText("Comida", 6_000))
    }

    @Test fun `labels that want the same spot are pushed apart and never overlap`() {
        val tops = spreadLabels(desiredCenters = listOf(10f, 12f, 14f), heights = listOf(20f, 20f, 20f), gap = 4f)
        assertEquals(3, tops.size)
        assertTrue(tops[0] >= 0f)
        assertTrue(tops[1] >= tops[0] + 20f + 4f - 0.001f)
        assertTrue(tops[2] >= tops[1] + 20f + 4f - 0.001f)
    }

    @Test fun `labels already apart stay centered where they asked`() {
        val tops = spreadLabels(desiredCenters = listOf(50f, 200f), heights = listOf(20f, 20f), gap = 4f)
        assertEquals(40f, tops[0], 0.001f)
        assertEquals(190f, tops[1], 0.001f)
    }

    @Test fun `no labels place nothing`() {
        assertEquals(emptyList<Float>(), spreadLabels(emptyList(), emptyList(), 4f))
    }
}
