package com.al3xiel.my_finances.feature.accounts.domain.model

import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementId
import java.time.LocalDate

data class AccountWithBalance(val account: Account, val balanceMinor: Long)

/** Un asiento del libro de una cuenta; los anulados se muestran tachados, no se ocultan. */
data class LedgerLine(
    val id: String,
    val sequence: Long,
    val movementId: MovementId?,
    val deltaMinor: Long,
    val kind: LedgerEntryKind,
    val occurredOn: LocalDate,
    val isVoided: Boolean,
)
