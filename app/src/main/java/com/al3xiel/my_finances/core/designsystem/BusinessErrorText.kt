package com.al3xiel.my_finances.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.domain.model.BusinessError

/** Texto para el usuario de una regla de negocio rechazada. */
@Composable
fun BusinessError.message(): String = when (this) {
    BusinessError.BlankName -> stringResource(R.string.error_blank_name)
    BusinessError.DuplicateName -> stringResource(R.string.error_duplicate_name)
    BusinessError.NotFound -> stringResource(R.string.error_not_found)
    BusinessError.AlreadyArchived -> stringResource(R.string.error_already_archived)
    BusinessError.BalanceNotZero -> stringResource(R.string.error_balance_not_zero)
    BusinessError.PersonRequired -> stringResource(R.string.error_person_required)
    BusinessError.PersonNotAllowed -> stringResource(R.string.error_person_not_allowed)
    BusinessError.InvalidParent -> stringResource(R.string.error_invalid_parent)
    BusinessError.UnsupportedCurrency -> stringResource(R.string.error_unsupported_currency)
    BusinessError.NegativeAmount -> stringResource(R.string.error_negative_amount)
    BusinessError.Unexpected -> stringResource(R.string.error_unexpected)
    BusinessError.InvalidAmount -> stringResource(R.string.error_invalid_amount)
    BusinessError.FutureDate -> stringResource(R.string.error_future_date)
    BusinessError.SameAccount -> stringResource(R.string.error_same_account)
    BusinessError.NoParts -> stringResource(R.string.error_no_parts)
    BusinessError.PartsMismatch -> stringResource(R.string.error_parts_mismatch)
    BusinessError.InvalidPart -> stringResource(R.string.error_invalid_part)
    BusinessError.ReasonRequired -> stringResource(R.string.error_reason_required)
    BusinessError.TransferNotEditable -> stringResource(R.string.error_transfer_not_editable)
    BusinessError.AlreadyVoided -> stringResource(R.string.error_already_voided)
    BusinessError.TypeChangeNotAllowed -> stringResource(R.string.error_type_change_not_allowed)
    is BusinessError.WouldGoNegative -> stringResource(
        R.string.error_would_go_negative,
        moneyDisplayText(Money(resultingBalanceMinor, CurrencyCode.PEN), hidden = LocalHideAmounts.current),
    )
}
