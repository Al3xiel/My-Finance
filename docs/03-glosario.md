# 03 · Glosario inglés ↔ español

> Estado: borrador v0.1 · 2026-10-25
> Regla: **el código usa la columna "Código (EN)"; la UI usa "Término (ES)"**. Si un término nuevo aparece, se agrega aquí antes de programarlo.

---

## 1. Conceptos de dominio

| Término (ES) | Código (EN) | Tipo en código | Definición |
|---|---|---|---|
| Cuenta | `Account` | entidad | Lugar donde está físicamente el dinero (BBVA, BCP, Efectivo). |
| Tipo de cuenta | `AccountType` | enum: `BANK`, `CASH`, `DIGITAL_WALLET` | |
| Saldo | `balance` | `Money` | Dinero en una cuenta según la app. |
| Movimiento | `Movement` | entidad | Registro de dinero: ingreso, gasto o transferencia. *Se evita `Transaction` para no chocar con transacciones de BD (`@Transaction`).* |
| Tipo de movimiento | `MovementType` | enum: `INCOME`, `EXPENSE`, `TRANSFER`, `REIMBURSEMENT`, `LOAN_DISBURSEMENT`, `LOAN_REPAYMENT`, `ADJUSTMENT`, `FUND_UNTRACKED_SPEND` | Solo `INCOME` y `EXPENSE` cuentan en reportes de ingreso/gasto. |
| Asiento contable | `LedgerEntry` | proyección | Cada cambio de saldo de una cuenta (±). Una transferencia genera dos. |
| Tipo de asiento | `EntryKind` | enum: `OPENING`, `INCOME`, `EXPENSE`, `FEE`, `TRANSFER_OUT`, `TRANSFER_IN`, `REIMBURSEMENT_IN`, `LOAN_OUT`, `LOAN_IN`, `ADJUSTMENT` | |
| Saldo inicial | `openingBalance` | `Money` | Saldo al crear la cuenta; fuente de origen `OPENING_BALANCE`. |
| Ingreso | `Income` | `MovementType.INCOME` | Dinero que entra a una cuenta desde fuera. |
| Gasto | `Expense` | `MovementType.EXPENSE` | Dinero que sale de una cuenta hacia fuera. |
| Transferencia | `Transfer` | `MovementType.TRANSFER` | Dinero entre dos cuentas propias. No es ingreso ni gasto. |
| Retiro | `Withdrawal` | subtipo de `Transfer` | Banco → Efectivo. |
| Depósito | `Deposit` | subtipo de `Transfer` | Efectivo → Banco. |
| Comisión | `Fee` | `Expense` vinculado | Cargo asociado a una transferencia. |
| Parte de gasto | `ExpensePart` | entidad hija | Todo gasto tiene ≥ 1 parte; cada una con monto, categoría, conceptos y, opcionalmente, cúmulo **o** fondo. "Dividir gasto" = registrar varias partes. |
| Monto | `amount` | `Money` | |
| Dinero (valor) | `Money` | value class (`amountMinor: Long`, `currency`) | Céntimos, nunca decimales. |
| Moneda | `currency` | `CurrencyCode` (`PEN`, `USD`) | |
| Categoría | `Category` | entidad | *En qué* se fue el dinero. Exactamente 1 por gasto/parte. |
| Concepto | `Tag` | entidad | *Para quién / para qué*. 0..n por gasto. Solo filtros. |
| Tipo de concepto | `TagKind` | enum: `PERSON`, `TOPIC` | |
| Persona | `Person` | entidad | Alguien con quien hay dinero de por medio (seguimiento o deudas). |
| Fuente de ingreso | `IncomeSource` | entidad | Origen de un ingreso (Sueldo, Mamá…). |
| Composición de origen | `OriginComposition` | proyección | Porcentaje del saldo de cada cuenta que proviene de cada fuente. |
| Atribución de origen | `OriginAttribution` | valor | Parte de un gasto atribuida a cada fuente. |
| Ajuste / Desconocido | `UNKNOWN_ADJUSTMENT` | fuente especial | Origen de diferencias de conciliación. |
| Conciliación | `Reconciliation` | entidad/proceso | Comparar saldo real del banco vs. saldo calculado, investigar y resolver. En dos pasos: registrar y luego resolver. |
| Estado de conciliación | `ReconciliationStatus` | enum: `OPEN`, `RESOLVED`, `CANCELLED` | |
| Causa de la diferencia | `ReconciliationCause` | enum: `BALANCED`, `MISSING_RECORD`, `RECORDING_ERROR`, `SUSPECTED_APP_ERROR`, `UNKNOWN` | Saldado / Faltó registrar / Registro erróneo / Posible error de la app / Desconocida |
| Diferencia restante | `remainingDifference` | `Money` | Diferencia que queda tras vincular movimientos olvidados o corregidos. |
| Ingreso no identificado | `UNIDENTIFIED_INCOME` | `AdjustmentDirection` | Ajuste positivo creado al resolver una conciliación. |
| Egreso no identificado | `UNIDENTIFIED_EXPENSE` | `AdjustmentDirection` | Ajuste negativo creado al resolver una conciliación. |
| Requiere revisión | `needsReview` | Boolean | La diferencia cambió tras re-proyectar: posible error de código. |
| Paquete de diagnóstico | `DiagnosticPackage` (`.fdiag`) | archivo | Exportación de una conciliación para rastrear errores en otro equipo. |
| Alcance del diagnóstico | `DiagnosticScope` | enum: `RECONCILIATION`, `ACCOUNT_HISTORY`, `FULL` | |
| Anonimización | `anonymize` / `Pseudonymizer` | función | Reemplaza alias por seudónimos estables y elimina notas. |
| Espacio de diagnóstico | `DiagnosticWorkspace` | BD separada | Donde se abre un paquete recibido; solo lectura. |
| Reporte de diferencias | `DivergenceReport` | resultado | Primer `sequence` donde el saldo exportado y el recalculado divergen. |
| Diccionario de seudónimos | `PseudonymDictionary` (`.fkey`) | archivo | Revela qué nombre real corresponde a cada seudónimo de un paquete. Separado del paquete. |
| Alcance del diccionario | `DictionaryScope` | enum: `PARTIAL`, `FULL` | |
| Solicitud de diccionario | `DictionaryRequest` | valor / código | Seudónimos y eventos que el auditor necesita revelar. |
| Mapa de seudónimos | `pseudonymMap` | `Map<String, EntityRef>` | Seudónimo → entidad, guardado en el evento de exportación. |
| Referencia a entidad | `EntityRef` | data class (`entityType`, `entityId`) | |
| Saldo real | `realBalance` | `Money` | El que el usuario ve en su banco. |
| Saldo calculado | `computedBalance` | `Money` | El que calcula la app. |
| Diferencia | `difference` | `Money` | `realBalance − computedBalance`. |

