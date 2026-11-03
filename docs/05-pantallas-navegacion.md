# 05 · Pantallas y navegación

> Estado: borrador v0.1 · 2026-10-25
> Depende de: `03-glosario.md` (nombres), `04-modelo-datos.md` (eventos y proyecciones).
> UI en español; rutas, pantallas y componentes en inglés.

---

## 1. Principios de UX

| # | Principio | Cómo se aplica |
|---|---|---|
| U1 | **Registrar un gasto en ≤ 3 toques** | Botón "+" siempre visible, cuenta y categoría recordadas, registro rápido por texto |
| U2 | **El saldo real primero** | Inicio muestra los 4 indicadores antes que cualquier otra cosa |
| U3 | **Nada se pierde** | No hay "eliminar": hay "anular" y "corregir", con motivo y con historial visible |
| U4 | **Lo estimado se rotula** | Fondos estimados y trazabilidad de origen llevan la etiqueta "estimado" |
| U5 | **Una pantalla, una tarea** | Pantallas cortas; las secciones largas van a pantallas de detalle |
| U6 | **Privacidad visible** | Bloqueo al abrir, montos ocultables con un toque (ícono de ojo), `FLAG_SECURE` |
| U7 | **Material 3 con identidad propia** | Turquesa como color principal, estilo One UI (§9), color del sistema opcional (apagado por defecto), modo oscuro y oscuro puro, tamaño táctil ≥ 48 dp |

---

## 2. Estructura de navegación

```
                         ┌──────────────┐
                         │  AppLock      │  (biometría / PIN al abrir y al volver)
                         └──────┬───────┘
          primer uso ┌──────────┴──────────┐ uso normal
                     ▼                     ▼
              ┌────────────┐     ┌────────────────────────────────────────────────┐
              │ Onboarding │────►│                MainScaffold                    │
              └────────────┘     │  ┌──────────────────────────────────────────┐  │
                                 │  │      contenido de la pestaña activa       │  │
                                 │  └──────────────────────────────────────────┘  │
                                 │                     (+)  ← FAB "Registrar"     │
                                 │ [Inicio] [Movimientos] [Por cobrar] [Reportes] [Más] │
                                 └────────────────────────────────────────────────┘
```

**Barra inferior (5 pestañas)**

| Pestaña | Ícono | Qué agrupa |
|---|---|---|
| **Inicio** | casa | Indicadores, cuentas, fondos activos, próximos ingresos, alertas |
| **Movimientos** | lista | Historial filtrable de todos los movimientos |
| **Por cobrar** | mano con moneda | Cúmulos y deudas sueltas, saldo por persona |
| **Reportes** | gráfico | A dónde va el dinero: categorías, conceptos, cuentas, origen |
| **Más** | menú | Cuentas, fondos, catálogos, conciliación, ingresos esperados, respaldo, ajustes |

**FAB "Registrar" (+)**: visible en Inicio, Movimientos y Por cobrar. Abre un *bottom sheet*:
`Gasto` · `Ingreso` · `Transferencia` · `Reembolso recibido` · `Registro rápido (texto)`

La barra inferior usa el indicador en píldora de Material 3 sobre `surfaceContainer`. El FAB es redondeado (16 dp) en el contenedor primario. La hoja muestra cada acción con su icono en un `IconBadge` y el texto, en filas de al menos 56 dp. «Más» es una lista agrupada en una `SectionCard`.

El FAB solo aparece en la raíz de esas pestañas (no sobre un detalle apilado) y se oculta si ninguna feature aportó acciones. **Atrás** desde la raíz de una pestaña distinta de Inicio vuelve a Inicio; desde la raíz de Inicio sale de la app.

---

## 3. Inventario de pantallas

Formato: **ID · Nombre (UI)** · `RouteKey` · propósito · acciones principales → evento emitido.

