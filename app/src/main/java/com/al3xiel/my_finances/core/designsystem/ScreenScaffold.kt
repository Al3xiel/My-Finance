package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** Etiqueta de prueba del encabezado, para medir que se contrae. */
const val SCREEN_SCAFFOLD_BAR_TAG = "screen_scaffold_bar"

/**
 * Marco de pantalla al estilo One UI: encabezado grande que se contrae al desplazar, botón volver por `LocalNavigator`
 * y acciones. Con `showBack = false` es la versión de las pestañas. Es opt-in: una pantalla que no lo usa sigue igual (D71).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigator = LocalNavigator.current
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                modifier = Modifier.testTag(SCREEN_SCAFFOLD_BAR_TAG),
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = navigator::back) {
                            Icon(painterResource(AppIcons.Back), contentDescription = stringResource(R.string.scaffold_back))
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        content = content,
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ScreenScaffoldPreview() {
    MyfinancesTheme { ScreenScaffold(title = "Cuentas") { padding -> Text("Contenido", Modifier.padding(padding)) } }
}
