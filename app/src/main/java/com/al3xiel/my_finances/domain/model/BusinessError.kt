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

    /** Aviso de saldo negativo (D14): el usuario puede confirmar y repetir la operación. */
    data class WouldGoNegative(val resultingBalanceMinor: Long) : BusinessError
}
