package com.al3xiel.my_finances.feature.accounts.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.rules.AccountDraftRules
import javax.inject.Inject

data class InitialAccountDraft(val name: String, val type: AccountType, val openingBalance: Money)

/** Crea las cuentas del primer uso: o se registran todas en una sola llamada al registrador, o ninguna (D59). */
class CreateInitialAccountsUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(drafts: List<InitialAccountDraft>): Outcome<List<AccountId>> {
        if (drafts.isEmpty()) return Outcome.Failed(BusinessError.NoAccounts)

        // Cada nombre ya aceptado ocupa el lugar para los siguientes: así se detectan los repetidos dentro de la lista.
        val taken = accounts.activeNames().toMutableList()
        val events = ArrayList<AccountCreated>(drafts.size)
        for (draft in drafts) {
            val clean = when (val checked = AccountDraftRules.validate(draft.name, draft.openingBalance, taken)) {
                is Outcome.Failed -> return checked
                is Outcome.Ok -> checked.value
            }
            taken += clean
            events += AccountCreated(
                AccountId(ids.newId()), clean, draft.type, draft.openingBalance.currency, null, draft.openingBalance,
            )
        }
        val today = recorder.today()
        recorder.record(events.map { NewEvent(it, today) })
        return Outcome.Ok(events.map { it.accountId })
    }
}