### 3.1 Acceso y primer uso

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S01 | Bloqueo | `AppLock` | Logo, botón "Desbloquear" (biometría o credencial del dispositivo); el contenido no se compone mientras está bloqueada | — |
| S02 | Bienvenida | `Onboarding.Welcome` | Qué hace la app, "tus datos no salen del teléfono"; "Restaurar respaldo" solo si `CanRestoreUseCase` lo permite (mismo flujo y reinicio que S70) | Continuar · Restaurar |
| S03 | Crear cuentas | `Onboarding.Accounts` | Lista sugerida editable y quitable: BBVA, BCP, Efectivo, cada una con saldo inicial (mínimo una; sin billetera digital) | Crear → `AccountCreated` ×n, todas o ninguna (D59) |
| S04 | Seguridad | `Onboarding.Security` | Estado real del bloqueo (activado por defecto; sin huella ni credencial del dispositivo se avisa y no se activa), ocultar en recientes | Guardar ajustes |
| S05 | Respaldo | `Onboarding.Backup` | Explica el respaldo cifrado, recordatorio (interruptor y días; `POST_NOTIFICATIONS` en contexto, si se niega todo sigue funcionando) y enlace a S70 | Terminar → `onboardingCompleted` |

Orden de arranque: `AppLock` → `Onboarding` → `MainScaffold`. El flujo no tiene pestañas ni FAB. Dónde se retoma (D60): sin cuentas y sin bandera → S02; con cuentas y sin bandera → S04; con bandera → la app. Cerrar a mitad retoma en el paso que corresponde; tras restaurar un respaldo (reinicio, D44) hay cuentas sin bandera y se cae en S04 (D61); «Borrar todos mis datos» limpia la bandera y vuelve a S02. Quien ya tenía datos y actualiza la app ve S04 y S05 una vez.

### 3.2 Inicio

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S10 | Inicio | `Home` | **4 indicadores**: el Saldo en cuentas es el héroe (único número grande, D74) y Apartado en fondos, Disponible libre —en rojo y con aviso si es negativo— y Por cobrar van en tarjetas pequeñas · cuentas en un carrusel horizontal de tarjetas · fondos activos con barra de lo consumido (rótulo «estimado» solo en los modos estimados) · próximo ingreso esperado (el vencido más antiguo; si no, el programado más cercano; solo `PEN`) · hasta 3 alertas derivadas y no descartables, en este orden: `needsReview`, conciliación abierta, ingreso vencido, respaldo vencido · estados vacíos por bloque (sin cuentas, sin fondos, sin esperados) | Tocar cuenta → detalle de cuenta; fondo → detalle de fondo; `needsReview` y conciliación abierta → `ReconciliationDetail`; ingreso vencido → `IncomeEditor` prellenado desde el esperado; respaldo vencido → `Backup` · ojo en el encabezado alterna `hideAmounts` (persiste; oculta los cuatro números y las filas) |

### 3.3 Movimientos

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S20 | Movimientos | `Movements` | Lista agrupada por día, con chips de filtro (cuenta, tipo, categoría, concepto, cúmulo, fondo, rango de fechas) y búsqueda en notas | Tocar → S21 |
| S21 | Detalle de movimiento | `MovementDetail(id)` | Datos, partes, conceptos, estado de reembolso, **historial de versiones** (eventos del agregado) | Corregir → S22 · Anular → `MovementVoided` (pide motivo; no disponible en transferencias, que se revierten con otra, D12) |
| S22 | Registrar / corregir gasto | `ExpenseEditor(id?, presetPoolId?, presetFundId?)` | Monto, cuenta, fecha, nota · **partes** (por defecto 1): monto, categoría, conceptos, cúmulo (al elegirlo toma su categoría y conceptos por defecto) **o** fondo · indicador "falta asignar S/ X" | Guardar → `ExpenseRegistered` / `MovementCorrected` (pide motivo) |
| S23 | Registrar ingreso | `IncomeEditor(id?, expectedIncomeId?, reconciliationId?)` | Monto, cuenta, fuente, fecha, nota · opción "apartar en fondo" (fondo y monto ≤ el ingreso) · opción "confirma ingreso esperado" (con `expectedIncomeId` llega prellenado; sin él sugiere los pendientes de la misma cuenta y fuente: "¿confirma Sueldo ~S/ 2,000?") · con `reconciliationId` (desde S61) se oculta "apartar en fondo" y "confirma ingreso esperado", se propone la cuenta de la conciliación y el ingreso queda vinculado (D34) | Guardar → `IncomeRegistered` (+ `FundToppedUp` / `ExpectedIncomeConfirmed`) |
| S24 | Transferencia | `TransferEditor(reconciliationId?)` | Desde, hacia, monto, comisión opcional; etiqueta automática "Retiro"/"Depósito". Solo crea: no se edita ni se corrige; un error se revierte con otra transferencia (D12) | Guardar → `TransferRegistered` |
| S25 | Registro rápido | `QuickEntry` | Campo de texto + vista previa interpretada (ver §5) | Confirmar → evento correspondiente |

