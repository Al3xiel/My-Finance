package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.event.ExpensePartData

/**
 * Las partes que siguen (D15) se actualizan sin borrarlas, para no perder lo que escriben los proyectores de
 * cúmulos y reembolsos; `created_sequence` es el del evento que la creó.
 */
internal suspend fun MovementDao.saveParts(movementId: String, parts: List<ExpensePartData>, sequence: Long) {
    val existing = partIdsOf(movementId).toSet()
    deletePartTagsOf(movementId)
    deletePartsById((existing - parts.map { it.partId.value }.toSet()).toList())
    val (kept, added) = parts.partition { it.partId.value in existing }
    kept.forEach { updatePartCore(it.partId.value, it.amount.amountMinor, it.categoryId.value, it.poolId?.value) }
    insertParts(
        added.map {
            MovementPartEntity(
                it.partId.value, movementId, it.amount.amountMinor, it.categoryId.value, PartKind.NORMAL,
                poolId = it.poolId?.value, createdSequence = sequence,
            )
        },
    )
    insertPartTags(
        parts.flatMap { part -> part.tagIds.map { MovementPartTagEntity(part.partId.value, it.value) } },
    )
}
