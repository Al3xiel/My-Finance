package com.al3xiel.my_finances.domain.model

/** Cuánto de un gasto con cúmulo ya se reembolsó. Es derivado de las asignaciones: no es un evento. */
enum class ReimbursementStatus { PENDING, PARTIALLY_REIMBURSED, REIMBURSED }
