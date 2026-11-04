package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** El motivo por el que un botón está bloqueado, escrito encima de él; sin motivo no muestra nada. */
@Composable
fun BlockedHint(reason: String?, modifier: Modifier = Modifier) {
    if (reason == null) return
    Text(reason, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