**Disposición de los editores de registro (S22–S25, S32, S34, S36).** Solo cambia lo visual; validaciones, orden de errores y textos de negocio no se mueven.
- Monto grande arriba (`MoneyInput` grande, teclado numérico del sistema, D72). Cuenta, categoría, concepto y fuente se eligen en **chips** (`EntityChips`); las opciones se acomodan en varias líneas, sin desplazamiento horizontal.
- Las partes del gasto son tarjetas (`SectionCard`), una por parte; el indicador "falta asignar" va bajo ellas.
- El pie (`EditorFooter`) con la píldora "Guardar" y el error de negocio queda **fijo** al fondo y sube con el teclado; el aviso de saldo negativo (D14) se conserva.
- En el registro rápido, los tramos reconocidos y los ambiguos se distinguen con `StatusBadge` y texto; un conflicto (D64) bloquea Guardar y se explica con texto.

### 3.4 Por cobrar

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S30 | Por cobrar | `Receivables` | Total por cobrar · **por persona** (cúmulos + deudas) · lista de cúmulos con pendiente del periodo · deudas sueltas abiertas | Nuevo cúmulo → S32 · Nueva deuda → S36 |
| S31 | Detalle de cúmulo | `PoolDetail(id)` | Responsable, periodo actual (gastos, total, reembolsado, pendiente), historial de periodos | Agregar gasto (abre S22 con cúmulo preseleccionado) · **Rendir cuentas** → S33 · Registrar reembolso → S34 |
| S32 | Crear / editar cúmulo | `PoolEditor(id?)` | Nombre, responsable, tipo (permanente/evento), categoría y conceptos por defecto | Guardar → `ReimbursementPoolCreated` / `Renamed` |
| S33 | Rendición de cuentas | `SettlementStatement(poolId)` | **Vista previa** de la imagen/PDF: detalle de gastos, total pendiente; elegir formato | Compartir → `SettlementStatementGenerated` + Intent de Android |
| S34 | Registrar reembolso | `ReimbursementEditor(personId?, poolId?)` | Persona, cuenta destino, monto, estrategia (antiguos primero / manual) · lista de partes pendientes con lo que se saldaría · bloqueo si excede (D1) | Guardar → `ReimbursementReceived` |
| S35 | Detalle de persona | `PersonDetail(id)` | Saldo con la persona, cúmulos, deudas, reembolsos, gastos con concepto de esa persona | — |
| S36 | Deuda suelta | `LoanEditor(id?)` / `LoanDetail(id)` | Persona, cuenta, monto, pagos recibidos | Guardar → `LoanGranted` · Registrar pago → `LoanRepaymentReceived` |

### 3.5 Reportes

