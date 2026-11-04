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
    BusinessError.AccountRequired -> stringResource(R.string.error_account_required)
    BusinessError.DestinationAccountRequired -> stringResource(R.string.error_destination_account_required)
    BusinessError.CategoryRequired -> stringResource(R.string.error_category_required)
    BusinessError.IncomeSourceRequired -> stringResource(R.string.error_income_source_required)
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
    BusinessError.ReconciliationAlreadyOpen -> stringResource(R.string.error_reconciliation_already_open)
    BusinessError.ReconciliationNotOpen -> stringResource(R.string.error_reconciliation_not_open)
    BusinessError.AdjustmentLocked -> stringResource(R.string.error_adjustment_locked)
    BusinessError.MovementNotInAccount -> stringResource(R.string.error_movement_not_in_account)
    BusinessError.HasOpenReconciliation -> stringResource(R.string.error_has_open_reconciliation)
    BusinessError.CauseNotAllowed -> stringResource(R.string.error_cause_not_allowed)
    BusinessError.TransferNotEditable -> stringResource(R.string.error_transfer_not_editable)
    BusinessError.AlreadyVoided -> stringResource(R.string.error_already_voided)
    BusinessError.TypeChangeNotAllowed -> stringResource(R.string.error_type_change_not_allowed)
    BusinessError.ExceedsPending -> stringResource(R.string.error_exceeds_pending)
    BusinessError.PendingNotZero -> stringResource(R.string.error_pending_not_zero)
    BusinessError.HasReimbursements -> stringResource(R.string.error_has_reimbursements)
    BusinessError.BelowReimbursedAmount -> stringResource(R.string.error_below_reimbursed_amount)
    BusinessError.ReimbursedPartLocked -> stringResource(R.string.error_reimbursed_part_locked)
    BusinessError.HasRepayments -> stringResource(R.string.error_has_repayments)
    BusinessError.NothingPending -> stringResource(R.string.error_nothing_pending)
    BusinessError.NextExpectedConfirmed -> stringResource(R.string.error_next_expected_confirmed)
    BusinessError.NotPending -> stringResource(R.string.error_not_pending)
    BusinessError.InvalidRange -> stringResource(R.string.error_invalid_range)
    BusinessError.DeclaredAboveRemaining -> stringResource(R.string.error_declared_above_remaining)
    BusinessError.ExceedsAvailable -> stringResource(R.string.error_exceeds_available)
    BusinessError.ExceedsIncome -> stringResource(R.string.error_exceeds_income)
    BusinessError.FundClosed -> stringResource(R.string.error_fund_closed)
    BusinessError.NoAccounts -> stringResource(R.string.error_no_accounts)
    BusinessError.DateOutOfFundRange -> stringResource(R.string.error_date_out_of_fund_range)
    BusinessError.DateBeforeLastCount -> stringResource(R.string.error_date_before_last_count)
    BusinessError.UsageDayNotAllowed -> stringResource(R.string.error_usage_day_not_allowed)
    BusinessError.FieldLocked -> stringResource(R.string.error_field_locked)
    BusinessError.HasTopUps -> stringResource(R.string.error_has_top_ups)
    BusinessError.BelowToppedUpAmount -> stringResource(R.string.error_below_topped_up_amount)
    BusinessError.ReservedByFunds -> stringResource(R.string.error_reserved_by_funds)
    BusinessError.UntrackedSpendLocked -> stringResource(R.string.error_untracked_spend_locked)
    is BusinessError.WouldGoNegative -> stringResource(
        R.string.error_would_go_negative,
        moneyDisplayText(Money(resultingBalanceMinor, CurrencyCode.PEN), hidden = LocalHideAmounts.current),
    )
}
