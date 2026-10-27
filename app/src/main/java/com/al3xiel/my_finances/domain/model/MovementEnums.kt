package com.al3xiel.my_finances.domain.model

/** Tipos de movimiento proyectados; los demás de `docs/04 §4.3` llegan con sus features. */
enum class MovementType { INCOME, EXPENSE, TRANSFER }

enum class MovementStatus { ACTIVE, VOIDED }

/** `FEE` es la parte sintética de la comisión de una transferencia (D11). */
enum class PartKind { NORMAL, FEE }

enum class LedgerEntryKind { OPENING, INCOME, EXPENSE, FEE, TRANSFER_OUT, TRANSFER_IN }
