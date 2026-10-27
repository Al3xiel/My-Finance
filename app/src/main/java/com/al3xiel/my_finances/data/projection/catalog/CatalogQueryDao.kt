package com.al3xiel.my_finances.data.projection.catalog

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Lecturas de catálogo para repositorios y pantallas; el proyector no las usa. */
@Dao
interface CatalogQueryDao {
    @Query("SELECT * FROM accounts ORDER BY is_archived, created_on, name")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeAccount(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM categories ORDER BY name") fun observeCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM tags ORDER BY name") fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM people ORDER BY alias") fun observePeople(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM income_sources ORDER BY name") fun observeIncomeSources(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT name FROM accounts WHERE is_archived = 0") suspend fun activeAccountNames(): List<String>
    @Query("SELECT name FROM categories WHERE is_archived = 0") suspend fun activeCategoryNames(): List<String>
    @Query("SELECT name FROM tags WHERE is_archived = 0") suspend fun activeTagNames(): List<String>
    @Query("SELECT alias FROM people WHERE is_archived = 0") suspend fun activePersonAliases(): List<String>
    @Query("SELECT name FROM income_sources") suspend fun incomeSourceNames(): List<String>
}
