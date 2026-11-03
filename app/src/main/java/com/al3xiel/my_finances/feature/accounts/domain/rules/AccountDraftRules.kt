package com.al3xiel.my_finances.feature.accounts.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome

/** Reglas de una cuenta por crear, compartidas por la creación individual y la del primer uso. */
object AccountDraftRules {
    /** Devuelve el nombre limpio, o el primer error. `takenNames` son los nombres que ya ocupan las cuentas activas. */
    fun validate(name: String, openingBalance: Money, takenNames: List<String>): Outcome<String> {
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(takenNames, clean)) return Outcome.Failed(BusinessError.DuplicateName)
        if (openingBalance.currency != CurrencyCode.PEN) return Outcome.Failed(BusinessError.UnsupportedCurrency)
        if (openingBalance.amountMinor < 0) return Outcome.Failed(BusinessError.NegativeAmount)
        return Outcome.Ok(clean)
    }
}
