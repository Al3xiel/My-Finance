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
| U7 | **Material 3** | Color dinámico, modo oscuro, tamaño táctil ≥ 48 dp |

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

---

## 3. Inventario de pantallas

Formato: **ID · Nombre (UI)** · `RouteKey` · propósito · acciones principales → evento emitido.

### 3.1 Acceso y primer uso

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S01 | Bloqueo | `AppLock` | Logo, botón "Desbloquear" (biometría), PIN de respaldo | — |
| S02 | Bienvenida | `Onboarding.Welcome` | Qué hace la app, "tus datos no salen del teléfono" | Continuar |
| S03 | Crear cuentas | `Onboarding.Accounts` | Lista sugerida: BBVA, BCP, Efectivo, cada una con saldo inicial | Crear → `AccountCreated` ×n |
| S04 | Seguridad | `Onboarding.Security` | Activar biometría, ocultar en recientes | Guardar ajustes |
| S05 | Respaldo | `Onboarding.Backup` | Explica el respaldo cifrado, frecuencia de recordatorio | Guardar ajustes |

### 3.2 Inicio

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S10 | Inicio | `Home` | **4 indicadores** (Saldo en cuentas, Apartado en fondos, Disponible libre, Por cobrar) · tarjetas de cuentas con saldo · fondos activos con barra de consumo · próximo ingreso esperado · alertas (respaldo vencido, conciliación abierta, `needsReview`, ingreso vencido) | Tocar tarjeta → detalle · ojo para ocultar montos |

### 3.3 Movimientos

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S20 | Movimientos | `Movements` | Lista agrupada por día, con chips de filtro (cuenta, tipo, categoría, concepto, cúmulo, fondo, rango de fechas) y búsqueda en notas | Tocar → S21 |
| S21 | Detalle de movimiento | `MovementDetail(id)` | Datos, partes, conceptos, estado de reembolso, **historial de versiones** (eventos del agregado) | Corregir → S22 · Anular → `MovementVoided` (pide motivo) |
| S22 | Registrar / corregir gasto | `ExpenseEditor(id?)` | Monto, cuenta, fecha, nota · **partes** (por defecto 1): monto, categoría, conceptos, cúmulo **o** fondo · indicador "falta asignar S/ X" | Guardar → `ExpenseRegistered` / `MovementCorrected` (pide motivo) |
| S23 | Registrar ingreso | `IncomeEditor(id?)` | Monto, cuenta, fuente, fecha, nota · opción "apartar en fondo" · opción "confirma ingreso esperado" | Guardar → `IncomeRegistered` (+ `FundToppedUp` / `ExpectedIncomeConfirmed`) |
| S24 | Transferencia | `TransferEditor(id?)` | Desde, hacia, monto, comisión opcional; etiqueta automática "Retiro"/"Depósito" | Guardar → `TransferRegistered` |
| S25 | Registro rápido | `QuickEntry` | Campo de texto + vista previa interpretada (ver §5) | Confirmar → evento correspondiente |

### 3.4 Por cobrar

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S30 | Por cobrar | `Receivables` | Total por cobrar · **por persona** (cúmulos + deudas) · lista de cúmulos con pendiente del periodo · deudas sueltas abiertas | Nuevo cúmulo → S32 · Nueva deuda → S36 |
| S31 | Detalle de cúmulo | `PoolDetail(id)` | Responsable, periodo actual (gastos, total, reembolsado, pendiente), historial de periodos | Agregar gasto (abre S22 con cúmulo preseleccionado) · **Rendir cuentas** → S33 · Registrar reembolso → S34 |
| S32 | Crear / editar cúmulo | `PoolEditor(id?)` | Nombre, responsable, tipo (permanente/evento), categoría y conceptos por defecto | Guardar → `ReimbursementPoolCreated` / `Renamed` |
| S33 | Rendición de cuentas | `SettlementStatement(poolId)` | **Vista previa** de la imagen/PDF: detalle de gastos, total pendiente; elegir formato | Compartir → `SettlementStatementGenerated` + Intent de Android |
| S34 | Registrar reembolso | `ReimbursementEditor(personId)` | Persona, cuenta destino, monto, estrategia (antiguos primero / manual) · lista de partes pendientes con lo que se saldaría · bloqueo si excede (D1) | Guardar → `ReimbursementReceived` |
| S35 | Detalle de persona | `PersonDetail(id)` | Saldo con la persona, cúmulos, deudas, reembolsos, gastos con concepto de esa persona | — |
| S36 | Deuda suelta | `LoanEditor(id?)` / `LoanDetail(id)` | Persona, cuenta, monto, pagos recibidos | Guardar → `LoanGranted` · Registrar pago → `LoanRepaymentReceived` |