### Préstamos y reembolsos
| Término (ES) | Código (EN) | Tipo | Definición |
|---|---|---|---|
| Cúmulo | `ReimbursementPool` | entidad | Grupo de gastos que **siempre** reembolsa una persona. |
| Responsable del cúmulo | `responsiblePersonId` | `PersonId` | Quien reembolsa. Obligatorio. |
| Tipo de cúmulo | `PoolKind` | enum: `PERMANENT`, `EVENT` | Permanente con periodos / por evento. |
| Periodo del cúmulo | `PoolPeriod` | entidad | Tramo de un cúmulo permanente entre rendiciones. |
| Estado del periodo | `PoolPeriodStatus` | enum: `OPEN`, `STATEMENT_SENT`, `SETTLED` | Abierto / Rendido / Saldado. |
| Rendición de cuentas | `SettlementStatement` | entidad + archivo | Resumen compartible de lo pendiente. |
| Reembolso | `Reimbursement` | entidad | Pago recibido que salda gastos de un cúmulo o una deuda. |
| Asignación de reembolso | `ReimbursementAllocation` | entidad | Qué parte de un reembolso salda qué gasto. |
| Estrategia de asignación | `AllocationStrategy` | enum: `OLDEST_FIRST`, `MANUAL` | |
| Estado de reembolso de un gasto | `ReimbursementStatus` | enum: `PENDING`, `PARTIALLY_REIMBURSED`, `REIMBURSED` | |
| Deuda suelta | `Loan` | entidad | Préstamo puntual a una persona. |
| Por cobrar | `receivable` / `ReceivableSummary` | `Money` / proyección | Total que te deben. |

