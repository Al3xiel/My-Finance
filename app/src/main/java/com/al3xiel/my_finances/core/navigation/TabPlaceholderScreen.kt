package com.al3xiel.my_finances.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
