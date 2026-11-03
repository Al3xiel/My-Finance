package com.al3xiel.my_finances.feature.people.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.usecase.ArchiveGuard
import com.al3xiel.my_finances.feature.people.domain.repository.PersonDetailRepository
import javax.inject.Inject

/** Una persona con saldo pendiente (cúmulos activos o deudas abiertas) no se archiva: dejaría de verse en Por cobrar. */
class PersonBalanceGuard @Inject constructor(
    private val people: PersonDetailRepository,
) : ArchiveGuard {
    override suspend fun check(personId: PersonId): BusinessError? =
        if (people.balanceMinor(personId) > 0) BusinessError.BalanceNotZero else null
}
