package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview

/**
 * Cálculos del editor de gasto, sin estado: con una sola parte el total es el campo de monto; con dos o más el total es la
 * suma de sus partes y no se teclea.
 */
internal fun partsTotalMinor(parts: List<PartDraft>): Long = parts.sumOf { MoneyInputFilter.toMinor(it.amountText) ?: 0L }

/** El total que se guarda y se muestra: el campo con una parte, la suma con varias. */
internal fun effectiveTotalMinor(parts: List<PartDraft>, totalText: String): Long =
    if (parts.size >= 2) partsTotalMinor(parts) else MoneyInputFilter.toMinor(totalText) ?: 0L

/** Lo que vale una parte: con una sola parte lleva el total entero. */
internal fun partAmountMinor(parts: List<PartDraft>, totalText: String, part: PartDraft): Long =
    MoneyInputFilter.toMinor(if (parts.size == 1) totalText else part.amountText) ?: 0L

/** Fondos a los que el gasto les suma más de lo que les queda; `originalMinor` (partes ya guardadas) ya está en su gastado. */
internal fun overspentFundNames(
    parts: List<PartDraft>,
    totalText: String,
    originalMinor: Map<com.al3xiel.my_finances.domain.model.PartId, Long>,
    fundList: List<FundOverview>,
): List<String> = fundList.filter { overview ->
    val own = parts.filter { it.fundId == overview.fund.id }
    val extra = own.sumOf { partAmountMinor(parts, totalText, it) } - own.sumOf { originalMinor[it.partId] ?: 0L }
    own.isNotEmpty() && extra > overview.estimatedRemaining.amountMinor.coerceAtLeast(0)
}.map { it.fund.name }
