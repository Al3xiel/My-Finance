package com.al3xiel.my_finances.data.projection.catalog

/** `CatalogDao` en memoria: el proyector solo ve la interfaz, así que se prueba sin Room. */
class FakeCatalogDao : CatalogDao {
    val accounts = linkedMapOf<String, AccountEntity>()
    val categories = linkedMapOf<String, CategoryEntity>()
    val tags = linkedMapOf<String, TagEntity>()
    val people = linkedMapOf<String, PersonEntity>()
    val incomeSources = linkedMapOf<String, IncomeSourceEntity>()

    override suspend fun upsertAccount(account: AccountEntity) { accounts[account.id] = account }
    override suspend fun upsertCategory(category: CategoryEntity) { categories[category.id] = category }
    override suspend fun upsertTag(tag: TagEntity) { tags[tag.id] = tag }
    override suspend fun upsertPerson(person: PersonEntity) { people[person.id] = person }
    override suspend fun upsertIncomeSource(source: IncomeSourceEntity) { incomeSources[source.id] = source }

    override suspend fun account(id: String) = accounts[id]
    override suspend fun category(id: String) = categories[id]
    override suspend fun tag(id: String) = tags[id]
    override suspend fun person(id: String) = people[id]
    override suspend fun incomeSource(id: String) = incomeSources[id]

    override suspend fun clearAccounts() = accounts.clear()
    override suspend fun clearCategories() = categories.clear()
    override suspend fun clearTags() = tags.clear()
    override suspend fun clearPeople() = people.clear()
    override suspend fun clearIncomeSources() = incomeSources.clear()

    fun snapshot() = listOf(accounts.toMap(), categories.toMap(), tags.toMap(), people.toMap(), incomeSources.toMap())
}
