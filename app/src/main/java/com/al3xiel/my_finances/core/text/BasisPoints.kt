package com.al3xiel.my_finances.core.text

import java.text.DecimalFormatSymbols

/** "33,34 %" con el separador decimal del idioma; solo aritmética entera (10 000 puntos base = 100%). */
fun formatBasisPoints(basisPoints: Int, separator: Char = DecimalFormatSymbols.getInstance().decimalSeparator): String {
    val whole = basisPoints / 100
    val fraction = (basisPoints % 100).toString().padStart(2, '0')
    return "$whole$separator$fraction %"
}
