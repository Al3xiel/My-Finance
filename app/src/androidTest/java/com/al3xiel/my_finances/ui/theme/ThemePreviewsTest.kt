package com.al3xiel.my_finances.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import org.junit.Rule
import org.junit.Test

class ThemePreviewsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(mode: ThemeMode, fontScale: Float = 1f, dynamic: Boolean = false) = rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            MyfinancesTheme(themeMode = mode, dynamicColor = dynamic) { ThemeGallery() }
        }
    }

    private fun assertComposes(mode: ThemeMode) {
        show(mode)
        rule.onNodeWithText(GALLERY_TITLE).assertIsDisplayed()
    }

    @Test fun theGalleryComposesInLight() = assertComposes(ThemeMode.LIGHT)

    @Test fun theGalleryComposesInDark() = assertComposes(ThemeMode.DARK)

    @Test fun theGalleryComposesInAmoled() = assertComposes(ThemeMode.AMOLED)

    @Test fun theGalleryComposesAtTwiceTheFontSize() {
        show(ThemeMode.LIGHT, fontScale = 2f)
        rule.onNodeWithText(GALLERY_TITLE).assertIsDisplayed()
    }

    @Test fun theGalleryComposesWithTheSystemColor() {
        show(ThemeMode.DARK, dynamic = true)
        rule.onNodeWithText(GALLERY_TITLE).assertIsDisplayed()
    }
}
