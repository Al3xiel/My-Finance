package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.al3xiel.my_finances.core.text.formatBasisPoints
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PieChartContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val slices = listOf(
        ChartSlice("Comida", 6_000, Color.Red),
        ChartSlice("Casa", 3_500, Color.Blue),
        ChartSlice("Café", 500, Color.Green),
    )

    private fun show(onSliceClick: ((Int) -> Unit)? = null, list: List<ChartSlice> = slices) {
        rule.setContent { MyfinancesTheme { PieChart(list, Modifier.fillMaxWidth(), onSliceClick) } }
    }

    @Test fun wideSlicesAreLabelledDirectlyWithNameAndPercentage() {
        show()
        rule.onNodeWithText("Comida " + formatBasisPoints(6_000)).assertIsDisplayed()
        rule.onNodeWithText("Casa " + formatBasisPoints(3_500)).assertIsDisplayed()
    }

    @Test fun aNarrowSliceHasNoDirectLabelButIsStillDescribed() {
        show()
        rule.onNodeWithText("Café " + formatBasisPoints(500)).assertDoesNotExist()
        rule.onNode(hasContentDescription("Café", substring = true)).assertExists()
    }

    @Test fun theDescriptionNamesEverySliceForScreenReaders() {
        show()
        rule.onNode(hasContentDescription("Comida", substring = true) and hasContentDescription("Casa", substring = true)).assertExists()
    }

    @Test fun tappingADirectLabelReportsItsSlice() {
        var tapped: Int? = null
        show({ tapped = it })
        rule.onNodeWithText("Casa " + formatBasisPoints(3_500)).performClick()
        assertEquals(1, tapped)
    }

    @Test fun tappingTheRingReportsTheSliceUnderTheFinger() {
        var tapped: Int? = null
        show({ tapped = it }, listOf(ChartSlice("A", 5_000, Color.Red), ChartSlice("B", 5_000, Color.Blue)))
        rule.onNodeWithTag(PIE_CHART_RING_TAG).performTouchInput {
            // A la derecha del centro: 90° desde arriba, dentro de la primera mitad.
            click(Offset(center.x + width * 0.36f, center.y))
        }
        assertEquals(0, tapped)
        rule.onNodeWithTag(PIE_CHART_RING_TAG).performTouchInput {
            click(Offset(center.x - width * 0.36f, center.y))
        }
        assertEquals(1, tapped)
    }

    @Test fun withoutAnyCallbackTheChartDoesNotAskForTaps() {
        show(null)
        rule.onNodeWithText("Comida " + formatBasisPoints(6_000)).assertIsDisplayed()
    }
}
