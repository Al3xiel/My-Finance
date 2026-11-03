package com.al3xiel.my_finances.domain.model

/** Reglas de negocio que un caso de uso puede rechazar; nunca se lanzan como excepción. */
sealed interface BusinessError {
    data object BlankName : BusinessError
    data object DuplicateName : BusinessError
    data object NotFound : BusinessError
    data object AlreadyArchived : BusinessError
    data object BalanceNotZero : BusinessError
    data object PersonRequired : BusinessError
    data object PersonNotAllowed : BusinessError
    data object InvalidParent : BusinessError
    data object UnsupportedCurrency : BusinessError
    data object NegativeAmount : BusinessError

    /** Fallo técnico inesperado (por ejemplo, de la base): la operación no se hizo y se puede reintentar. */
    data object Unexpected : BusinessError

    data object InvalidAmount : BusinessError
    data object FutureDate : BusinessError
    data object SameAccount : BusinessError
    data object NoParts : BusinessError
    data object PartsMismatch : BusinessError
    data object InvalidPart : BusinessError
    data object ReasonRequired : BusinessError

    /** Las transferencias no se corrigen ni se anulan (D12). */
    data object TransferNotEditable : BusinessError
    data object AlreadyVoided : BusinessError
    data object TypeChangeNotAllowed : BusinessError

    /** Un reembolso o un pago mayor a lo pendiente se rechaza (D1). */
    data object ExceedsPending : BusinessError

    /** Un cúmulo con algo pendiente de reembolsar no se archiva. */
    data object PendingNotZero : BusinessError

    /** Un gasto con reembolsos no se anula hasta anular el reembolso (D20). */
    data object HasReimbursements : BusinessError

    /** Una parte reembolsada no puede quedar por debajo de lo reembolsado. */
    data object BelowReimbursedAmount : BusinessError

    /** Una parte reembolsada no se quita ni cambia de cúmulo. */
    data object ReimbursedPartLocked : BusinessError

    /** El arqueo declara más de lo que le queda al fondo: hay recargas por revisar o gastos anulados. */
    data object DeclaredAboveRemaining : BusinessError

    /** Una recarga con dinero propio no puede pasar del Disponible libre. */
    data object ExceedsAvailable : BusinessError

    /** Las recargas vinculadas a un ingreso suman como máximo su monto (D24). */
    data object ExceedsIncome : BusinessError

    /** Un fondo cerrado no recibe gastos, recargas, días de uso ni ediciones. */
    data object FundClosed : BusinessError

    /** La fecha queda fuera de las fechas del fondo. */
    data object DateOutOfFundRange : BusinessError

    /** Un gasto no puede ser anterior al último arqueo del fondo (D21). */
    data object DateBeforeLastCount : BusinessError

    /** Los días de uso solo existen en el modo estimado. */
    data object UsageDayNotAllowed : BusinessError

    /** El dato ya no se puede cambiar (modo con días de uso o arqueos, inicio con gastos). */
    data object FieldLocked : BusinessError

    /** Un ingreso con recargas a fondos no se anula (D24). */
    data object HasTopUps : BusinessError

    /** Un ingreso con recargas no puede quedar por debajo de lo recargado. */
    data object BelowToppedUpAmount : BusinessError

    /** Anular o bajar un ingreso dejaría el Disponible libre en negativo: su dinero ya está apartado en fondos. */
    data object ReservedByFunds : BusinessError

    /** El gasto no registrado de un arqueo no se anula ni se corrige (D25). */
    data object UntrackedSpendLocked : BusinessError

    /** No hay nada pendiente que rendir. */
    data object NothingPending : BusinessError

    /** Un préstamo con pagos no se anula hasta anular los pagos (D20). */
    data object HasRepayments : BusinessError

    /** El ingreso esperado ya se confirmó o se canceló: solo uno pendiente se edita, se cancela o se confirma. */
    data object NotPending : BusinessError

    /** Anular el ingreso no es posible: el siguiente ingreso esperado que generó ya se confirmó. */
    data object NextExpectedConfirmed : BusinessError

    /** El rango de un ingreso esperado empieza después de terminar. */
    data object InvalidRange : BusinessError

    /** La cuenta ya tiene una conciliación abierta: se resuelve o se cancela antes de registrar otro saldo real. */
    data object ReconciliationAlreadyOpen : BusinessError

    /** La conciliación ya se resolvió o se canceló. */
    data object ReconciliationNotOpen : BusinessError

    /** El ajuste de una conciliación no se corrige; se anula si ya no corresponde (D33). */
    data object AdjustmentLocked : BusinessError

    /** El movimiento no toca la cuenta de la conciliación. */
    data object MovementNotInAccount : BusinessError

    /** Una cuenta con una conciliación abierta no se archiva. */
    data object HasOpenReconciliation : BusinessError

    /** La causa elegida no admite la diferencia restante (con diferencia 0 o distinta de 0). */
    data object CauseNotAllowed : BusinessError

    /** Aviso de saldo negativo (D14): el usuario puede confirmar y repetir la operación. */
    data class WouldGoNegative(val resultingBalanceMinor: Long) : BusinessError

    /** El primer uso exige al menos una cuenta (D59). */
    data object NoAccounts : BusinessError
}
