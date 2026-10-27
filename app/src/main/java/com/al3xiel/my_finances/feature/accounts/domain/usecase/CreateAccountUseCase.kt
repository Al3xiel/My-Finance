package com.al3xiel.my_finances.feature.accounts.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

class CreateAccountUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(name: String, type: AccountType, color: String?, openingBalance: Money): Outcome<AccountId> {
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(accounts.activeNames(), clean)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }
        if (openingBalance.currency != CurrencyCode.PEN) return Outcome.Failed(BusinessError.UnsupportedCurrency)
        if (openingBalance.amountMinor < 0) return Outcome.Failed(BusinessError.NegativeAmount)

        val id = AccountId(ids.newId())
        recorder.record(AccountCreated(id, clean, type, openingBalance.currency, color, openingBalance))
        return Outcome.Ok(id)
    }
}