| ID | Pantalla | Ruta | Contenido |
|---|---|---|---|
| S40 | Resumen | `Reports` | Selector de periodo (mes, año o rango; nunca futuro, D36) · ingresos vs. gastos propios · gasto por otros (reembolsable) · línea "No identificado" · acceso a S44 |
| S41 | Por categoría | `Reports.ByCategory` | Torta/barras que **suman 100%** · tocar → movimientos filtrados |
| S42 | Por concepto | `Reports.ByTag` | Total **por concepto por separado** (sin torta comparativa) · cruce con categoría |
| S43 | Por cuenta | `Reports.ByAccount` | Entradas, salidas y transferencias por cuenta |
| S44 | Origen del dinero | `Reports.Origin` | Composición por fuente en cada cuenta · "¿en qué se gastó lo de X?" · Sankey fuentes → cuentas → categorías · rotulado **estimado** · nota de que los fondos no se rastrean por fuente · aviso de cuenta con faltante (`UNKNOWN_ADJUSTMENT` negativa) |
| S45 | Exportar reporte | `Reports.Export` | CSV/PDF del periodo y filtros actuales, con aviso "no cifrado"; el PDF suma la composición actual por cuenta y el gasto de cada fuente, rotulados *estimado* |

### 3.6 Más

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S50 | Más | `More` | Menú de secciones | — |
| S51 | Cuentas | `Accounts` / `AccountDetail(id)` | Saldo, saldo a una fecha, asientos, última conciliación | Crear/renombrar/archivar → `Account*` (no se archiva con una conciliación `OPEN`) · **Conciliar** → S60 · aviso `needsReview` |
| S52 | Fondos | `Funds` / `FundDetail(id)` | Asignado, gastado (detallado + no registrado), restante estimado, días de uso, historial de arqueos y desviación | Recargar → `FundToppedUp` · Marcar día → `UsageDayMarked` · **Arquear** → S53 · Cerrar → `FundClosed` |
| S53 | Arqueo de fondo | `FundCount(id)` | "¿Cuánto te queda?" · cálculo de gastado vs. estimado · si hay gasto no registrado: cuenta y categoría a cargar | Confirmar → `FundCounted` |
| S54 | Crear / editar fondo | `FundEditor(id?)` | Nombre, modo de consumo, estimado diario, fechas; el modo y el inicio se muestran bloqueados cuando ya tienen historia | Guardar → `FundCreated` / `FundEdited` |
| S55 | Ingresos esperados | `ExpectedIncomes` / `ExpectedIncomeEditor(id?)` | Lista con estado (programado/vencido/confirmado/cancelado), rango de fechas, marca de cuenta archivada (las fuentes no se archivan) que bloquea Confirmar hasta editarlo; el editor corrige uno pendiente | Programar → `ExpectedIncomeScheduled` · Confirmar (abre S23) · Cancelar |
| S56 | Categorías | `Categories` | Árbol de 1 nivel | `Category*` |
| S57 | Conceptos | `Tags` | Lista por tipo (persona/tema) | `Tag*` |
| S58 | Personas | `People` | Lista de alias | `Person*` |
| S59 | Fuentes de ingreso | `IncomeSources` | Lista | `IncomeSourceCreated` |
| S70 | Respaldo y restauración | `Backup` | Último respaldo y estado de la cadena · crear (contraseña con doble ingreso, re-autenticación, selector) · restaurar (archivo → contraseña → resumen sin montos ni nombres → confirmar; deshabilitado con explicación si hay datos propios) · recordatorio (interruptor, días; pide `POST_NOTIFICATIONS`) | `BackupCreated` / `BackupRestored` |
| S71 | Privacidad y seguridad | `Privacy` | Qué se guarda, interruptores (bloqueo, minutos en segundo plano, pantalla segura, ocultar montos), **Borrar todos mis datos** (aviso → autenticar → escribir frase → borrar). Sin biometría ni credencial registradas el bloqueo no se activa y se avisa | — |
| S72 | Ajustes | `Settings` | Tema (sistema/claro/oscuro); moneda PEN y formato `es-PE` solo informativos · secciones aportadas por cada feature (`SettingsSectionContributor`), p. ej. **Recordatorios** de ingresos esperados: interruptor que pide `POST_NOTIFICATIONS` en contexto | — |
| S73 | Herramientas de desarrollador | `DevTools` | Verificar cadena de hashes, re-proyectar, comparar proyecciones, ver eventos crudos, conciliaciones `needsReview` · **Abrir paquete de diagnóstico** → S64 | `ProjectionsRebuilt` |

