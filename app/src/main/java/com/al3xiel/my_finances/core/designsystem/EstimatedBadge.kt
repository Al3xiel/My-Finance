package com.al3xiel.my_finances.core.designsystem

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** Etiqueta "Estimado": todo número de origen la lleva. Es el `StatusBadge` ESTIMATED, con icono y texto. */
@Composable
fun EstimatedBadge(modifier: Modifier = Modifier) = StatusBadge(BadgeStatus.ESTIMATED, modifier)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EstimatedBadgePreview() {
    MyfinancesTheme { EstimatedBadge() }
}
