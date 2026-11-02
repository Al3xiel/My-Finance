package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SankeyDiagramTest {
    @get:Rule
    val rule = createComposeRule()

    private val columns = listOf(
        listOf(SankeyBox("s1", "Sueldo", 50), SankeyBox("s2", "Freelance", 10)),
        listOf(SankeyBox("a", "BBVA", 60)),
        listOf(SankeyBox("c", "Comida", 60)),
    )
    private val flows = listOf(SankeyFlow("s1", "a", 50), SankeyFlow("s2", "a", 10), SankeyFlow("a", "c", 60))

    @Test
    fun theEquivalentTableListsEveryFlowWithItsAmount() {
        rule.setContent { SankeyDiagram(columns, flows, amountLabel = { "$it u" }) }
        rule.onNodeWithText("Sueldo → BBVA").assertIsDisplayed()
        rule.onNodeWithText("Freelance → BBVA").assertIsDisplayed()
        rule.onNodeWithText("BBVA → Comida").assertIsDisplayed()
        rule.onNodeWithText("50 u").assertIsDisplayed()
    }

    @Test
    fun theCanvasHasAnAccessibleDescriptionWithTheFlows() {
        rule.setContent { SankeyDiagram(columns, flows, amountLabel = { "$it u" }) }
        rule.onNode(hasContentDescription("Sueldo → BBVA", substring = true)).assertExists()
    }

    @Test
    fun withoutFlowsNothingIsDrawn() {
        rule.setContent { SankeyDiagram(columns, emptyList(), amountLabel = { "$it u" }) }
        assertEquals(0, rule.onAllNodesWithText("→", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun layoutGivesEachNodeAHeightProportionalToItsValue() {
        val layout = sankeyLayout(columns, totalHeight = 130f, gap = 10f)
        // la columna mayor suma 60 y la de fuentes tiene 2 nodos: escala = (130 − 10) / 60 = 2
        assertEquals(100f, layout.getValue("s1").height, 0.001f)
        assertEquals(20f, layout.getValue("s2").height, 0.001f)
        assertEquals(110f, layout.getValue("s2").top, 0.001f)
        assertEquals(120f, layout.getValue("a").height, 0.001f)
    }
}
