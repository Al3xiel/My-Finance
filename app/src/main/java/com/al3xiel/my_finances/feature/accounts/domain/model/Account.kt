package com.al3xiel.my_finances.feature.accounts.domain.model

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountType

data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val currency: CurrencyCode,
    val color: String?,
    val isArchived: Boolean,
)
