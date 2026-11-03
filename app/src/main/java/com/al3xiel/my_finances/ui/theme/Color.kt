package com.al3xiel.my_finances.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Marca (≈ #00A5A8). Con blanco da 3:1, así que solo sirve para acentos grandes y gráficos;
 * el texto y los botones usan los tonos de los esquemas, que sí llegan a AA (D67).
 */
val BrandTurquoise = Color(0xFF00A5A8)

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF0F2),
    onPrimaryContainer = Color(0xFF002020),
    inversePrimary = Color(0xFF4DD9DC),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAFA),
    onBackground = Color(0xFF171D1D),
    surface = Color(0xFFF5FAFA),
    onSurface = Color(0xFF171D1D),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    inverseSurface = Color(0xFF2B3232),
    inverseOnSurface = Color(0xFFECF2F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F4),
    surfaceContainer = Color(0xFFE9EFEE),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDEE4E3),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF4DD9DC),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFF9CF0F2),
    inversePrimary = Color(0xFF00696B),
    secondary = Color(0xFFB1CCCC),
    onSecondary = Color(0xFF1C3434),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF899392),
    outlineVariant = Color(0xFF3F4948),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2B3232),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3636),
)

/** Oscuro puro: el mismo esquema oscuro con fondo y superficie `#000000` (D68). */
val AmoledColors: ColorScheme = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0F0F),
    surfaceContainer = Color(0xFF111717),
    surfaceContainerHigh = Color(0xFF1A2020),
    surfaceContainerHighest = Color(0xFF242A2A),
)
