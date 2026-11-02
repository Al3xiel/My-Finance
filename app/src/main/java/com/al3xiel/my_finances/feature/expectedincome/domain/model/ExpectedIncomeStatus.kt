package com.al3xiel.my_finances.feature.expectedincome.domain.model

/**
 * La tabla guarda solo `SCHEDULED`, `CONFIRMED` y `CANCELLED`; `OVERDUE` se calcula al consultar (D27) y nunca se
 * proyecta.
 */
enum class ExpectedIncomeStatus { SCHEDULED, OVERDUE, CONFIRMED, CANCELLED }
