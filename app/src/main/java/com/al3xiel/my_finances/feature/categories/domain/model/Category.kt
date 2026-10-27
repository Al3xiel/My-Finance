package com.al3xiel.my_finances.feature.categories.domain.model

import com.al3xiel.my_finances.domain.model.CategoryId

data class Category(
    val id: CategoryId,
    val name: String,
    val parentId: CategoryId?,
    val isArchived: Boolean,
)
