package com.al3xiel.my_finances.data.projection.catalog

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** Escritura mínima para el proyector (leer-copiar-guardar); las consultas de pantalla vienen en T14. */
@Dao
interface CatalogDao {
    @Upsert suspend fun upsertAccount(account: AccountEntity)
    @Upsert suspend fun upsertCategory(category: CategoryEntity)
    @Upsert suspend fun upsertTag(tag: TagEntity)
    @Upsert suspend fun upsertPerson(person: PersonEntity)
    @Upsert suspend fun upsertIncomeSource(source: IncomeSourceEntity)

    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): AccountEntity?
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: String): CategoryEntity?
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun tag(id: String): TagEntity?
    @Query("SELECT * FROM people WHERE id = :id") suspend fun person(id: String): PersonEntity?
    @Query("SELECT * FROM income_sources WHERE id = :id") suspend fun incomeSource(id: String): IncomeSourceEntity?

    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM income_sources") suspend fun clearIncomeSources()
}
