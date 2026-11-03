package com.al3xiel.my_finances.core.designsystem

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.ui.theme.LocalFinanceColors
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

enum class BadgeStatus { PENDING, OVERDUE, NEEDS_REVIEW, RECONCILED, ESTIMATED }

@DrawableRes
internal fun BadgeStatus.icon(): Int = when (this) {
    BadgeStatus.PENDING -> AppIcons.StatusPending
    BadgeStatus.OVERDUE -> AppIcons.StatusOverdue
    BadgeStatus.NEEDS_REVIEW -> AppIcons.StatusReview
    BadgeStatus.RECONCILED -> AppIcons.StatusReconciled
    BadgeStatus.ESTIMATED -> AppIcons.StatusEstimated
}

@StringRes
private fun BadgeStatus.label(): Int = when (this) {
    BadgeStatus.PENDING -> R.string.status_pending
    BadgeStatus.OVERDUE -> R.string.status_overdue
    BadgeStatus.NEEDS_REVIEW -> R.string.status_needs_review
    BadgeStatus.RECONCILED -> R.string.status_reconciled
    BadgeStatus.ESTIMATED -> R.string.origin_estimated_label
}

/**
 * Estado de una cosa: icono + texto + color. El color solo refuerza (D73). `label` reemplaza el texto cuando el estado
 * de la pantalla tiene nombre propio (Saldado, Rendido…) pero comparte icono y color con uno de los cinco.
 */
@Composable
fun StatusBadge(status: BadgeStatus, modifier: Modifier = Modifier, label: String? = null) {
    val f = LocalFinanceColors.current
    val (container, content) = when (status) {
        BadgeStatus.PENDING -> f.neutralContainer to f.onNeutralContainer
        BadgeStatus.OVERDUE -> f.expenseContainer to f.onExpenseContainer
        BadgeStatus.NEEDS_REVIEW -> f.warningContainer to f.onWarningContainer
        BadgeStatus.RECONCILED -> f.incomeContainer to f.onIncomeContainer
        BadgeStatus.ESTIMATED -> f.estimatedContainer to f.onEstimatedContainer
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = container) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(status.icon()), contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label ?: stringResource(status.label()), style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusBadgePreview() {
    MyfinancesTheme { Column { BadgeStatus.entries.forEach { StatusBadge(it) } } }
}
