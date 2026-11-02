package com.al3xiel.my_finances.data.projection.expectedincome

/** DAO en memoria: el proyector solo ve la interfaz, así que se prueba sin Room. */
class FakeExpectedIncomeDao : ExpectedIncomeDao {
    val rows = linkedMapOf<String, ExpectedIncomeEntity>()

    override suspend fun upsert(expected: ExpectedIncomeEntity) { rows[expected.id] = expected }
    override suspend fun expected(id: String) = rows[id]
    override suspend fun clear() = rows.clear()
}
