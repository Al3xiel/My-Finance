package com.al3xiel.my_finances.feature.diagnostics.domain.anonymize

import com.al3xiel.my_finances.domain.model.event.EntityRef
import com.al3xiel.my_finances.domain.model.event.EntityType

/**
 * Reparte seudónimos estables dentro de un paquete: la misma entidad siempre recibe el mismo, aunque se renombre, y dos
 * entidades con el mismo alias reciben distintos. Las cuentas llevan letras (`Account-A`) y el resto números (`Person-1`).
 * El orden es el de la primera aparición, así que el mismo registro da siempre los mismos seudónimos.
 */
class PseudonymAllocator {
    private val byEntity = LinkedHashMap<EntityRef, String>()
    private val counters = HashMap<EntityType, Int>()

    fun pseudonymFor(ref: EntityRef): String = byEntity.getOrPut(ref) {
        val next = (counters[ref.entityType] ?: 0) + 1
        counters[ref.entityType] = next
        if (ref.entityType == EntityType.ACCOUNT) "Account-${letters(next)}" else "${prefix(ref.entityType)}-$next"
    }

    /** Seudónimo → entidad, en el orden en que se repartieron. */
    fun pseudonymMap(): Map<String, EntityRef> = byEntity.entries.associateTo(LinkedHashMap()) { it.value to it.key }

    private fun letters(position: Int): String {
        var n = position
        val out = StringBuilder()
        while (n > 0) {
            n--
            out.insert(0, 'A' + n % 26)
            n /= 26
        }
        return out.toString()
    }

    private fun prefix(type: EntityType) = when (type) {
        EntityType.ACCOUNT -> "Account"
        EntityType.PERSON -> "Person"
        EntityType.TAG -> "Tag"
        EntityType.CATEGORY -> "Category"
        EntityType.INCOME_SOURCE -> "Source"
        EntityType.POOL -> "Pool"
        EntityType.FUND -> "Fund"
    }
}
