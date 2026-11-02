package com.al3xiel.my_finances.feature.reports.domain.repository

import com.al3xiel.my_finances.feature.reports.domain.model.AccountOrigin
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRecord
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod

/** Lecturas de origen para S44; el cálculo vive en `OriginCalculator`. Todo es estimado. */
interface OriginRepository {
    /** Composición actual de cada cuenta (no depende del periodo: es el saldo de hoy). */
    suspend fun composition(): List<AccountOrigin>

    /** Atribuciones de los asientos vigentes del periodo, por fecha del asiento (D42). */
    suspend fun attributions(period: ReportPeriod): List<OriginAttributionRecord>
}