> S73 se habilita tocando 7 veces la versión de la app en la sección «Acerca de» de S72 (patrón de Android); el estado «herramientas activadas» va en DataStore. Cada vez que se entra pide biometría o credencial. «Abrir paquete de diagnóstico» usa el selector `OpenDocument`.

### 3.7 Conciliación y diagnóstico

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S60 | Nueva conciliación | `ReconciliationStart(accountId)` | "¿Cuánto dice tu banco?" · saldo de la app · diferencia | Registrar → `BalanceCheckRecorded` (**no ajusta**); el saldo real puede ser 0 o negativo; si ya hay una `OPEN` en la cuenta abre S61 |
| S61 | Conciliación abierta | `ReconciliationDetail(id)` | Saldo real, saldo de la app al momento, **diferencia restante en vivo** · movimientos vinculados · sugerencias (movimientos recientes sin conciliar) | Registrar movimiento olvidado (abre S22/S23/S24 vinculado) · Vincular existente → `MovementLinkedToReconciliation` · **Resolver** → S62 · Cancelar (motivo obligatorio) → `ReconciliationCancelled` · **Exportar diagnóstico** → S63 · **Exportar diccionario** → S65 |
| S62 | Resolver conciliación | `ReconciliationResolve(id)` | Causa (Saldado / Faltó registrar / Registro erróneo / Posible error de la app / Desconocida) · solo admite las causas que permite la diferencia (0: Saldado / Faltó registrar / Registro erróneo; ≠ 0: Posible error de la app / Desconocida) · si diferencia ≠ 0: vista del ingreso/egreso no identificado · nota opcional | Confirmar → `ReconciliationResolved` |
| S63 | Exportar diagnóstico | `DiagnosticExport(reconciliationId)` | Alcance, anonimización (activada), contraseña opcional, **vista previa del contenido** | Exportar → selector `CreateDocument` → relee y verifica → `DiagnosticExportCreated` (cancelar no deja evento); pide re-autenticación si el bloqueo está activo |
| S64 | Espacio de diagnóstico | `DiagnosticWorkspace(packageId)` | Resumen del paquete · **reporte de diferencias** (primer `sequence` divergente, asientos lado a lado) · generar solicitud de diccionario · aplicar diccionario · borrar espacio | — (BD separada) |
| S65 | Exportar diccionario | `DictionaryExport(exportId)` | Cargar solicitud (código o lista) · alcance parcial/completo · **lista exacta de lo que se revelará** · contraseña obligatoria | Exportar → selector `CreateDocument` → relee y verifica → `DiagnosticDictionaryExported` |

**Total MVP: 45 pantallas (49 rutas)**, incluidos editores y detalles.

---

## 4. Flujos clave

### F1 · Gasto simple (3 toques)
`+` → `Gasto` → monto (teclado numérico abierto; cuenta y categoría = últimas usadas) → **Guardar**.

### F2 · Boleta mixta (parte para mamá, parte propia)
S22: monto S/ 150 → "Dividir" → parte 1: S/ 110, Mercado, cúmulo "Casa – mamá" · parte 2: S/ 40, Mercado, sin cúmulo → Guardar.

### F3 · Rendición y reembolso
S30 → cúmulo "Perros – mamá" (S31) → **Rendir cuentas** (S33) → vista previa → compartir por WhatsApp → días después: **Registrar reembolso** (S34) → monto S/ 200, a BCP → la app muestra qué gastos se saldan → Guardar.

