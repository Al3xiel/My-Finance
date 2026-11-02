package com.al3xiel.my_finances.domain.model

/** Tipos de movimiento proyectados; los demás de `docs/04 §4.3` llegan con sus features. */
enum class MovementType { INCOME, EXPENSE, TRANSFER, REIMBURSEMENT, LOAN_DISBURSEMENT, LOAN_REPAYMENT, FUND_UNTRACKED_SPEND, ADJUSTMENT }

enum class MovementStatus { ACTIVE, VOIDED }

/** `FEE` es la parte sintética de la comisión de una transferencia (D11). */
enum class PartKind { NORMAL, FEE, UNTRACKED }

enum class LedgerEntryKind { OPENING, INCOME, EXPENSE, FEE, TRANSFER_OUT, TRANSFER_IN, REIMBURSEMENT_IN, LOAN_OUT, LOAN_IN, ADJUSTMENT }
