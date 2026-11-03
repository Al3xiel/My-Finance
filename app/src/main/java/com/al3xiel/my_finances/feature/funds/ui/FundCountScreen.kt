package com.al3xiel.my_finances.feature.funds.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EditorFooter
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.message
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.model.Fund
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import java.time.LocalDate

/** S53: arqueo de un fondo. */
@Composable
fun FundCountScreen(
    fundId: FundId,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FundCountViewModel = hiltViewModel<FundCountViewModel, FundCountViewModel.Factory>(
        key = fundId.value,
        creationCallback = { it.create(fundId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    FundCountContent(state, viewModel::onAction, modifier)
}

@Composable
fun FundCountContent(state: FundCountUiState, onAction: (FundCountUiAction) -> Unit, modifier: Modifier = Modifier) {
    val overview = state.overview
    if (overview == null) {
        if (state.notFound) EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.fund_count_title, overview.fund.name), style = MaterialTheme.typography.titleLarge)
        if (state.suggestBeforeClose) {
            Text(stringResource(R.string.fund_count_suggest_before_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyInput(
            value = state.declaredText,
            onValueChange = { onAction(FundCountUiAction.EditDeclared(it)) },
            label = stringResource(R.string.fund_count_declared),
            isError = state.rejection != null,
            supportingText = state.rejection?.message(),
            modifier = Modifier.fillMaxWidth(),
        )
        state.realSpent?.let { Figure(stringResource(R.string.fund_count_real_spent), it) }
        state.estimatedSpent?.let { Figure(stringResource(R.string.fund_count_estimated_spent), it) }
        state.untracked?.takeIf { it.isPositive }?.let {
            Figure(stringResource(R.string.fund_count_untracked), it)
            Text(stringResource(R.string.fund_count_untracked_hint), style = MaterialTheme.typography.bodySmall)
            EntityPicker(
                label = stringResource(R.string.fund_count_account),
                options = state.accountOptions,
                selectedId = state.accountId?.value,
                onSelect = { onAction(FundCountUiAction.SelectAccount(AccountId(it))) },
                modifier = Modifier.fillMaxWidth(),
            )
            EntityPicker(
                label = stringResource(R.string.fund_count_category),
                options = state.categoryOptions,
                selectedId = state.categoryId?.value,
                onSelect = { onAction(FundCountUiAction.SelectCategory(CategoryId(it))) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        EditorFooter(error = state.error, onSave = { onAction(FundCountUiAction.Confirm) })
    }
}

@Composable
private fun Figure(label: String, money: Money) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        MoneyText(money, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = FundCountUiState(
    loaded = true,
    overview = FundOverview(
        Fund(
            FundId("fare"), "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 25),
            pen(5_000), pen(0), FundStatus.ACTIVE, null, null, null,
        ),
        pen(500), 2, null, pen(4_500), pen(1_000), pen(4_500),
    ),
    declaredText = "38",
    accountId = AccountId("bbva"),
    categoryId = CategoryId("transport"),
    accountOptions = listOf(EntityOption("bbva", "BBVA")),
    categoryOptions = listOf(EntityOption("transport", "Transporte")),
    realSpent = pen(1_200),
    estimatedSpent = pen(1_000),
    untracked = pen(700),
    suggestBeforeClose = true,
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FundCountContentPreview() {
    CompositionLocalProvider(LocalHideAmounts provides false) { FundCountContent(previewState, {}) }
}

@Preview(showBackground = true)
@Composable
private fun FundCountRejectedPreview() {
    FundCountContent(previewState.copy(declaredText = "46", untracked = null, realSpent = null, rejection = BusinessError.DeclaredAboveRemaining), {})
}
