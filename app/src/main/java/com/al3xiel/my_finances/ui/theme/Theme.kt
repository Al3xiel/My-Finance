package com.al3xiel.my_finances.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode

/** Qué esquema de color se aplica; separado de Compose para poder probar la elección (D67, D68). */
enum class SchemeKind { LIGHT, DARK, AMOLED, DYNAMIC_LIGHT, DYNAMIC_DARK, DYNAMIC_AMOLED }

private const val DYNAMIC_COLOR_MIN_SDK = Build.VERSION_CODES.S

/** El color del sistema solo se usa si se pidió y el teléfono es Android 12 o superior. */
fun schemeKindFor(mode: ThemeMode, systemDark: Boolean, dynamicColor: Boolean, sdkInt: Int): SchemeKind {
    val useDynamic = dynamicColor && sdkInt >= DYNAMIC_COLOR_MIN_SDK
    return when (mode) {
        ThemeMode.LIGHT -> if (useDynamic) SchemeKind.DYNAMIC_LIGHT else SchemeKind.LIGHT
        ThemeMode.DARK -> if (useDynamic) SchemeKind.DYNAMIC_DARK else SchemeKind.DARK
        ThemeMode.AMOLED -> if (useDynamic) SchemeKind.DYNAMIC_AMOLED else SchemeKind.AMOLED
        ThemeMode.SYSTEM -> schemeKindFor(if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT, systemDark, dynamicColor, sdkInt)
    }
}

/** Los esquemas propios; los dinámicos devuelven `null` porque salen del teléfono. */
fun staticSchemeFor(kind: SchemeKind): ColorScheme? = when (kind) {
    SchemeKind.LIGHT -> LightColors
    SchemeKind.DARK -> DarkColors
    SchemeKind.AMOLED -> AmoledColors
    SchemeKind.DYNAMIC_LIGHT, SchemeKind.DYNAMIC_DARK, SchemeKind.DYNAMIC_AMOLED -> null
}

fun financeColorsFor(kind: SchemeKind): FinanceColors = when (kind) {
    SchemeKind.LIGHT, SchemeKind.DYNAMIC_LIGHT -> FinanceColors.Light
    else -> FinanceColors.Dark
}

@Composable
fun MyfinancesTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val kind = schemeKindFor(themeMode, isSystemInDarkTheme(), dynamicColor, Build.VERSION.SDK_INT)
    val colorScheme = staticSchemeFor(kind) ?: dynamicScheme(kind)
    CompositionLocalProvider(
        LocalFinanceColors provides financeColorsFor(kind),
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Para previews y pantallas sin ajustes: claro u oscuro según el teléfono, con la paleta turquesa. */
@Composable
fun MyfinancesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MyfinancesTheme(themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false, content = content)
}

@Composable
private fun dynamicScheme(kind: SchemeKind): ColorScheme {
    val context = LocalContext.current
    return when (kind) {
        SchemeKind.DYNAMIC_LIGHT -> dynamicLightColorScheme(context)
        SchemeKind.DYNAMIC_DARK -> dynamicDarkColorScheme(context)
        else -> dynamicDarkColorScheme(context).copy(background = Color.Black, surface = Color.Black)
    }
}
