package com.al3xiel.my_finances.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/** `true` = los montos se enmascaran. Por defecto oculto: falla del lado seguro si nadie lo provee. */
val LocalHideAmounts = staticCompositionLocalOf { true }
