package com.al3xiel.my_finances.core.designsystem

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val peru = Locale.forLanguageTag("es-PE")

/** Fecha del hecho para mostrar (`26 oct 2026`); solo presentación, nunca se guarda así. */
fun dayText(day: LocalDate): String = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(peru))

/** Encabezado de un grupo de la lista (`lunes, 26 de octubre de 2026`). */
fun dayHeaderText(day: LocalDate): String = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(peru))
