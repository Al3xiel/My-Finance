package com.al3xiel.my_finances.ui.accessibility

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.feature.privacy.ui.PrivacyContent
import com.al3xiel.my_finances.feature.privacy.ui.PrivacyUiState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.feature.privacy.ui.AboutContent
import com.al3xiel.my_finances.feature.privacy.ui.AboutUiState
import com.al3xiel.my_finances.feature.privacy.ui.SettingsContent
import com.al3xiel.my_finances.feature.privacy.ui.SettingsUiState
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auditoría automática (contraste, objetivos táctiles, etiquetas) de pantallas clave en los tres temas. */
class AccessibilityAuditTest {
    @get:Rule
    val rule = createComposeRule()

    private fun audit(mode: ThemeMode, content: @Composable () -> Unit) {
        rule.enableAccessibilityChecks()
        rule.setContent { MyfinancesTheme(themeMode = mode, content = content) }
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Composable
    private fun settings() = SettingsContent(SettingsUiState(), {}, dynamicColorSupported = true, sections = { Text("Recordatorios") })

    @Composable
    private fun about() = AboutContent(AboutUiState(version = "1.0", devToolsEnabled = true), {}, onOpenDevTools = {})

    @Test fun settingsLight() = audit(ThemeMode.LIGHT) { settings() }
    @Test fun settingsDark() = audit(ThemeMode.DARK) { settings() }
    @Test fun settingsAmoled() = audit(ThemeMode.AMOLED) { settings() }
    @Test fun aboutLight() = audit(ThemeMode.LIGHT) { about() }
    @Test fun aboutDark() = audit(ThemeMode.DARK) { about() }
    @Test fun aboutAmoled() = audit(ThemeMode.AMOLED) { about() }

    @Composable
    private fun privacy() = PrivacyContent(PrivacyUiState(), {})

    @Test fun privacyLight() = audit(ThemeMode.LIGHT) { privacy() }
    @Test fun privacyDark() = audit(ThemeMode.DARK) { privacy() }
    @Test fun privacyAmoled() = audit(ThemeMode.AMOLED) { privacy() }

    /** Control: la auditoría de verdad detecta un objetivo táctil pequeño y sin etiqueta. */
    @Test fun theAuditCatchesATinyUnlabelledClickable() {
        val caught = try {
            audit(ThemeMode.LIGHT) { Box(Modifier.size(20.dp).background(Color.Gray).clickable { }) }
            false
        } catch (e: RuntimeException) {
            true
        }
        assertTrue("la auditoría no detectó el objetivo pequeño", caught)
    }
}
