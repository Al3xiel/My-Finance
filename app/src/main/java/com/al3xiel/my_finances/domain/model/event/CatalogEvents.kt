package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import kotlinx.serialization.Serializable

enum class AccountType { BANK, CASH, DIGITAL_WALLET }

enum class TagKind { PERSON, TOPIC }

@Serializable
data class AccountCreated(
    val accountId: AccountId,
    val name: String,
    val accountType: AccountType,
    val currency: CurrencyCode,
    val color: String?,
    val openingBalance: Money,
) : DomainEvent {
    override val type get() = "AccountCreated"
    override val aggregateType get() = AggregateType.ACCOUNT
    override val aggregateId get() = accountId.value
}

@Serializable
data class AccountRenamed(val accountId: AccountId, val name: String) : DomainEvent {
    override val type get() = "AccountRenamed"
    override val aggregateType get() = AggregateType.ACCOUNT
    override val aggregateId get() = accountId.value
}

@Serializable
data class AccountArchived(val accountId: AccountId) : DomainEvent {
    override val type get() = "AccountArchived"
    override val aggregateType get() = AggregateType.ACCOUNT
    override val aggregateId get() = accountId.value
}

@Serializable
data class CategoryCreated(
    val categoryId: CategoryId,
    val name: String,
    val icon: String,
    val color: String,
    val parentCategoryId: CategoryId?,
) : DomainEvent {
    override val type get() = "CategoryCreated"
    override val aggregateType get() = AggregateType.CATEGORY
    override val aggregateId get() = categoryId.value
}

@Serializable
data class CategoryRenamed(val categoryId: CategoryId, val name: String) : DomainEvent {
    override val type get() = "CategoryRenamed"
    override val aggregateType get() = AggregateType.CATEGORY
    override val aggregateId get() = categoryId.value
}

@Serializable
data class CategoryArchived(val categoryId: CategoryId) : DomainEvent {
    override val type get() = "CategoryArchived"
    override val aggregateType get() = AggregateType.CATEGORY
    override val aggregateId get() = categoryId.value
}

@Serializable
data class TagCreated(val tagId: TagId, val name: String, val kind: TagKind, val personId: PersonId?) : DomainEvent {
    override val type get() = "TagCreated"
    override val aggregateType get() = AggregateType.TAG
    override val aggregateId get() = tagId.value
}

@Serializable
data class TagRenamed(val tagId: TagId, val name: String) : DomainEvent {
    override val type get() = "TagRenamed"
    override val aggregateType get() = AggregateType.TAG
    override val aggregateId get() = tagId.value
}

@Serializable
data class TagArchived(val tagId: TagId) : DomainEvent {
    override val type get() = "TagArchived"
    override val aggregateType get() = AggregateType.TAG
    override val aggregateId get() = tagId.value
}

@Serializable
data class PersonCreated(val personId: PersonId, val alias: String) : DomainEvent {
    override val type get() = "PersonCreated"
    override val aggregateType get() = AggregateType.PERSON
    override val aggregateId get() = personId.value
}

@Serializable
data class PersonRenamed(val personId: PersonId, val alias: String) : DomainEvent {
    override val type get() = "PersonRenamed"
    override val aggregateType get() = AggregateType.PERSON
    override val aggregateId get() = personId.value
}

@Serializable
data class PersonArchived(val personId: PersonId) : DomainEvent {
    override val type get() = "PersonArchived"
    override val aggregateType get() = AggregateType.PERSON
    override val aggregateId get() = personId.value
}

@Serializable
data class IncomeSourceCreated(val incomeSourceId: IncomeSourceId, val name: String, val personId: PersonId?) : DomainEvent {
    override val type get() = "IncomeSourceCreated"
    override val aggregateType get() = AggregateType.INCOME_SOURCE
    override val aggregateId get() = incomeSourceId.value
}

@Serializable
data class ProjectionsRebuilt(val projectorVersions: Map<String, Int>, val reason: String) : DomainEvent {
    override val type get() = "ProjectionsRebuilt"
    override val aggregateType get() = AggregateType.SYSTEM
    override val aggregateId get() = "projections"
}