### F4 · Fondo de pasajes
Mamá transfiere S/ 50: `+` → `Ingreso` → BCP, fuente Mamá, **apartar en fondo "Pasajes"** → durante la semana: marcar días en S52 (o no registrar nada) → **Arquear** (S53): "me quedan S/ 12" → gastado S/ 38 vs. estimado S/ 35 → se carga el gasto no registrado a Efectivo → (opcional) Cerrar fondo y liberar o acumular el sobrante.

### F5 · Conciliación con investigación
S51 BCP → **Conciliar** (S60): el banco dice S/ 812.40, la app S/ 800.00 → Registrar (no ajusta) → S61: diferencia restante +12.40 → "Me olvidé un reembolso de S/ 10" → registrar vinculado → restante +2.40 → **Resolver** (S62): causa Desconocida → ingreso no identificado S/ 2.40.

### F6 · Auditoría con otra persona
Pareja: S61 → **Exportar diagnóstico** (S63) → te lo envía · Tú: S73 → Abrir paquete (S64) → divergencia en #482 (Account-A, Person-1) → generar solicitud → se la envías · Pareja: S61 → **Exportar diccionario** (S65) con la solicitud → te lo envía · Tú: aplicar diccionario en S64.

### F7 · Ingreso esperado
S55: programar "Sueldo, BBVA, ~S/ 2,000, entre el 28 y el 5, mensual" → Inicio lo muestra → si pasa el 5 sin confirmar: alerta "¿Ya te pagaron?" → Confirmar abre S23 prellenado.

---

## 5. Registro rápido por texto (S25)

Solo **gasto** e **ingreso**, siempre con la fecha de hoy (reloj inyectado). Transferencias, reembolsos, varias partes y comisión se hacen en los editores. Un gasto tiene una parte.

Gramática (orden libre, separado por espacios):

| Token | Interpretación | Ejemplo |
|---|---|---|
| Número | Monto: el primero que aparezca; `.` o `,`, hasta 2 decimales, prefijo `S/`; sin separador de miles | `45`, `12.50`, `S/45` |
| `+` al inicio | Ingreso en vez de gasto; entonces el token de **fuente de ingreso** entra a la tabla | `+50 bcp sueldo` |
| Nombre de cuenta | Cuenta | `bbva`, `bcp`, `efectivo` |
| Nombre de categoría | Categoría | `mercado` |
| Nombre de concepto | Concepto (0..n) | `pareja` |
| Nombre de cúmulo o su responsable | Cúmulo | `perros`, `mamá` |
| Nombre de fondo | Fondo | `pasajes` |
| Fuente de ingreso (solo con `+`) | Fuente | `sueldo` |
| Resto | Nota | |

Reglas de coincidencia:
- Sin tildes ni mayúsculas (`Locale.ROOT`). La coincidencia **exacta gana** sobre el prefijo; el prefijo exige al menos 3 letras.
- Los nombres de varias palabras se prueban primero como secuencia, de la más larga a la más corta (D63).
- Un token que coincide con más de una entidad (de cualquier tipo) se **resalta** y el usuario elige; nunca se adivina.
- `cúmulo + fondo`, o dos cuentas o dos categorías, es conflicto y bloquea Guardar hasta elegir (D64).

Valores ausentes (D62): la categoría se sugiere desde la `defaultCategoryId` del cúmulo o, si no hay, la del último gasto; la cuenta, la del último gasto activo o, si está archivada o no hay historial, la primera activa por creación. Se marcan "sugerida" (con texto, no solo color). Sin categoría, Guardar queda desactivado. Los conceptos por defecto del cúmulo se aplican como en el editor de gasto.

**Siempre muestra la vista previa interpretada antes de guardar.** Es editable en línea (tocar un campo abre `EntityPicker`). Confirmar ejecuta los mismos casos de uso y validaciones que los editores (D66). El texto no se guarda, no se restaura al cerrar la app y no se registra (D65).

---

## 6. Rutas (Navigation 3)

