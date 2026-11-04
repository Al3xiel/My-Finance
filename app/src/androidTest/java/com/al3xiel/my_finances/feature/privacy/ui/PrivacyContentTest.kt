package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PrivacyContentTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: PrivacyUiState = PrivacyUiState(), onAction: (PrivacyUiAction) -> Unit = {}) =
        rule.setContent { MyfinancesTheme { PrivacyContent(state, onAction) } }

    @Test fun showsItsTitleAndWhatIsStored() {
        show()
        rule.onNodeWithText("Privacidad").assertIsDisplayed()
    }

    @Test fun theAutoLockOptionsOnlyAppearWithTheLockOn() {
        show(PrivacyUiState(settings = SecuritySettings(lockEnabled = false)))
        rule.onNodeWithText("Siempre").assertDoesNotExist()
    }

    @Test fun withoutACredentialTheLockSaysWhy() {
        show(PrivacyUiState(canEnableLock = false))
        rule.onNodeWithText("Configura una huella, rostro o bloqueo de pantalla en el sistema para activar el bloqueo.").assertIsDisplayed()
    }

    @Test fun startingTheWipeDispatchesIt() {
        val actions = mutableListOf<PrivacyUiAction>()
        show(onAction = { actions += it })
        rule.onNodeWithText("Borrar todos mis datos").performClick()
        assertEquals(PrivacyUiAction.StartWipe, actions.single())
    }

    @Test fun theWipeConfirmationExplainsWhyItIsBlockedUntilThePhraseIsTyped() {
        show(PrivacyUiState(wipeStage = WipeStage.CONFIRM_PHRASE, typedPhrase = ""))
        rule.onNodeWithText("Escribe la frase exacta para activar el botón.").assertIsDisplayed()
        rule.onNodeWithText("Borrar").assertIsNotEnabled()
    }

    @Test fun withTheRightPhraseTheWipeIsEnabledAndTheHintIsGone() {
        show(PrivacyUiState(wipeStage = WipeStage.CONFIRM_PHRASE, typedPhrase = WIPE_CONFIRMATION_PHRASE))
        rule.onNodeWithText("Escribe la frase exacta para activar el botón.").assertDoesNotExist()
        rule.onNodeWithText("Borrar").assertIsEnabled()
    }
}
