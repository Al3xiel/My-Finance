package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SankeyColorsTest {
    private val palette = listOf(Color.Red, Color.Green, Color.Blue)

    private val columns = listOf(
        listOf(SankeyBox("s1", "Sueldo", 50), SankeyBox("s2", "Freelance", 10)),
        listOf(SankeyBox("a", "BBVA", 60)),
        listOf(SankeyBox("c", "Comida", 40), SankeyBox("t", "Transporte", 20)),
    )

    @Test fun `each node gets the next color of the palette in reading order`() {
        val colors = sankeyNodeColors(columns, palette)
        assertEquals(Color.Red, colors["s1"])
        assertEquals(Color.Green, colors["s2"])
        assertEquals(Color.Blue, colors["a"])
        assertEquals(Color.Red, colors["c"])
        assertEquals(Color.Green, colors["t"])
    }

    @Test fun `nodes of the same column never share a color while the palette lasts`() {
        val colors = sankeyNodeColors(columns, palette)
        assertNotEquals(colors["s1"], colors["s2"])
        assertNotEquals(colors["c"], colors["t"])
    }

    @Test fun `an empty palette or no columns colors nothing`() {
        assertEquals(emptyMap<String, Color>(), sankeyNodeColors(emptyList(), palette))
        assertEquals(emptyMap<String, Color>(), sankeyNodeColors(columns, emptyList()))
    }
}