### 3.5 Reportes

| ID | Pantalla | Ruta | Contenido |
|---|---|---|---|
| S40 | Resumen | `Reports` | Selector de periodo · ingresos vs. gastos propios · gasto por otros (reembolsable) · línea "No identificado" |
| S41 | Por categoría | `Reports.ByCategory` | Torta/barras que **suman 100%** · tocar → movimientos filtrados |
| S42 | Por concepto | `Reports.ByTag` | Total **por concepto por separado** (sin torta comparativa) · cruce con categoría |
| S43 | Por cuenta | `Reports.ByAccount` | Entradas, salidas y transferencias por cuenta |
| S44 | Origen del dinero | `Reports.Origin` | Composición por fuente en cada cuenta · "¿en qué se gastó lo de X?" · Sankey fuentes → cuentas → categorías · rotulado **estimado** |
| S45 | Exportar reporte | `Reports.Export` | CSV/PDF del periodo y filtros actuales, con aviso "no cifrado" |

### 3.6 Más

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S50 | Más | `More` | Menú de secciones | — |
| S51 | Cuentas | `Accounts` / `AccountDetail(id)` | Saldo, saldo a una fecha, asientos, última conciliación | Crear/renombrar/archivar → `Account*` · **Conciliar** → S60 |
| S52 | Fondos | `Funds` / `FundDetail(id)` | Asignado, gastado (detallado + no registrado), restante estimado, días de uso, historial de arqueos y desviación | Recargar → `FundToppedUp` · Marcar día → `UsageDayMarked` · **Arquear** → S53 · Cerrar → `FundClosed` |
| S53 | Arqueo de fondo | `FundCount(id)` | "¿Cuánto te queda?" · cálculo de gastado vs. estimado · si hay gasto no registrado: cuenta y categoría a cargar | Confirmar → `FundCounted` |
| S54 | Crear fondo | `FundEditor(id?)` | Nombre, modo de consumo, estimado diario, fechas | Guardar → `FundCreated` |
| S55 | Ingresos esperados | `ExpectedIncomes` / `ExpectedIncomeEditor(id?)` | Lista con estado (programado/vencido/confirmado), rango de fechas | Programar → `ExpectedIncomeScheduled` · Confirmar (abre S23) · Cancelar |
| S56 | Categorías | `Categories` | Árbol de 1 nivel | `Category*` |
| S57 | Conceptos | `Tags` | Lista por tipo (persona/tema) | `Tag*` |
| S58 | Personas | `People` | Lista de alias | `Person*` |
| S59 | Fuentes de ingreso | `IncomeSources` | Lista | `IncomeSourceCreated` |
| S70 | Respaldo y restauración | `Backup` | Último respaldo, crear respaldo (contraseña + destino), restaurar, frecuencia de recordatorio | `BackupCreated` / `BackupRestored` |
| S71 | Privacidad y seguridad | `Privacy` | Qué se guarda, biometría, ocultar en recientes, **Borrar todos mis datos** | — |
| S72 | Ajustes | `Settings` | Tema, moneda por defecto, formato | — |
| S73 | Herramientas de desarrollador | `DevTools` | Verificar cadena de hashes, re-proyectar, comparar proyecciones, ver eventos crudos, conciliaciones `needsReview` · **Abrir paquete de diagnóstico** → S64 | `ProjectionsRebuilt` |

> S73 se habilita tocando 7 veces la versión de la app en "Acerca de" (patrón de Android).

### 3.7 Conciliación y diagnóstico

