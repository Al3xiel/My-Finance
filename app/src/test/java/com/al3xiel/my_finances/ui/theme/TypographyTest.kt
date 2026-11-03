package com.al3xiel.my_finances.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTest {
    private fun Typography.all(): Map<String, TextStyle> = mapOf(
        "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
        "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
    )

    @Test fun `all fifteen material styles use Inter with explicit size and line height`() {
        val styles = AppTypography.all()
        assertEquals(15, styles.size)
        styles.forEach { (name, style) ->
            assertEquals("$name font", InterFontFamily, style.fontFamily)
            assertTrue("$name size", style.fontSize != TextUnit.Unspecified)
            assertTrue("$name line height", style.lineHeight != TextUnit.Unspecified)
        }
    }

    @Test fun `the scale grows from small to large in each role`() {
        val t = AppTypography
        assertTrue(t.displayLarge.fontSize > t.displayMedium.fontSize && t.displayMedium.fontSize > t.displaySmall.fontSize)
        assertTrue(t.titleLarge.fontSize > t.titleMedium.fontSize && t.titleMedium.fontSize > t.titleSmall.fontSize)
        assertTrue(t.bodyLarge.fontSize > t.bodyMedium.fontSize && t.bodyMedium.fontSize > t.bodySmall.fontSize)
    }

    @Test fun `numeric styles use tabular figures`() {
        val numeric = NumericStyles.all()
        assertEquals(3, numeric.size)
        numeric.forEach { (name, style) ->
            assertTrue("$name tnum", style.fontFeatureSettings.orEmpty().contains("tnum"))
            assertEquals("$name font", InterFontFamily, style.fontFamily)
        }
    }

    @Test fun `text styles keep proportional figures`() {
        assertNotEquals("tnum", AppTypography.bodyLarge.fontFeatureSettings)
    }
}
