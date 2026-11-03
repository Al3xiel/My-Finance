package com.al3xiel.my_finances.feature.quickentry.domain.model

enum class EntityKind { ACCOUNT, CATEGORY, TAG, POOL, FUND, INCOME_SOURCE }

/** Algo que el texto puede nombrar; `names` incluye alias (p. ej. el responsable de un cúmulo). El id viaja como texto. */
data class CatalogEntry(val kind: EntityKind, val id: String, val names: List<String>)

/** Foto de lo activo; el intérprete no consulta nada más. */
data class CatalogSnapshot(val entries: List<CatalogEntry>)
