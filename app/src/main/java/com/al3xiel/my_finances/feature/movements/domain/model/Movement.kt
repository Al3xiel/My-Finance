package com.al3xiel.my_finances.feature.movements.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.TagId
import java.time.LocalDate

data class Movement(
    val id: MovementId,
    val type: MovementType,
    val accountId: AccountId,
    val counterAccountId: AccountId?,
    val amount: Money,
    val incomeSourceId: IncomeSourceId?,
    val note: String?,
    val occurredOn: LocalDate,
    val status: MovementStatus,
    val revision: Int,
    val parts: List<MovementPart>,
)

data class MovementPart(
    val id: PartId,
    val amount: Money,
    val categoryId: CategoryId,
    val kind: PartKind,
    val tagIds: List<TagId>,
)
