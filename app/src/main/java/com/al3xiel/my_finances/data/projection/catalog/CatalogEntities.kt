package com.al3xiel.my_finances.data.projection.catalog

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.TagKind

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: AccountType,
    val currency: CurrencyCode,
    val color: String?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_on") val createdOn: String,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: TagKind,
    @ColumnInfo(name = "person_id") val personId: String?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val alias: String,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
)

@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: IncomeSourceKind,
    @ColumnInfo(name = "person_id") val personId: String?,
)
