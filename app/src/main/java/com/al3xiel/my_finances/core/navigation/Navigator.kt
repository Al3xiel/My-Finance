package com.al3xiel.my_finances.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** Lo que una pantalla puede pedirle a la navegación sin conocer el scaffold. */
interface Navigator {
    fun push(route: Route)

    fun back()
}

private object NoOpNavigator : Navigator {
    override fun push(route: Route) = Unit

    override fun back() = Unit
}

/** El scaffold lo provee alrededor de las pantallas; en previews y tests no hace nada. */
val LocalNavigator = staticCompositionLocalOf<Navigator> { NoOpNavigator }
