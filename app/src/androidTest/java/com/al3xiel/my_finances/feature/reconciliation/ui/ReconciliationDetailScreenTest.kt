package com.al3xiel.my_finances.feature.reconciliation.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationDetail
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ReconciliationDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun state(status: ReconciliationStatus, needsReview: Boolean = false, canAct: Boolean = true, difference: Long = 240) =
        ReconciliationDetailUiState(
            loaded = true,
            accountName = "BCP",
            canAct = canAct,
            detail = ReconciliationDetail(
                Reconciliation(
                    ReconciliationId("r1"), AccountId("bcp"), pen(81_240), pen(80_000), status, null, null, null,
                    Instant.EPOCH, null, needsReview,
                ),
                computed = pen(81_000),
                difference = pen(difference),
                linked = emptyList(),
                suggestions = emptyList(),
            ),
        )

    private fun show(state: ReconciliationDetailUiState, onResolve: () -> Unit = {}) {
        rule.setContent { MyfinancesTheme { ReconciliationDetailContent(state, {}, {}, {}, onResolve) } }
    }

    @Test fun anOpenReconciliationSaysSoWithABadge() {
        show(state(ReconciliationStatus.OPEN))
        rule.onNodeWithText("Abierta").assertIsDisplayed()
    }

    @Test fun aResolvedOneThatNeedsReviewCarriesTheReviewBadgeAndItsExplanation() {
        show(state(ReconciliationStatus.RESOLVED, needsReview = true, canAct = false))
        rule.onNodeWithText("Necesita revisión").assertIsDisplayed()
        rule.onNodeWithText("Revisar: el cálculo cambió desde que la resolviste. Si el ajuste ya no corresponde, anúlalo.").assertIsDisplayed()
    }

    @Test fun aResolvedOneThatDoesNotNeedReviewShowsNoReviewBadge() {
        show(state(ReconciliationStatus.RESOLVED, canAct = false))
        rule.onNodeWithText("Resuelta").assertIsDisplayed()
        rule.onNodeWithText("Necesita revisión").assertDoesNotExist()
    }

    @Test fun resolveIsOfferedWhileOpen() {
        var resolved = 0
        show(state(ReconciliationStatus.OPEN)) { resolved++ }
        rule.onNodeWithText("Resolver").performClick()
        assertEquals(1, resolved)
    }
}
