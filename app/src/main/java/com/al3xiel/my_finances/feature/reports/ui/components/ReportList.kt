package com.al3xiel.my_finances.feature.reports.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.core.designsystem.EmptyState

/** Esqueleto común de las pantallas de reporte (S41–S44): estado vacío con su texto o la lista con el relleno de siempre. */
@Composable
fun ReportList(
    isEmpty: Boolean,
    emptyTitle: String,
    emptyBody: String,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: LazyListScope.() -> Unit,
) {
    if (isEmpty) {
        EmptyState(title = emptyTitle, body = emptyBody, modifier = modifier)
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) { content() }
}
