package com.al3xiel.my_finances.domain.model

import kotlinx.serialization.Serializable

@Serializable @JvmInline value class AccountId(val value: String)
@Serializable @JvmInline value class CategoryId(val value: String)
@Serializable @JvmInline value class TagId(val value: String)
@Serializable @JvmInline value class PersonId(val value: String)
@Serializable @JvmInline value class IncomeSourceId(val value: String)
@Serializable @JvmInline value class MovementId(val value: String)
@Serializable @JvmInline value class PartId(val value: String)
@Serializable @JvmInline value class ReconciliationId(val value: String)
@Serializable @JvmInline value class PoolId(val value: String)
@Serializable @JvmInline value class FundId(val value: String)
@Serializable @JvmInline value class PoolPeriodId(val value: String)
@Serializable @JvmInline value class StatementId(val value: String)
@Serializable @JvmInline value class ReimbursementId(val value: String)
@Serializable @JvmInline value class LoanId(val value: String)
@Serializable @JvmInline value class RepaymentId(val value: String)
