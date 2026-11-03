package com.al3xiel.my_finances.core.designsystem

import com.al3xiel.my_finances.core.money.Money

/**
 * Qué parte de `total` es `part`, entre 0 y 1, solo para dibujar una barra de avance. Es visual: ninguna cifra sale de aquí
 * (los montos se muestran aparte, en `Long` céntimos). Con un total de cero o menos no divide y dibuja vacío.
 */
fun progressFraction(part: Money, total: Money): Float =
    if (total.amountMinor <= 0) 0f else (part.amountMinor.toFloat() / total.amountMinor).coerceIn(0f, 1f)
