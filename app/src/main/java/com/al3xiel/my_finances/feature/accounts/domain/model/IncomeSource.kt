package com.al3xiel.my_finances.feature.accounts.domain.model

import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.PersonId

data class IncomeSource(
    val id: IncomeSourceId,
    val name: String,
    val kind: IncomeSourceKind,
    val personId: PersonId?,
)
