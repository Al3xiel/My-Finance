package com.al3xiel.my_finances.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {
    @Test fun `shapes follow the 12 16 24 28 dp scale`() {
        assertEquals(RoundedCornerShape(12.dp), AppShapes.small)
        assertEquals(RoundedCornerShape(16.dp), AppShapes.medium)
        assertEquals(RoundedCornerShape(24.dp), AppShapes.large)
        assertEquals(RoundedCornerShape(28.dp), AppShapes.extraLarge)
    }

    @Test fun `spacing is a 4 dp grid`() {
        val s = Spacing()
        listOf(s.xs, s.sm, s.md, s.lg, s.xl, s.xxl).forEach { assertEquals(0f, it.value % 4f, 0f) }
        assertEquals(4.dp, s.xs)
        assertEquals(16.dp, s.lg)
    }
}
