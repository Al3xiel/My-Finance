package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.Navigator
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    private class RecordingNavigator : Navigator {
        var backs = 0
        override fun push(route: Route) = Unit
        override fun back() { backs++ }
    }

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(navigator: Navigator = RecordingNavigator(), fontScale: Float = 1f, showBack: Boolean = true) = rule.setContent {
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalDensity provides Density(LocalDensity.current.density, fontScale),
        ) {
            MyfinancesTheme {
                ScreenScaffold(title = "Cuentas", showBack = showBack) { padding ->
                    Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).testTag("content")) {
                        repeat(40) { Text("Fila $it", Modifier.fillMaxWidth().height(56.dp)) }
                    }
                }
            }
        }
    }

    @Test fun showsTheTitle() {
        show()
        rule.onNodeWithText("Cuentas").assertIsDisplayed()
    }

    @Test fun backGoesThroughTheNavigator() {
        val navigator = RecordingNavigator()
        show(navigator)
        rule.onNodeWithContentDescription(string(R.string.scaffold_back)).performClick()
        assertEquals(1, navigator.backs)
    }

    @Test fun theTabVersionHasNoBackButton() {
        show(showBack = false)
        rule.onNodeWithContentDescription(string(R.string.scaffold_back)).assertDoesNotExist()
    }

    @Test fun theHeaderCollapsesWhenTheContentScrolls() {
        show()
        val expanded = rule.onNodeWithTag(SCREEN_SCAFFOLD_BAR_TAG).getUnclippedBoundsInRoot().height
        rule.onNodeWithTag("content").performTouchInput { swipeUp() }
        rule.waitForIdle()
        val collapsed = rule.onNodeWithTag(SCREEN_SCAFFOLD_BAR_TAG).getUnclippedBoundsInRoot().height
        assertTrue("el encabezado debía contraerse: $expanded -> $collapsed", collapsed < expanded)
    }

    @Test fun staysUsableAtTwiceTheFontSize() {
        show(fontScale = 2f)
        rule.onNodeWithText("Cuentas").assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.scaffold_back)).assertIsDisplayed()
    }
}
