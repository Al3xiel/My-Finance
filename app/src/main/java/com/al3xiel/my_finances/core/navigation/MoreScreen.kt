package com.al3xiel.my_finances.core.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState

/** S50: the entries are contributed by the features; the shell only lists them. */
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
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        entries.forEach { entry ->
            Text(
                text = stringResource(entry.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onOpen(entry.route) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}
