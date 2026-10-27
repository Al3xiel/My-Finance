package com.al3xiel.my_finances.feature.people.domain.model

import com.al3xiel.my_finances.domain.model.PersonId

data class Person(
    val id: PersonId,
    val alias: String,
    val isArchived: Boolean,
)
