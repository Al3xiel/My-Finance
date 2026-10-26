---
paths:
  - "app/**"
  - "docs/**"
---

# finanzas-personales (local, sin Internet, Event Sourcing)

Fuente: `docs/01-guia-programacion.md`, `02-reglas-datos.md`, `03-glosario.md`, `04-modelo-datos.md`,
`05-pantallas-navegacion.md`. Lee solo la sección que aplica (`node .ai-mobile/am.mjs outline <doc>`).

- **Glosario primero**: el código usa la columna "Código (EN)"; la UI, "Término (ES)". Un término nuevo
  se agrega al glosario antes de programarlo. Prohibidos: `Transaction` (→ `Movement`), `Cluster`
  (→ `ReimbursementPool`), `Concept` (→ `Tag`), `Debt` (→ `Loan`), `date` suelto (→ `occurredOn` /
  `recordedAt`).
- **Dinero**: `Money(amountMinor: Long, currency)`; nunca `Double`/`Float`. Redondeo con `BigDecimal`
  y `HALF_EVEN`; el residuo va a la porción mayor para que cuadre al céntimo. Monto > 0; el signo lo da
  el tipo de movimiento.
- **Tiempo**: `Clock` y `ZoneId` inyectados; nunca `Instant.now()` ni `ZoneId.systemDefault()` en el
  dominio. `occurredOn: LocalDate` (cuándo pasó) ≠ `recordedAt: Instant` (cuándo se registró).
- **Eventos**: la tabla `events` es append-only (solo INSERT; triggers bloquean UPDATE/DELETE). Un
  error se corrige con `MovementCorrected` / `MovementVoided` y su `reason`. Evento nuevo → catálogo del
  glosario y payload en el modelo. Evento existente que cambia → `schemaVersion` nueva y upcaster;
  nunca se reescriben eventos guardados. IDs UUID generados en el caso de uso, no autoincrement.
- **Proyecciones**: un caso de uso emite eventos y los proyectores actualizan las tablas en la misma
  transacción. Proyectores deterministas (sin reloj ni aleatoriedad); todo se puede borrar y
  reconstruir con el replay, y hay un test que lo prueba. Un hecho derivable no es un evento.
- **Datos del usuario**: sin permiso `INTERNET` ni dependencias de red o telemetría; `allowBackup="false"`;
  permisos solo `USE_BIOMETRIC` y `POST_NOTIFICATIONS`; archivos por Storage Access Framework. Nunca
  en logs montos, nombres, notas ni payloads. Nada sensible en preferencias (DataStore solo para
  ajustes); la clave de la base en el Keystore.
- **Conciliación**: registrar no ajusta saldos; el ajuste no identificado se crea solo al resolver.
- **Commits** en inglés, Conventional Commits; ramas `feat/<feature>`, `fix/<tema>`.
