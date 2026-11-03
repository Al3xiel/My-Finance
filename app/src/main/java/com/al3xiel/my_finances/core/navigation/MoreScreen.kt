package com.al3xiel.my_finances.core.navigation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S50: the entries are contributed by the features; the shell only lists them, grouped in one card. */
@Composable
fun MoreContent(entries: List<MoreMenuEntry>, onOpen: (Route) -> Unit, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_state_title),
            body = stringResource(R.string.empty_state_body),
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp).semantics { heading() },
        )
        SectionCard {
            entries.forEachIndexed { index, entry ->
                if (index > 0) SectionDivider()
                SectionRow(headline = stringResource(entry.labelRes), onClick = { onOpen(entry.route) })
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MoreContentPreview() {
    MyfinancesTheme {
        MoreContent(
            entries = listOf(
                MoreMenuEntry("privacy", R.string.more_entry_privacy, Privacy, order = 1),
                MoreMenuEntry("settings", R.string.more_entry_settings, Settings, order = 2),
            ),
            onOpen = {},
        )
    }
}