```kotlin
@Serializable sealed interface Route : NavKey

// Acceso
@Serializable data object AppLock : Route
@Serializable sealed interface Onboarding : Route {
    @Serializable data object Welcome : Onboarding
    @Serializable data object Accounts : Onboarding
    @Serializable data object Security : Onboarding
    @Serializable data object Backup : Onboarding
}

// Pestañas
@Serializable data object Home : Route
@Serializable data object Movements : Route
@Serializable data object Receivables : Route
@Serializable data object Reports : Route
@Serializable data object More : Route

// Detalles y editores (ejemplos)
@Serializable data class MovementDetail(val movementId: String) : Route
@Serializable data class ExpenseEditor(val id: String? = null, val presetPoolId: String? = null,
                                       val presetFundId: String? = null, val reconciliationId: String? = null) : Route
@Serializable data object Funds : Route
@Serializable data class FundDetail(val id: String) : Route
@Serializable data class FundCount(val id: String) : Route
@Serializable data class FundEditor(val id: String? = null) : Route
@Serializable data class IncomeEditor(val id: String? = null, val expectedIncomeId: String? = null,
                                      val reconciliationId: String? = null) : Route
@Serializable data class TransferEditor(val reconciliationId: String? = null) : Route
@Serializable data object ExpectedIncomes : Route
@Serializable data class ExpectedIncomeEditor(val id: String? = null) : Route
@Serializable data class PoolDetail(val id: String) : Route
@Serializable data class PoolEditor(val id: String? = null) : Route
@Serializable data class SettlementStatement(val poolId: String) : Route
@Serializable data class ReimbursementEditor(val personId: String? = null, val poolId: String? = null) : Route
@Serializable data class PersonDetail(val id: String) : Route
@Serializable data class LoanEditor(val personId: String? = null) : Route
@Serializable data class LoanDetail(val id: String) : Route
@Serializable data class ReconciliationStart(val accountId: String) : Route
@Serializable data class ReconciliationDetail(val reconciliationId: String) : Route
@Serializable data class ReconciliationResolve(val reconciliationId: String) : Route
@Serializable data class DiagnosticWorkspace(val packageId: String) : Route
@Serializable data class ReportByCategory(val from: String, val to: String) : Route
@Serializable data class ReportByTag(val from: String, val to: String) : Route
@Serializable data class ReportByAccount(val from: String, val to: String) : Route
@Serializable data class ReportExport(val from: String, val to: String) : Route
@Serializable data class MovementsFiltered(val from: String? = null, val to: String? = null,
                                           val categoryId: String? = null, val tagId: String? = null,
                                           val accountId: String? = null) : Route
// … una clase por fila del inventario (§3)
```

- Cada pestaña tiene **su propio back stack** (se conserva al cambiar de pestaña).
- Todas las rutas viven en un solo archivo (`core/navigation/Routes.kt`: la interfaz sellada exige mismo módulo y paquete); cada feature agrega las suyas ahí y aporta sus pantallas, acciones del "+" y entradas de "Más" por contribuciones Hilt (`@IntoSet`), así el shell no conoce a ninguna.
- Los editores reciben "presets" para abrirse desde contexto (desde un cúmulo, un fondo o una conciliación) sin duplicar pantallas.
- Los IDs viajan como `String` (UUID) y se convierten a `value class` en el ViewModel.

---

## 7. Componentes reutilizables (design system)

Viven en `core/designsystem`. Los de marco y agrupación son el estilo One UI de la §9; el resto son los de dominio ya existentes, rehechos con los tokens nuevos.

**Marco y agrupación**

| Componente | Uso |
|---|---|
| `ScreenScaffold` | Encabezado grande que se contrae al desplazar, botón volver por `LocalNavigator` y acciones; versión sin volver para las pestañas. Es opt-in: una pantalla no migrada sigue funcionando (D71) |
| `SectionCard` | Título de sección y tarjeta de esquinas 24 dp que agrupa filas con separadores finos |
| `PillButton` | Botón en píldora: primario, tonal o texto; objetivo táctil ≥ 48 dp |
| `IconBadge` | Icono dentro de un contenedor redondeado |
| `StatusBadge` | Estado con icono, texto y color (pendiente, vencido, necesita revisión, conciliado, estimado); el color solo refuerza (D73) |

