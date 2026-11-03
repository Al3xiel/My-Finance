package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money

@Composable
fun AccountCard(
    name: String,
    balance: Money,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    typeLabel: String? = null,
    accent: Color? = null,
    isArchived: Boolean = false,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).alpha(if (isArchived) 0.6f else 1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (accent != null) Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                val subtitle = listOfNotNull(typeLabel, stringResource(R.string.account_card_archived).takeIf { isArchived })
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MoneyText(balance, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountCardPreview() {
    androidx.compose.runtime.CompositionLocalProvider(LocalHideAmounts provides false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountCard("BBVA", Money(1_095_000, CurrencyCode.PEN), onClick = {}, typeLabel = "Banco", accent = Color(0xFF1565C0))
            AccountCard("Antigua", Money(0, CurrencyCode.PEN), onClick = {}, isArchived = true)
        }
    }
}
