package com.al3xiel.my_finances.data.projection.catalog

import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.AccountArchived
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.CategoryArchived
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.CategoryRenamed
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.PersonArchived
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.model.event.PersonRenamed
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TagArchived
import com.al3xiel.my_finances.domain.model.event.TagCreated
import com.al3xiel.my_finances.domain.model.event.TagRenamed
import javax.inject.Inject

/** Cuentas, categorías, conceptos, personas y fuentes de ingreso. Una fila por entidad; nada que dependa del reloj. */
class CatalogProjector @Inject constructor(private val dao: CatalogDao) : EventProjector {
    override val name = "catalog"
    override val version = 1

    override suspend fun apply(event: StoredEvent) {
        when (val e = event.event) {
            is AccountCreated -> dao.upsertAccount(
                AccountEntity(e.accountId.value, e.name, e.accountType, e.currency, e.color, false, event.occurredOn.toString()),
            )
            is AccountRenamed -> dao.account(e.accountId.value)?.let { dao.upsertAccount(it.copy(name = e.name)) }
            is AccountArchived -> dao.account(e.accountId.value)?.let { dao.upsertAccount(it.copy(isArchived = true)) }

            is CategoryCreated -> dao.upsertCategory(
                CategoryEntity(e.categoryId.value, e.name, e.icon, e.color, e.parentCategoryId?.value, false),
            )
            is CategoryRenamed -> dao.category(e.categoryId.value)?.let { dao.upsertCategory(it.copy(name = e.name)) }
            is CategoryArchived -> dao.category(e.categoryId.value)?.let { dao.upsertCategory(it.copy(isArchived = true)) }

            is TagCreated -> dao.upsertTag(TagEntity(e.tagId.value, e.name, e.kind, e.personId?.value, false))
            is TagRenamed -> dao.tag(e.tagId.value)?.let { dao.upsertTag(it.copy(name = e.name)) }
            is TagArchived -> dao.tag(e.tagId.value)?.let { dao.upsertTag(it.copy(isArchived = true)) }

            is PersonCreated -> dao.upsertPerson(PersonEntity(e.personId.value, e.alias, false))
            is PersonRenamed -> dao.person(e.personId.value)?.let { dao.upsertPerson(it.copy(alias = e.alias)) }
            is PersonArchived -> dao.person(e.personId.value)?.let { dao.upsertPerson(it.copy(isArchived = true)) }

            is IncomeSourceCreated -> dao.upsertIncomeSource(
                IncomeSourceEntity(e.incomeSourceId.value, e.name, SystemCatalog.incomeSourceKind(e.incomeSourceId), e.personId?.value),
            )
            else -> Unit
        }
    }

    override suspend fun reset() {
        dao.clearAccounts()
        dao.clearCategories()
        dao.clearTags()
        dao.clearPeople()
        dao.clearIncomeSources()
    }
}
