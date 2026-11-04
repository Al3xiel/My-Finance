package com.al3xiel.my_finances.feature.reports.ui.components

import androidx.compose.foundation.layout.padding
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
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

/**
 * Esqueleto común de las pantallas de reporte (S41–S44): encabezado con su título, y debajo el estado vacío con su texto o la
 * lista con el relleno de siempre.
 */
@Composable
fun ReportList(
    title: String,
    isEmpty: Boolean,
    emptyTitle: String,
    emptyBody: String,
    modifier: Modifier = Modifier,
    spacing: Dp = LocalSpacing.current.lg,
    content: LazyListScope.() -> Unit,
) {
    ScreenScaffold(title = title, modifier = modifier) { padding ->
        if (isEmpty) {
            EmptyState(title = emptyTitle, body = emptyBody, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(LocalSpacing.current.lg),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) { content() }
        }
    }
}
