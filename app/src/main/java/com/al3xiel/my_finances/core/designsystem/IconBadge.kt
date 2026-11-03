package com.al3xiel.my_finances.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** Icono dentro de un contenedor redondeado. Decorativo salvo que se dé `contentDescription`. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier.size(size).clip(MaterialTheme.shapes.medium).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = content, modifier = Modifier.size(size / 2))
    }
}

@Preview(showBackground = true)
@Composable
private fun IconBadgePreview() {
    MyfinancesTheme { IconBadge(AppIcons.Add) }
}
