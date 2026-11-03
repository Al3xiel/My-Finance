package com.al3xiel.my_finances.data.projection.expectedincome

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** Escritura para el proyector de ingresos esperados; las consultas de pantalla vienen en T7. */
@Dao
interface ExpectedIncomeDao {
    @Upsert suspend fun upsert(expected: ExpectedIncomeEntity)

    @Query("SELECT * FROM expected_incomes WHERE id = :id") suspend fun expected(id: String): ExpectedIncomeEntity?

    @Query("DELETE FROM expected_incomes") suspend fun clear()
}