| ID | Pantalla | Ruta | Contenido | Acciones → evento |
|---|---|---|---|---|
| S60 | Nueva conciliación | `ReconciliationStart(accountId)` | "¿Cuánto dice tu banco?" · saldo de la app · diferencia | Registrar → `BalanceCheckRecorded` (**no ajusta**) |
| S61 | Conciliación abierta | `ReconciliationDetail(id)` | Saldo real, saldo de la app al momento, **diferencia restante en vivo** · movimientos vinculados · sugerencias (movimientos recientes sin conciliar) | Registrar movimiento olvidado (abre S22/S23/S24 vinculado) · Vincular existente → `MovementLinkedToReconciliation` · **Resolver** → S62 · Cancelar → `ReconciliationCancelled` · **Exportar diagnóstico** → S63 · **Exportar diccionario** → S65 |
| S62 | Resolver conciliación | `ReconciliationResolve(id)` | Causa (Saldado / Faltó registrar / Registro erróneo / Posible error de la app / Desconocida) · si diferencia ≠ 0: vista del ingreso/egreso no identificado | Confirmar → `ReconciliationResolved` |
| S63 | Exportar diagnóstico | `DiagnosticExport(reconciliationId)` | Alcance, anonimización (activada), contraseña opcional, **vista previa del contenido** | Exportar → `DiagnosticExportCreated` + Intent |
| S64 | Espacio de diagnóstico | `DiagnosticWorkspace(packageId)` | Resumen del paquete · **reporte de diferencias** (primer `sequence` divergente, asientos lado a lado) · generar solicitud de diccionario · aplicar diccionario · borrar espacio | — (BD separada) |
| S65 | Exportar diccionario | `DictionaryExport(exportId)` | Cargar solicitud (código o lista) · alcance parcial/completo · **lista exacta de lo que se revelará** · contraseña obligatoria | Exportar → `DiagnosticDictionaryExported` + Intent |

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

Gramática (orden libre, separado por espacios):

| Token | Interpretación | Ejemplo |
|---|---|---|
| Número | Monto | `45`, `12.50` |
| Nombre de cuenta | Cuenta | `bbva`, `bcp`, `efectivo` |
| Nombre de categoría | Categoría | `mercado` |
| Nombre de concepto | Concepto (0..n) | `pareja` |
| Nombre de cúmulo o su responsable | Cúmulo | `perros`, `mamá` |
| Nombre de fondo | Fondo | `pasajes` |
| `+` al inicio | Ingreso en vez de gasto | `+50 bcp mamá` |
| Resto | Nota | |

Coincidencia sin tildes ni mayúsculas, por prefijo y por alias. **Siempre muestra la vista previa interpretada antes de guardar**; lo ambiguo se resalta para elegir.

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
@Serializable data class ExpenseEditor(val movementId: String? = null, val presetPoolId: String? = null,
                                       val presetFundId: String? = null, val reconciliationId: String? = null) : Route
@Serializable data class PoolDetail(val poolId: String) : Route
@Serializable data class ReconciliationDetail(val reconciliationId: String) : Route
@Serializable data class DiagnosticWorkspace(val packageId: String) : Route
// … una clase por fila del inventario (§3)
```

- Cada pestaña tiene **su propio back stack** (se conserva al cambiar de pestaña).
- Los editores reciben "presets" para abrirse desde contexto (desde un cúmulo, un fondo o una conciliación) sin duplicar pantallas.
- Los IDs viajan como `String` (UUID) y se convierten a `value class` en el ViewModel.

---

## 7. Componentes reutilizables (design system)

| Componente | Uso |
|---|---|
| `MoneyText` | Monto formateado `es-PE`, color según signo, respeta "ocultar montos" |
| `MoneyInput` | Teclado numérico, céntimos, sin decimales flotantes |
| `IndicatorCard` | Cada uno de los 4 indicadores de Inicio |
| `AccountCard` | Saldo y tipo de cuenta |
| `FundProgressCard` | Barra asignado/gastado/estimado |
| `MovementRow` | Fila de la lista de movimientos |
| `ExpensePartEditor` | Una parte del gasto (monto, categoría, conceptos, cúmulo/fondo) |
| `EntityPicker` | Selector con búsqueda para cuenta, categoría, concepto, persona, cúmulo, fondo |
| `EstimatedBadge` | Etiqueta "estimado" |
| `ReasonDialog` | Pide motivo para corregir o anular |
| `ShareSheetLauncher` | Genera imagen/PDF y abre el Intent de compartir |
| `EmptyState` | Estados vacíos con acción sugerida |

---

## 8. Wireframes y pendientes
- Wireframes de baja fidelidad de S10, S22, S31, S53 y S61: https://claude.ai/artifact/2qfasNhbdVdjukB1z83zeH (v0.1, 2026-10-26).
- Textos finales de UI (`strings.xml`).
- Estados vacíos, de carga y de error por pantalla.
