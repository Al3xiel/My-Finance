package com.al3xiel.my_finances.feature.backup.ui

import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import com.al3xiel.my_finances.feature.backup.domain.model.BackupStatus
import java.time.Instant
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.feature.backup.domain.usecase.ChainStatus
import com.al3xiel.my_finances.feature.backup.domain.usecase.BackupSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackupScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(state: BackupUiState, actions: MutableList<BackupUiAction> = mutableListOf()) {
        rule.setContent { MyfinancesTheme { BackupContent(state, onAction = { actions += it }, hasPermission = { true }) } }
    }

    @Test
    fun createButtonStartsTheCreateFlow() {
        val actions = mutableListOf<BackupUiAction>()
        show(BackupUiState(), actions)
        rule.onNodeWithText(string(R.string.backup_create)).performClick()
        assertEquals(BackupUiAction.StartCreate, actions.single())
    }

    @Test
    fun restoreIsDisabledWithAnExplanationWhenThereIsData() {
        show(BackupUiState(canRestore = false))
        rule.onNodeWithText(string(R.string.backup_restore)).assertIsNotEnabled()
        rule.onNodeWithText(string(R.string.backup_restore_unavailable)).assertIsDisplayed()
    }

    @Test
    fun restoreIsEnabledOnAnEmptyDatabase() {
        show(BackupUiState(canRestore = true))
        rule.onNodeWithText(string(R.string.backup_restore)).assertIsEnabled()
    }

    @Test
    fun aBrokenChainIsWarnedAbout() {
        show(BackupUiState(chain = ChainStatus.Broken(3, ChainVerification.Reason.HASH_MISMATCH)))
        rule.onNodeWithText(string(R.string.backup_chain_broken)).assertIsDisplayed()
    }

    @Test
    fun thePasswordDialogOnlySubmitsWhenTheUserConfirms() {
        val actions = mutableListOf<BackupUiAction>()
        show(BackupUiState(stage = BackupStage.CREATE_PASSWORD), actions)
        rule.onNodeWithText(string(R.string.backup_password_title)).assertIsDisplayed()
        assertTrue(actions.isEmpty())
        rule.onNodeWithText(string(R.string.backup_password_cancel)).performClick()
        assertEquals(BackupUiAction.Cancel, actions.single())
    }

    @Test
    fun theRestoreConfirmationShowsCountsAndNoAmounts() {
        val summary = BackupSummary(42, 0L, "1.0", chainIntact = true, firstOccurredOn = "2026-10-01", lastOccurredOn = "2026-11-02")
        show(BackupUiState(stage = BackupStage.RESTORE_CONFIRM, summary = summary))
        rule.onNodeWithText(string(R.string.backup_restore_confirm)).assertIsDisplayed()
    }

    @Test
    fun aMessageIsShown() {
        show(BackupUiState(message = BackupMessage.WRONG_PASSWORD_OR_TAMPERED))
        rule.onNodeWithText(string(R.string.backup_message_wrong_password)).assertIsDisplayed()
    }

    @Test
    fun showsItsTitle() {
        show(BackupUiState())
        rule.onNodeWithText("Respaldo").assertIsDisplayed()
    }

    @Test
    fun withoutAnyBackupItSaysSoWithABadge() {
        show(BackupUiState(status = BackupStatus(null, null, null, overdue = false)))
        rule.onNodeWithText(string(R.string.backup_last_none)).assertIsDisplayed()
        rule.onNodeWithText("Sin respaldo").assertIsDisplayed()
    }

    @Test
    fun anOverdueBackupCarriesTheOverdueBadgeBesideItsExplanation() {
        show(BackupUiState(status = BackupStatus(Instant.EPOCH, Instant.EPOCH, 40, overdue = true)))
        rule.onNodeWithText("Vencido").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.backup_overdue)).assertIsDisplayed()
    }

    @Test
    fun aCurrentBackupCarriesTheUpToDateBadge() {
        show(BackupUiState(status = BackupStatus(Instant.EPOCH, Instant.EPOCH, 0, overdue = false)))
        rule.onNodeWithText("Al día").assertIsDisplayed()
    }

    @Test
    fun anIntactChainCarriesItsBadgeAndTheEventCount() {
        show(BackupUiState(chain = ChainStatus.Intact(120)))
        rule.onNodeWithText("Íntegro").assertIsDisplayed()
        rule.onNodeWithText("Registro íntegro (120 eventos)").assertIsDisplayed()
    }

    @Test
    fun aBrokenChainCarriesTheReviewBadgeAndCanBeChecked() {
        val actions = mutableListOf<BackupUiAction>()
        show(BackupUiState(chain = ChainStatus.Broken(3, ChainVerification.Reason.HASH_MISMATCH)), actions)
        rule.onNodeWithText("Necesita revisión").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.backup_chain_check)).performClick()
        assertEquals(BackupUiAction.RecheckChain, actions.single())
    }

    @Test
    fun theRestoreDialogConfirmsThroughItsOwnButton() {
        val actions = mutableListOf<BackupUiAction>()
        val summary = BackupSummary(42, 0L, "1.0", chainIntact = true, firstOccurredOn = "2026-10-01", lastOccurredOn = "2026-11-02")
        show(BackupUiState(stage = BackupStage.RESTORE_CONFIRM, summary = summary), actions)
        rule.onNodeWithText(string(R.string.backup_restore_confirm)).performClick()
        assertEquals(BackupUiAction.ConfirmRestore, actions.single())
    }
}