### Fondos
| Término (ES) | Código (EN) | Tipo | Definición |
|---|---|---|---|
| Fondo | `Fund` | entidad | Dinero propio apartado para un uso. Reserva virtual, no ligada a una cuenta. |
| Apartado en fondos | `reservedAmount` | `Money` | Total reservado en fondos activos. |
| Disponible libre | `availableBalance` | `Money` | `totalBalance − reservedAmount`. |
| Modo de consumo | `ConsumptionMode` | enum: `DETAILED`, `DAILY_ESTIMATE`, `NONE` | |
| Estimado diario | `dailyEstimate` | `Money` | Costo aproximado por día de uso. |
| Día de uso | `UsageDay` | entidad | Día marcado como usado en modo estimado. |
| Arqueo | `FundCount` | evento | Declarar cuánto queda realmente en el fondo. |
| Gastado (calculado) | `spentAmount` | `Money` | `allocated − declaredRemaining`. |
| Gasto no registrado | `untrackedSpend` | `Money` + cuenta | Lo gastado del fondo sin anotar; al arquear genera un gasto implícito en la cuenta elegida. |
| Desviación vs. estimado | `estimateDeviation` | `Money` | `spentAmount − estimatedSpent`. |
| Cierre de fondo | `FundClosure` | evento | |
| Destino del sobrante | `RemainderPolicy` | enum: `RELEASE_TO_AVAILABLE`, `ROLL_OVER` | Vuelve a disponible / se acumula. |

### Ingresos esperados
| Término (ES) | Código (EN) | Tipo | Definición |
|---|---|---|---|
| Ingreso esperado | `ExpectedIncome` | entidad | Ingreso previsto con fecha flexible. |
| Rango de fechas | `expectedFrom` / `expectedTo` | `LocalDate` | Ventana probable de pago. |
| Monto estimado | `estimatedAmount` | `Money` | |
| Estado | `ExpectedIncomeStatus` | enum: `SCHEDULED`, `OVERDUE`, `CONFIRMED`, `CANCELLED` | |

### Arquitectura y datos
| Término (ES) | Código (EN) | Definición |
|---|---|---|
| Evento | `DomainEvent` | Hecho inmutable declarado por el usuario. |
| Registro de eventos | `EventStore` / tabla `events` | Tabla append-only. |
| Proyección | `Projection` | Estado derivado de los eventos (saldos, por cobrar…). |
| Proyector | `Projector` | Función que aplica eventos a una proyección. |
| Reproducir | `replay` | Regenerar proyecciones desde el inicio. |
| Convertidor de versión | `Upcaster` | Lee eventos de esquema viejo como nuevos. |
| Fecha del hecho | `occurredOn` | `LocalDate`, cuándo pasó en la vida real. |
| Fecha de registro | `recordedAt` | `Instant` UTC, cuándo se guardó. |
| Corrección | `MovementCorrected` | Evento que reemplaza datos de otro. |
| Anulación | `MovementVoided` | Evento que invalida otro. |
| Motivo | `reason` | Texto obligatorio en correcciones/anulaciones. |
| Caso de uso | `UseCase` | Acción de negocio (`RegisterExpenseUseCase`). |
| Repositorio | `Repository` | Acceso a datos desde el dominio. |
| Estado de UI | `UiState` | |
| Acción de UI | `UiAction` | |
| Respaldo | `Backup` | Archivo cifrado con todos los eventos. |
| Restaurar | `Restore` | |
| Bloqueo de app | `AppLock` | |
| Recordatorio | `Reminder` | |

