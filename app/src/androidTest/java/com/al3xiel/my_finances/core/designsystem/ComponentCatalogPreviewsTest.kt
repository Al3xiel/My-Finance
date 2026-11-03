package com.al3xiel.my_finances.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Rule
import org.junit.Test

class ComponentCatalogPreviewsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(mode: ThemeMode, fontScale: Float = 1f, hidden: Boolean = false) = rule.setContent {
        CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, fontScale),
            LocalHideAmounts provides hidden,
        ) {
            MyfinancesTheme(themeMode = mode) { ComponentCatalog() }
        }
    }

    @Test fun composesInLight() {
        show(ThemeMode.LIGHT)
        rule.onNodeWithText(COMPONENT_CATALOG_TITLE).assertIsDisplayed()
    }

    @Test fun composesInDark() {
        show(ThemeMode.DARK)
        rule.onNodeWithText(COMPONENT_CATALOG_TITLE).assertIsDisplayed()
    }

    @Test fun composesInAmoled() {
        show(ThemeMode.AMOLED)
        rule.onNodeWithText(COMPONENT_CATALOG_TITLE).assertIsDisplayed()
    }

    @Test fun composesAtTwiceTheFontSize() {
        show(ThemeMode.LIGHT, fontScale = 2f)
        rule.onNodeWithText(COMPONENT_CATALOG_TITLE).assertIsDisplayed()
    }

    @Test fun composesWithHiddenAmounts() {
        show(ThemeMode.LIGHT, hidden = true)
        rule.onNodeWithText(COMPONENT_CATALOG_TITLE).assertIsDisplayed()
    }

    @Test fun showsANegativeBalance() {
        show(ThemeMode.LIGHT)
        rule.onAllNodesWithText("-S/ 50.00").onFirst().assertExists()
    }
}
