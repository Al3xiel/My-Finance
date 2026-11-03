package com.al3xiel.my_finances.feature.diagnostics.domain.anonymize

import com.al3xiel.my_finances.domain.model.event.EntityType

/**
 * Qué se hace con un texto plano al exportar sin identidad (D52): `PSEUDONYM` lo reemplaza por un seudónimo estable de la
 * entidad, `REMOVE` lo elimina y `KEEP` lo deja porque no es texto de la persona (códigos, hashes, colores, identificadores).
 */
enum class Treatment { PSEUDONYM, REMOVE, KEEP }

data class FieldRule(val treatment: Treatment, val entityType: EntityType? = null)

/**
 * Lista permitida explícita: llave `Tipo.ruta` de cada `String` plano de cada evento. `AnonymizerCoverageTest` falla si hay un
 * texto sin clasificar o una regla de un campo que ya no existe; un campo nuevo no viaja hasta que alguien decide qué es.
 */
object FieldClassification {
    private fun pseudonym(type: EntityType) = FieldRule(Treatment.PSEUDONYM, type)
    private val remove = FieldRule(Treatment.REMOVE)
    private val keep = FieldRule(Treatment.KEEP)

    val rules: Map<String, FieldRule> = mapOf(
        "AccountCreated.name" to pseudonym(EntityType.ACCOUNT),
        "AccountCreated.color" to keep,
        "AccountRenamed.name" to pseudonym(EntityType.ACCOUNT),
        "CategoryCreated.name" to pseudonym(EntityType.CATEGORY),
        "CategoryCreated.icon" to keep,
        "CategoryCreated.color" to keep,
        "CategoryRenamed.name" to pseudonym(EntityType.CATEGORY),
        "TagCreated.name" to pseudonym(EntityType.TAG),
        "TagRenamed.name" to pseudonym(EntityType.TAG),
        "PersonCreated.alias" to pseudonym(EntityType.PERSON),
        "PersonRenamed.alias" to pseudonym(EntityType.PERSON),
        "IncomeSourceCreated.name" to pseudonym(EntityType.INCOME_SOURCE),
        "ReimbursementPoolCreated.name" to pseudonym(EntityType.POOL),
        "ReimbursementPoolRenamed.name" to pseudonym(EntityType.POOL),
        "FundCreated.name" to pseudonym(EntityType.FUND),
        "FundEdited.name" to pseudonym(EntityType.FUND),
        "IncomeRegistered.note" to remove,
        "ExpenseRegistered.note" to remove,
        "TransferRegistered.note" to remove,
        "MovementCorrected.snapshot.note" to remove,
        "MovementCorrected.reason" to remove,
        "MovementVoided.reason" to remove,
        "ReimbursementReceived.note" to remove,
        "LoanGranted.note" to remove,
        "ExpectedIncomeCancelled.reason" to remove,
        "ReconciliationResolved.note" to remove,
        "ReconciliationCancelled.reason" to remove,
        // Constantes técnicas (`RebuildReason`), no texto de la persona.
        "ProjectionsRebuilt.reason" to keep,
        // Auditoría propia: identificadores, hashes y seudónimos; no viajan en los paquetes, pero se clasifican igual.
        "BackupCreated.backupId" to keep,
        "BackupCreated.lastHash" to keep,
        "BackupRestored.backupId" to keep,
        "DiagnosticExportCreated.exportId" to keep,
        "DiagnosticExportCreated.packageHash" to keep,
        "DiagnosticExportCreated.pseudonymMap.*.entityId" to keep,
        "DiagnosticDictionaryExported.dictionaryId" to keep,
        "DiagnosticDictionaryExported.exportId" to keep,
        "DiagnosticDictionaryExported.revealedPseudonyms" to keep,
        "DiagnosticDictionaryExported.revealedNoteEventIds" to keep,
    )
}
