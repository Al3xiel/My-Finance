package com.al3xiel.my_finances.feature.pools.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolDetail
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PoolDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private val id = PoolId("dogs")

    private fun period(n: Int, status: PoolPeriodStatus, closed: LocalDate?) =
        PoolPeriod(PoolPeriodId("dogs-p$n"), id, LocalDate.of(2026, 10, 1), closed, status, null, pen(20_000), pen(15_000))

    private val state = PoolDetailUiState(
        loaded = true,
        detail = PoolDetail(
            Pool(id, "Perros", PersonId("mom"), PoolKind.PERMANENT, null, emptyList(), false),
            "Mamá",
            listOf(
                period(3, PoolPeriodStatus.OPEN, null),
                period(2, PoolPeriodStatus.STATEMENT_SENT, LocalDate.of(2026, 10, 31)),
                period(1, PoolPeriodStatus.SETTLED, LocalDate.of(2026, 9, 30)),
            ),
            pen(11_000),
        ),
    )

    private fun show(onSettle: (PoolId) -> Unit = {}) {
        rule.setContent { MyfinancesTheme { PoolDetailContent(state, {}, {}, onSettle, { _, _ -> }, {}) } }
    }

    @Test fun eachPeriodStateIsAStatusBadgeWithText() {
        show()
        rule.onNodeWithText("Abierto").assertExists()
        rule.onNodeWithText("Rendido").assertExists()
        rule.onNodeWithText("Saldado").assertExists()
    }

    @Test fun showsTheCurrentPeriodAndTheHistoryTitle() {
        show()
        rule.onNodeWithText("Periodo actual").assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithText("Periodo actual").fetchSemanticsNodes().size)
    }

    @Test fun settleOpensTheStatementOfThisPool() {
        var settled: PoolId? = null
        show { settled = it }
        rule.onNodeWithText("Rendir cuentas").performClick()
        assertEquals(id, settled)
    }
}
