package com.al3xiel.my_finances.feature.movements.domain.rules

import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.pools.domain.model.Pool

/** Lo que un cúmulo aporta a una parte de gasto; lo comparten el editor y el registro rápido (D66). */
object PoolDefaults {
    fun categoryFor(pool: Pool, current: CategoryId?): CategoryId? = pool.defaultCategoryId ?: current

    fun tagsFor(pool: Pool, current: Set<TagId>): Set<TagId> = current + pool.defaultTagIds
}
