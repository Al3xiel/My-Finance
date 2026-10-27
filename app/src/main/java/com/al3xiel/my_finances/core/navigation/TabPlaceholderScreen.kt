package com.al3xiel.my_finances.core.navigation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState

/** Stand-in for a route whose feature has not contributed a screen yet. */
@Composable
fun TabPlaceholderScreen() {
    EmptyState(
        title = stringResource(R.string.empty_state_title),
        body = stringResource(R.string.empty_state_body),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TabPlaceholderScreenPreview() {
    TabPlaceholderScreen()
}
