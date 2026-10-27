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
| Tipo de movimiento | `MovementType` | enum: `INCOME`, `EXPENSE`, `TRANSFER` (vigentes); `REIMBURSEMENT`, `LOAN_DISBURSEMENT`, `LOAN_REPAYMENT`, `ADJUSTMENT`, `FUND_UNTRACKED_SPEND` (futuro: llegan con sus features) | Solo `INCOME` y `EXPENSE` cuentan en reportes de ingreso/gasto; además cuentan como gasto, en su categoría, la comisión y el gasto no registrado de un fondo (D11). |
| Saldo inicial | `openingBalance` | `Money` | Saldo al crear la cuenta; fuente de origen `OPENING_BALANCE`. |
| Ingreso | `Income` | `MovementType.INCOME` | Dinero que entra a una cuenta desde fuera. |
| Gasto | `Expense` | `MovementType.EXPENSE` | Dinero que sale de una cuenta hacia fuera. |
| Transferencia | `Transfer` | `MovementType.TRANSFER` | Dinero entre dos cuentas propias. No es ingreso ni gasto. No se anula ni se corrige: se revierte con otra transferencia en sentido contrario (D12). |
| Retiro | `Withdrawal` | subtipo de `Transfer` | Banco → Efectivo. |
| Depósito | `Deposit` | subtipo de `Transfer` | Efectivo → Banco. |
| Comisión | `Fee` | `Expense` vinculado | Cargo asociado a una transferencia. Es gasto real: no se deshace al revertir la transferencia (D11, D12). |
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
| Cúmulo | `ReimbursementPool` (corto: `Pool`, `PoolId`) | entidad | Grupo de gastos que **siempre** reembolsa una persona. |
| Responsable del cúmulo | `responsiblePersonId` | `PersonId` | Quien reembolsa. Obligatorio. |
| Tipo de cúmulo | `PoolKind` | enum: `PERMANENT`, `EVENT` | Permanente con periodos / por evento. |
| Periodo del cúmulo | `PoolPeriod` | entidad | Tramo de un cúmulo permanente entre rendiciones. |
| Estado del periodo | `PoolPeriodStatus` | enum: `OPEN`, `STATEMENT_SENT`, `SETTLED` | Abierto / Rendido / Saldado. |
| Rendición de cuentas | `SettlementStatement` | entidad + archivo | Resumen compartible de lo pendiente. |
| Reembolso | `Reimbursement` | entidad | Pago recibido que salda gastos de un cúmulo o una deuda. |
| Asignación de reembolso | `ReimbursementAllocation` | entidad | Qué parte de un reembolso salda qué gasto. |
| Estrategia de asignación | `AllocationStrategy` | enum: `OLDEST_FIRST`, `MANUAL` | |
| Estado de reembolso de un gasto | `ReimbursementStatus` | enum: `PENDING`, `PARTIALLY_REIMBURSED`, `REIMBURSED` | |
| Formato de rendición | `StatementFormat` | enum: `IMAGE`, `PDF` | Archivo en que se comparte la rendición. |
| Parte pendiente | `PendingPart` | valor de dominio | Parte de un gasto en cúmulo con lo que falta reembolsar; entrada del reparto. |
| Reparto del más antiguo primero | `OldestFirstAllocator` | regla de dominio | Calcula las asignaciones de `OLDEST_FIRST`. |
| Excede lo pendiente | `ExceedsPending` | `BusinessError` | Reembolso o pago mayor a lo pendiente; se rechaza (D1). |
| Pendiente sin saldar | `PendingNotZero` | `BusinessError` | Un cúmulo con algo pendiente no se archiva. |
| Gasto con reembolsos | `HasReimbursements` | `BusinessError` | Un gasto con reembolsos no se anula hasta anular el reembolso (D20). |
| Bajo lo reembolsado | `BelowReimbursedAmount` | `BusinessError` | Una parte reembolsada no puede quedar por debajo de lo reembolsado. |
| Parte reembolsada fija | `ReimbursedPartLocked` | `BusinessError` | Una parte reembolsada no se quita ni cambia de cúmulo. |
| Nada pendiente | `NothingPending` | `BusinessError` | No hay partes con algo pendiente: no se rinde. |
| Préstamo con pagos | `HasRepayments` | `BusinessError` | Un préstamo con pagos no se anula hasta anular los pagos (D20). |
| Parte reembolsada | `ReimbursedPart` | valor de dominio | Parte de un gasto que ya recibió algo de reembolso. |
| Guardia de gasto en cúmulo | `PoolExpenseGuard` | regla de dominio | Valida el cúmulo de una parte y protege los gastos con reembolsos al anular o corregir. |
| Guardia de anulación de préstamo | `LoanVoidGuard` | regla de dominio | Impide anular un préstamo con pagos vigentes. |
| Guardia de archivado de persona | `PersonBalanceGuard` | regla de dominio | Impide archivar una persona con saldo pendiente (cúmulos activos o deudas abiertas). |
| Parte pendiente con nombres | `PendingPartLine` | valor de dominio | Parte con algo pendiente y los nombres que muestra el registro de un reembolso (S34). |
| Detalle de deuda | `LoanDetail` | valor de dominio | Una deuda con la persona y sus pagos vigentes (S36). La ruta se llama igual. |
| Pago de deuda | `LoanRepaymentLine` | valor de dominio | Un pago vigente de una deuda; anularlo es `MovementVoided` (D20). |
| Cúmulo por cobrar | `PoolReceivable` | valor de dominio | Un cúmulo con lo pendiente del periodo abierto y en total. |
| Deuda por cobrar | `LoanReceivable` | valor de dominio | Una deuda suelta con lo que falta pagar. |
| Saldo por persona | `PersonReceivable` | valor de dominio | Pendiente de sus cúmulos activos más sus deudas abiertas, sin movimientos anulados. |
| Por cobrar (resumen) | `Receivables` | valor de dominio | Total, saldo por persona, cúmulos con pendiente y deudas abiertas (S30). |
| Detalle de cúmulo | `PoolDetail` | valor de dominio | Un cúmulo con sus periodos, del más reciente al más antiguo (S31). La ruta de esa pantalla se llama igual (`core/navigation`); viven en paquetes distintos. |
| Detalle de persona | `PersonDetail` | valor de dominio | Saldo, cúmulos, deudas, reembolsos y gastos de una persona (S35). La ruta se llama igual. |
| Línea de reembolso | `ReimbursementLine` | valor de dominio | Un reembolso recibido de la persona, con su marca de anulado. |
| Línea de gasto de persona | `PersonPartLine` | valor de dominio | Un gasto de sus cúmulos con su concepto y lo pendiente. |
| Línea de deuda de persona | `PersonLoanLine` | valor de dominio | Una deuda de la persona con su estado. |
| Parte de rendición | `StatementPart` | valor de dominio | Parte de un gasto del periodo con los nombres que muestra la rendición. |
| Línea de rendición | `StatementLine` | valor de dominio | Una parte con algo pendiente, tal como aparece en la rendición. |
| Contenido de la rendición | `SettlementStatementContent` | valor de dominio | Lo que se muestra y se comparte: sin notas ni saldos de cuentas. |
| Rendición generada | `GeneratedStatement` | valor de dominio | El id de la rendición confirmada y su contenido. |
| Armador de la rendición | `SettlementStatementBuilder` | regla de dominio | Arma el contenido: solo partes con pendiente, por fecha y orden de registro. |
| Pedido de compartir | `ShareRequest` | valor de UI | Lo que la pantalla de la rendición dibuja y comparte; compartir no emite eventos. |
| Compartidor de rendición | `StatementSharer` | interfaz de UI | Dibuja el archivo temporal y arma el Intent; se inyecta para que la UI no importe `data/`. |
| Deuda suelta | `Loan` | entidad | Préstamo puntual a una persona. |
| Estado de la deuda | `LoanStatus` | enum: `OPEN`, `REPAID` | Abierta / Pagada. |
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
| Bloqueo de app | `AppLock` | pantalla (S01) | Pantalla que oculta la app hasta autenticar. |
| Autenticación | `Authenticator` | interfaz de `core/security` | Verifica al usuario con biometría o credencial del dispositivo; sin PIN propio. |
| Bloqueo tras segundo plano | `autoLockMinutes` | `Int` (0 / 1 / 5 / 15) | Minutos en segundo plano antes de volver a bloquear; 0 = siempre. Por defecto 5. |
| Pantalla segura | `secureScreen` | `Boolean` | `FLAG_SECURE` global: sin capturas ni vista previa en recientes. |
| Ocultar montos | `hideAmounts` | `Boolean` | Enmascara los montos en pantalla; persiste entre bloqueos. |
| Borrado total | `WipeAllData` | caso de uso | Borra clave, bases, preferencias y caché; la clave primero. |
| Ajustes de seguridad | `SecuritySettings` | modelo | Bloqueo, minutos en segundo plano, pantalla segura y ocultar montos; viven en DataStore. |
| Modo de tema | `ThemeMode` | enum: `SYSTEM`, `LIGHT`, `DARK` | Apariencia de la app; persiste como preferencia. |
| Paso de borrado | `WipeStep` | interfaz | Una pieza del borrado total (clave, bases, preferencias, caché); cada feature puede sumar la suya y corren por prioridad. |
| Resultado del borrado | `WipeResult` | modelo | Qué pasos del borrado total fallaron; vacío = borrado completo. |
| Pestaña principal | `MainTab` | enum: `HOME`, `MOVEMENTS`, `RECEIVABLES`, `REPORTS`, `MORE` | Una de las 5 pestañas de la barra inferior; cada una con su back stack y su ruta raíz. |
| Acción de registro | `RegisterAction` | modelo | Entrada del bottom sheet del botón "+" (texto, ruta destino, orden); la aporta cada feature y sin ninguna el botón no se muestra. |
| Recordatorio | `Reminder` | |
| Resultado de caso de uso | `Outcome` | sealed: `Ok(value)` o `Failed(error)`. Un caso de uso informa un rechazo de negocio como valor, sin lanzar excepción. |
| Error de negocio | `BusinessError` | sealed: la regla que rechazó la operación. Catálogos: `BlankName`, `DuplicateName` (sin tildes ni mayúsculas), `NotFound`, `AlreadyArchived`, `BalanceNotZero` (archivar cuenta), `PersonRequired` / `PersonNotAllowed` (concepto `PERSON` / `TOPIC`), `InvalidParent` (categoría de un solo nivel), `UnsupportedCurrency` (solo `PEN`), `NegativeAmount` (saldo inicial), `Unexpected` (fallo técnico inesperado: no se hizo nada y se puede reintentar). Movimientos: `InvalidAmount`, `FutureDate`, `SameAccount`, `NoParts`, `PartsMismatch`, `InvalidPart`, `ReasonRequired`, `TransferNotEditable` (D12), `AlreadyVoided`, `TypeChangeNotAllowed` (D15). |
| Aviso de saldo negativo | `WouldGoNegative` | `BusinessError` con el saldo resultante: el saldo queda negativo y la operación lo empeora. La UI pide confirmar y repite con `confirmNegativeBalance` (D14). |
| Catálogo del sistema | `SystemCatalog` | objeto de dominio | Fuentes de ingreso (`SystemIncomeSource`) y categorías por defecto (`DefaultCategory`, incluida "Comisiones") con ids fijos; `SeedCatalogUseCase` las crea al primer arranque, solo las que falten (D10). |
| Tipo de fuente de ingreso | `IncomeSourceKind` | enum: `REGULAR`, `OPENING_BALANCE`, `UNKNOWN_ADJUSTMENT`, `REIMBURSEMENT`, `LOAN_REPAYMENT` | Las cuatro últimas son del sistema; el proyector deduce el tipo por el id fijo. |
| Estado del movimiento | `MovementStatus` | enum: `ACTIVE`, `VOIDED` | Un movimiento anulado se conserva, marcado, y sus asientos dejan de contar. |
| Tipo de parte | `PartKind` | enum: `NORMAL`, `FEE` | `FEE` es la parte sintética que genera la comisión de una transferencia (D11). |
| Asiento | `LedgerEntry` / `LedgerEntryKind` | tabla `ledger_entries`; enum: `OPENING`, `INCOME`, `EXPENSE`, `FEE`, `TRANSFER_OUT`, `TRANSFER_IN` | Cada cambio de saldo es una fila; el saldo de una cuenta es la suma de sus asientos no anulados. `OPENING` no tiene movimiento (D16). |
| Saldo a una fecha | `asOf` / `balanceAt(id, asOf)` | `LocalDate` | Fecha tope (inclusive, del hecho) hasta la que se suman los asientos no anulados para calcular el saldo de una cuenta. |
| Tipo de agregado | `AggregateType` | enum: `ACCOUNT`, `CATEGORY`, `TAG`, `PERSON`, `INCOME_SOURCE`, `MOVEMENT`, `SYSTEM` | Entidad a la que pertenece un evento; con su id forma el historial (`events.aggregate_type`). |
| Evento por guardar / guardado | `NewEvent` / `StoredEvent` | modelos | `NewEvent` = evento + fecha del hecho; `StoredEvent` = fila del registro con secuencia, hashes y el texto exacto del payload. |
| Códec de eventos | `EventCodec` / `UpcasterRegistry` | objetos | Convierten evento ↔ texto del payload (determinista) y aplican los `Upcaster` registrados (vacío en la v1). |
| Hash de evento | `EventHasher` / `HashedFields` | objeto / modelo | `SHA-256` de todas las columnas del evento salvo `sequence` y `hash`, encadenado con el hash anterior (`GENESIS` en el primero); detecta cualquier alteración. |
| Verificación de cadena | `ChainVerification` | sealed: `Valid(count)` / `Broken(sequence, reason)` | Resultado de `VerifyEventChainUseCase`; `Reason`: hueco de secuencia, enlace roto o hash distinto. |
| Registrador de eventos | `EventRecorder` | servicio de dominio | Guarda lo que decide un caso de uso: una acción del usuario = una correlación. |
| Candado de escritura de movimientos | `MovementWriteLock` | servicio `@Singleton` | Serializa corregir y anular: leer el estado del movimiento, validar y guardar ocurre sin que otra llamada se intercale, así que dos anulaciones simultáneas guardan un solo evento. |
| Reconstructor de proyecciones | `ProjectionRebuilder` | puerto | Borra y rehace todas las proyecciones en una transacción; lo usa `ReplayProjectionsUseCase`. |
| Instantánea de movimiento | `MovementSnapshot` | sealed: `Income` / `Expense` | Estado completo que lleva `MovementCorrected`; no existe para transferencias (D12). |
| Parte de movimiento | `MovementPart` / `ExpensePartInput` | modelos | Parte guardada (con su `PartKind`) / parte que el usuario edita; al corregir, una parte que sigue conserva su id (D15). |
| Borrador de movimiento | `MovementDraft` | sealed: `Income` / `Expense` | Lo que el usuario deja tras corregir; el tipo no cambia (D15). |
| Referencias sin cambio | `UnchangedReferences` | modelo | Categorías y conceptos que el movimiento ya tenía antes de corregirlo: se aceptan aunque hoy estén archivados, para poder corregir otro dato (la nota, el monto) sin cambiarlos. |
| Filtro de movimientos | `MovementFilter` | modelo | Cuenta, tipo, categoría, concepto, fechas y texto en notas; se combinan con "y". |
| Resumen / detalle de movimiento | `MovementSummary` / `MovementDetail` | modelos | Fila de la lista / movimiento con sus partes y su historial. |
| Historial de movimiento | `MovementHistoryEntry` / `HistoryKind` | enum: `REGISTERED`, `CORRECTED`, `VOIDED` | Una entrada por evento del movimiento, con su motivo si lo tiene. |
| Cuenta con saldo / línea de libro | `AccountWithBalance` / `LedgerLine` | modelos | Cuenta con su saldo actual / asiento de una cuenta (los anulados se muestran tachados). |

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
