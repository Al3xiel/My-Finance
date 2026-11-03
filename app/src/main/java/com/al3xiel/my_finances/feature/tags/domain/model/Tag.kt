package com.al3xiel.my_finances.feature.tags.domain.model

import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.TagKind

data class Tag(
    val id: TagId,
    val name: String,
    val kind: TagKind,
    val personId: PersonId?,
    val isArchived: Boolean,
)
