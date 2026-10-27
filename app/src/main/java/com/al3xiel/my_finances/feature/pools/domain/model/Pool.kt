package com.al3xiel.my_finances.feature.pools.domain.model

import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.PoolKind

data class Pool(
    val id: PoolId,
    val name: String,
    val responsiblePersonId: PersonId,
    val kind: PoolKind,
    val defaultCategoryId: CategoryId?,
    val defaultTagIds: List<TagId>,
    val isArchived: Boolean,
)