---

## 2. Catálogo de eventos (nombres en código)

| Evento (EN) | Significado (ES) |
|---|---|
| `AccountCreated` / `AccountRenamed` / `AccountArchived` | Cuenta creada / renombrada / archivada |
| `IncomeRegistered` | Ingreso registrado |
| `ExpenseRegistered` | Gasto registrado (con 1..n partes) |
| `TransferRegistered` | Transferencia registrada (incluye retiro/depósito) |
| `MovementCorrected` | Movimiento corregido |
| `MovementVoided` | Movimiento anulado |
| `BalanceCheckRecorded` | Conciliación registrada (saldo real vs. app, sin ajustar) |
| `MovementLinkedToReconciliation` | Movimiento vinculado a una conciliación |
| `ReconciliationResolved` | Conciliación resuelta (con causa y, si aplica, ajuste no identificado) |
| `ReconciliationCancelled` | Conciliación cancelada |
| `CategoryCreated` / `CategoryRenamed` / `CategoryArchived` | Categoría … |
| `TagCreated` / `TagRenamed` / `TagArchived` | Concepto … |
| `PersonCreated` / `PersonRenamed` / `PersonArchived` | Persona … |
| `IncomeSourceCreated` | Fuente de ingreso creada |
| `ReimbursementPoolCreated` / `ReimbursementPoolRenamed` / `ReimbursementPoolArchived` | Cúmulo creado / renombrado / archivado |
| `SettlementStatementGenerated` | Rendición generada (cierra el periodo y abre otro en cúmulos permanentes) |
| `ReimbursementReceived` | Reembolso recibido (con asignaciones) |
| `LoanGranted` / `LoanRepaymentReceived` | Deuda suelta creada / pago recibido |
| `FundCreated` | Fondo creado |
| `FundToppedUp` | Fondo recargado |
| `UsageDayMarked` / `UsageDayUnmarked` | Día de uso marcado / desmarcado |
| `FundCounted` | Arqueo de fondo |
| `FundClosed` | Fondo cerrado |
| `ExpectedIncomeScheduled` / `ExpectedIncomeConfirmed` / `ExpectedIncomeCancelled` | Ingreso esperado programado / confirmado / cancelado |
| `BackupCreated` / `BackupRestored` | Respaldo creado / restaurado (auditoría) |
| `ProjectionsRebuilt` | Proyecciones regeneradas (auditoría) |
| `DiagnosticExportCreated` | Paquete de diagnóstico exportado (auditoría) |
| `DiagnosticDictionaryExported` | Diccionario de seudónimos exportado (auditoría) |

> Hechos **derivados** no son eventos: "periodo saldado", "ingreso esperado vencido", "retiro/depósito" (se deduce de los tipos de cuenta). Asignar un gasto a un cúmulo o fondo se hace en la parte del gasto (`ExpenseRegistered` / `MovementCorrected`).

---

## 3. Términos a evitar (y por qué)

| No usar | Usar | Motivo |
|---|---|---|
| `Transaction` | `Movement` | Choca con `@Transaction` de Room y con transacción de BD |
| `Cluster` | `ReimbursementPool` | "Cluster" sugiere agrupamiento estadístico, no reembolso |
| `Concept` | `Tag` | Término estándar en apps de finanzas |
| `Debt` (genérico) | `Loan` o `ReimbursementPool` | Distingue préstamo puntual de cúmulo |
| `price`, `cost`, `total` sueltos | `amount` + contexto | Uniformidad |
| `date` suelto | `occurredOn` / `recordedAt` | Hay dos fechas distintas |
| Nombres en español en código | Término de este glosario | Regla del proyecto |