**Montos, campos y avisos**

| Componente | Uso |
|---|---|
| `MoneyText` | Monto formateado `es-PE` con cifras tabulares; variantes héroe, normal y pequeña; color semántico opcional; respeta "ocultar montos" |
| `MoneyInput` | Monto grande con el teclado numérico del sistema, céntimos, sin decimales flotantes (D72) |
| `EntityPicker` | Selector con búsqueda para cuenta, categoría, concepto, persona, cúmulo, fondo |
| `EditorFooter` | Píldora principal fija y error de negocio del editor |
| `ReasonDialog` | Pide motivo para corregir o anular |
| `ShareSheetLauncher` | Genera imagen/PDF y abre el Intent de compartir |

**De dominio**

| Componente | Uso |
|---|---|
| `IndicatorCard` | Cada uno de los 4 indicadores de Inicio |
| `AccountCard` | Saldo y tipo de cuenta |
| `FundProgressCard` | Barra asignado/gastado/estimado |
| `MovementRow` | Fila de la lista de movimientos |
| `ExpensePartEditor` | Una parte del gasto (monto, categoría, conceptos, cúmulo/fondo) |
| `EstimatedBadge` | Etiqueta "estimado" (un `StatusBadge`); todo número de origen la lleva y no depende solo del color |
| `EmptyState` | Estados vacíos con acción sugerida |

---

## 8. Wireframes y pendientes
- Wireframes de baja fidelidad de S10, S22, S31, S53 y S61: https://claude.ai/artifact/2qfasNhbdVdjukB1z83zeH (v0.1, 2026-10-26).
- Textos finales de UI (`strings.xml`).
- Estados vacíos, de carga y de error por pantalla.

---

## 9. Sistema visual

Material 3 con estilo One UI y turquesa como color principal. Solo cambia lo visual: casos de uso, eventos y rutas no se tocan.

| Token | Valor | Regla |
|---|---|---|
| Color principal | Turquesa (marca ≈ `#00A5A8`) | El tono de texto/botón es más oscuro en claro (`#00696B`) para llegar a contraste AA; la marca pura queda para acentos grandes y gráficos |
| Esquemas | Claro, oscuro y oscuro puro (AMOLED, fondo `#000000`) | Todo par `on-` / color tiene contraste ≥ 4.5:1 |
| Color del sistema | Apagado por defecto; interruptor en Ajustes | Solo con Android 12+ (API 31) |
| Colores de finanzas (`FinanceColors`) | Ingreso, gasto, advertencia, estimado y neutro, cada uno con contenedor y `on-` | El ingreso (verde) no se confunde con el turquesa; el estado nunca se comunica solo con color (texto o icono acompañan) |
| Tipografía | Inter (OFL) empaquetada en la app; escala M3 completa | Cifras tabulares (`tnum`) en estilos numéricos; la fuente escala hasta 200 % |
| Formas | 8 / 12 / 16 / 24 / 28 dp | Tarjetas 24, botones en píldora, diálogos 28 |
| Espaciado | Rejilla de 4 dp | `Spacing` por `CompositionLocal` |
| Iconos | Vectores Material Symbols Rounded (Apache 2.0), solo los usados, índice `AppIcons` | Sin `material-icons-extended` |

**Estilo One UI.** Encabezado grande que se contrae, contenido en tarjetas agrupadas, acciones en píldoras. Sin la fuente ni los iconos de Samsung.

**Accesibilidad.** Contraste AA, objetivos táctiles ≥ 48 dp, gráficos con texto equivalente, previews en claro, oscuro y fuente grande. «Ocultar montos» cubre toda cifra nueva.
