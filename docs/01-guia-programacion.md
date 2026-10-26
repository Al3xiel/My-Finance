# 01 · Guía de programación (Android)

> Estado: borrador v0.1 · 2026-10-25
> Fuentes: material del curso 1ACC0238 (semanas 1–6, unidad 2 "Native Mobile Development") y proyectos propios (`Semana 2..6`, `practica-parcial`, `parcial`, `login`, `proyecto/mobile-app` SynHub), contrastados con la documentación oficial vigente a octubre 2026.

---

## 1. Qué enseña el curso (base que reutilizamos)

| Semana | Tema | Qué tomamos |
|---|---|---|
| 1 | Tecnologías móviles, ética | Principios éticos ACM/IEEE y CIP → ver `02-reglas-datos.md` |
| 2 | Apps nativas vs. multiplataforma | Decisión: nativo Android (Kotlin) |
| 3 | Android Foundation, UX | Jetpack Compose (Runtime, UI, Foundation, Material 3), Scaffold, TopAppBar, LazyColumn, Card, FAB, Navigation Drawer |
| 4 | Networking, Resources | Retrofit/Coil **no aplican** (app sin Internet). Sí aplica: flujo de permisos (obligatorios vs. opcionales) |
| 5 | Storage I y II | SharedPreferences, archivos internos/externos, SQLite (`SQLiteOpenHelper`), ORM → **Room** |
| 6 | Shared Preferences (práctica) | Concepto clave-valor → lo reemplazamos por **DataStore** |
| 7–8 | Trabajo parcial / Examen | No hay material nuevo |
| 9+ | Flutter | Fuera de alcance |

Patrón base del curso: **MVVM** (`ViewModel` expone estado → `@Composable` lo consume), corrutinas, Navigation Compose.

---

## 2. Hallazgos en los proyectos anteriores → reglas nuevas

El código de hace ~1,5 años funciona, pero tiene prácticas que **no** repetiremos:

| # | Hallazgo (dónde) | Problema | Regla en este proyecto |
|---|---|---|---|
| H1 | `GlobalScope.launch` en `PostViewModel` (practica-room) | Fugas de corrutinas, sin cancelación | Solo `viewModelScope` / scopes inyectados. `GlobalScope` prohibido |
| H2 | `Context` pasado a métodos del ViewModel | Fuga de memoria, ViewModel acoplado a Android | El ViewModel nunca recibe `Context`; las dependencias llegan por **Hilt** |
| H3 | DAO con funciones bloqueantes (`fun postList(): List<Post>`) | Bloquea hilos | DAO con `suspend` para escrituras y `Flow` para lecturas |
| H4 | `kapt` + `annotationProcessor` para Room | kapt está deprecado; Room 3 solo admite KSP | **KSP** obligatorio |
| H5 | Singleton manual de la BD (`@Volatile INSTANCE`) | Duplica lo que hace DI | La BD la provee un módulo Hilt `@Singleton` |
| H6 | Entidades con `var` | Estado mutable compartido | `data class` con `val`; inmutabilidad por defecto |
| H7 | Dependencias sin uso (`room-rxjava2/3`, `room-guava`, `rhino`) | APK más pesado, más superficie | Solo dependencias justificadas, declaradas en `libs.versions.toml` |
| H8 | ViewModel llama directo a `RetrofitClient`/`OpenHelper` | Sin capa de datos, imposible de testear | ViewModel → UseCase → Repository (ver §4) |
| H9 | `Log.d` con cuerpos de respuesta y datos (`TaskViewModel`) | Fuga de datos en logcat | Prohibido loguear montos, nombres, notas. Logs solo en debug y sin datos |
| H10 | Usuario y contraseña en `SharedPreferences` en texto plano (Semana 6) | Credenciales expuestas | Nada sensible en preferencias. Secretos solo vía **Android Keystore** |
| H11 | `onUpgrade` hace `drop table` (parcial, practica-sqlite) | **Pierde todos los datos** al actualizar | Migraciones Room explícitas y testeadas. `fallbackToDestructiveMigration` prohibido |
| H12 | Rutas de navegación como strings (`"Tasks/Edit/{taskId}"`) | Errores de tipeo en runtime | Rutas tipadas (`@Serializable` / `NavKey`) |
| H13 | Composables gigantes (`Tasks.kt` 63 KB, `MemberDetails.kt` 56 KB) | Inmantenible | Archivo ≤ ~300 líneas; pantalla = `Screen` (con estado) + `Content` (sin estado) + componentes |
| H14 | Mezcla español/inglés en código (`Sitios`, `Usuario`, `Navegacion`, `datos`) | Nomenclatura inconsistente | **Todo el código en inglés**; textos de UI en español vía `strings.xml`; mapeo en `03-glosario.md` |
| H15 | Uso de `!!` (`response.body()!!`) | Crashes | `!!` prohibido; usar `?.`, `requireNotNull` con mensaje, o tipos no nulos |
| H16 | `mutableStateOf` dentro de ViewModels | Mezcla capas de Compose con lógica | ViewModel expone `StateFlow<UiState>`; la UI usa `collectAsStateWithLifecycle()` |
| H17 | `com.example.*` como package | No publicable | Package propio (p. ej. `pe.gcasas.finanzas`) |
| H18 | `isMinifyEnabled = false` en release | Código fácil de inspeccionar, APK grande | R8 activado en release |
| H19 | Versiones viejas y mezcladas (coreKtx 1.10, Coil 2 y 3, Material 2 en láminas) | Incompatibilidades | Un solo catálogo de versiones; Material 3 únicamente |
| H20 | Accompanist Permissions (láminas Semana 4) | Accompanist está en retiro | `rememberLauncherForActivityResult(RequestPermission())` nativo |

---

## 3. Stack tecnológico actualizado

> Versiones verificadas en octubre 2026. Al crear el proyecto se fijan en `gradle/libs.versions.toml` con la última estable disponible ese día.

| Área | Antes (proyectos del curso) | **Ahora** | Nota |
|---|---|---|---|
| Build | AGP 8.10, plugin `kotlin-android` | **AGP 9.x** con *built-in Kotlin* | AGP 9 ya no requiere aplicar `org.jetbrains.kotlin.android` |
| Lenguaje | Kotlin 2.0.21, JVM 11 | **Kotlin 2.3.x**, **JVM 17** | |
| UI | Compose BOM 2024.09 | **Compose BOM 2026.08** + Material 3 | Material You (color dinámico) |
| Navegación | Navigation Compose 2.8/2.9 (rutas string) | **Navigation 3 (1.0, estable)** | Back stack como estado de Compose, rutas `NavKey` tipadas |
| Persistencia | Room 2.6.1 + kapt / `SQLiteOpenHelper` | **Room 3.0.x** (`androidx.room3`, solo KSP, API con corrutinas) | Alternativa conservadora: Room 2.8 si alguna librería no es compatible aún |
| Cifrado BD | — | **SQLCipher for Android 4.19.x** (`net.zetetic:sqlcipher-android`) | Room 3: `SQLCipherDriver` vía `setDriver()` |
| Preferencias | SharedPreferences | **DataStore (Preferences)** | Solo ajustes no sensibles (tema, frecuencia de recordatorio) |
| DI | Ninguna (singletons `object`) | **Hilt** (+ KSP) | |
| Async | Corrutinas + `GlobalScope` | Corrutinas + **Flow/StateFlow** | Dispatchers inyectados |
| Serialización | Gson | **kotlinx.serialization** | Payload JSON de eventos y respaldo |
| Tareas en 2º plano | — | **WorkManager** | Recordatorios de respaldo e ingresos esperados |
| Seguridad | — | **BiometricPrompt** (androidx.biometric) + **Android Keystore** | |
| Fechas | `String` | `java.time` (`Instant`, `LocalDate`, `ZoneId`) | minSdk 26 lo trae nativo |
| Red | Retrofit, OkHttp, Coil | **No se usa** | La app no declara permiso `INTERNET` |
| Tests | JUnit4 plantilla | JUnit, kotlinx-coroutines-test, Turbine, Room testing | Ver §7 |

**SDK**: `minSdk = 26`, `targetSdk = compileSdk = 36` (o la última estable).

---

## 4. Arquitectura

**Clean Architecture + MVVM**, flujo de datos unidireccional y **Event Sourcing** en la capa de datos.

```
ui/        Screen (stateful) → Content (stateless) → components
           ViewModel: StateFlow<UiState>, recibe UiAction
domain/    model (Kotlin puro), usecase, rules (reglas de negocio), repository (interfaces)
data/      eventstore (append-only), projection (projectors + tablas), repository (impl), backup/export
core/      money, time, security, di, designsystem
```

Reglas de dependencia:
- `ui` → `domain`. `data` → `domain`. **`domain` no depende de nada de Android** (sin `Context`, sin Room, sin Compose).
- Un caso de uso **no escribe tablas de proyección**: emite eventos al `EventStore`; los `Projector` actualizan las proyecciones **en la misma transacción**.
- La UI **solo lee proyecciones** (vía repositorios) y **solo escribe ejecutando casos de uso**.

Organización por *feature* dentro de cada capa:

```
pe.gcasas.finanzas
├── core/        (money, time, security, designsystem, di)
├── feature/
│   ├── accounts/       (cuentas, transferencias, conciliación)
│   ├── movements/      (ingresos, gastos, división)
│   ├── categories/
│   ├── tags/           (conceptos)
│   ├── people/
│   ├── pools/          (cúmulos, rendición, reembolsos)
│   ├── loans/          (deudas sueltas)
│   ├── funds/          (fondos, arqueo)
│   ├── expectedincome/
│   ├── reports/
│   └── backup/
└── data/eventstore/
```

---

## 5. Convenciones de código

### 5.1 Idioma y nombres
- **Todo identificador en inglés**: clases, funciones, variables, tablas, columnas, eventos, archivos, ramas y commits.
- **Textos visibles al usuario en español**, siempre en `res/values/strings.xml` (nunca hardcodeados).
- Cada término de dominio usa el nombre definido en `03-glosario.md`. Si falta, **primero se agrega al glosario**.

| Elemento | Convención | Ejemplo |
|---|---|---|
| Clases, interfaces, objetos | `PascalCase`, sustantivo | `Account`, `ReimbursementPool` |
| Funciones, variables | `camelCase`, verbo para funciones | `registerExpense()`, `availableBalance` |
| Constantes | `SCREAMING_SNAKE_CASE` | `MAX_NOTE_LENGTH` |
| Composables | `PascalCase`, sustantivo | `AccountCard`, `PoolDetailScreen` |
| Pantallas | `XxxScreen` (con estado) + `XxxContent` (sin estado) | `FundDetailScreen` / `FundDetailContent` |
| ViewModel | `XxxViewModel` | `FundDetailViewModel` |
| Estado / acciones de UI | `XxxUiState` (data class) / `XxxUiAction` (sealed interface) | `FundDetailUiState` |
| Caso de uso | `VerboSustantivoUseCase`, un `operator fun invoke` | `RegisterExpenseUseCase` |
| Repositorio | interfaz `XxxRepository`, impl `XxxRepositoryImpl` o `RoomXxxRepository` | |
| Eventos de dominio | Sustantivo + verbo en **pasado** | `ExpenseRegistered`, `FundCounted` |
| Entidades Room | `XxxEntity`; tablas `snake_case` plural | `AccountBalanceEntity` → `account_balances` |
| DAO | `XxxDao` | `EventDao` |
| Booleanos | prefijo `is/has/can/should` | `isArchived` |
| IDs | `value class` tipados | `AccountId`, `PersonId` |

### 5.2 Kotlin
- `val` por defecto; colecciones expuestas como `List`/`Map`, nunca `MutableList`.
- `!!` prohibido. `lateinit` solo en tests o inyección.
- Errores de negocio como tipos (`sealed interface DomainError`) devueltos en `Result`/`Either`; excepciones solo para fallos técnicos.
- `when` exhaustivo sobre `sealed`/`enum` (sin `else` cuando se puede evitar).
- Sin lógica en `init {}` de ViewModels que dependa de I/O sin manejo de error.

### 5.3 Dinero y fechas (reglas críticas)
- **Nunca `Double`/`Float` para dinero.** `Money(amountMinor: Long, currency: CurrencyCode)`. S/ 12.50 = `1250`.
- Redondeos (p. ej. trazabilidad por proporción) con `BigDecimal` y `RoundingMode.HALF_EVEN`; el residuo de redondeo se asigna a la mayor porción para que la suma cuadre al céntimo.
- `occurredOn: LocalDate` (cuándo pasó en la vida real) ≠ `recordedAt: Instant` (cuándo se registró, UTC).
- Zona horaria del usuario inyectada (`ZoneId`, por defecto `America/Lima`); nunca `ZoneId.systemDefault()` dentro del dominio.
- `Clock` inyectado (testeable); prohibido `Instant.now()` directo en dominio.

### 5.4 Corrutinas y Flow
- ViewModel: `viewModelScope`. Dispatchers inyectados (`@IoDispatcher`), nunca `Dispatchers.IO` hardcodeado en dominio.
- Lecturas: `Flow` desde Room → `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- Escrituras: `suspend`, dentro de `withTransaction` (evento + proyección atómicos).

### 5.5 Compose
- *State hoisting*: `XxxContent(state, onAction)` sin ViewModel → previsualizable y testeable.
- `collectAsStateWithLifecycle()` en la UI.
- Cada componente reutilizable con `@Preview` (claro/oscuro).
- Accesibilidad: `contentDescription` en íconos con acción; tamaño táctil ≥ 48 dp.
- Montos formateados con un único `MoneyFormatter` (locale `es-PE`).

### 5.6 Permisos
Solo los mínimos: `USE_BIOMETRIC`, `POST_NOTIFICATIONS` (opcional, solicitado en contexto, patrón "permiso opcional" del curso). **No** `INTERNET`, **no** almacenamiento externo (se usa Storage Access Framework).

---

## 6. Event Sourcing: reglas de implementación
1. La tabla `events` es **append-only**: el DAO no expone `@Update` ni `@Delete`, y triggers SQLite bloquean `UPDATE`/`DELETE` (ver `02-reglas-datos.md`).
2. Cada evento: `eventId (UUID)`, `type`, `schemaVersion`, `payload (JSON)`, `occurredOn`, `recordedAt`, `appVersion`, `sequence`, `previousHash`, `hash`.
3. Cambios de esquema de un evento → nueva `schemaVersion` + **upcaster** (convierte versión vieja a nueva al leer). Nunca se reescriben eventos guardados.
4. Toda proyección se puede **borrar y reconstruir** con `ReplayProjectionsUseCase`. Hay un test que lo garantiza.
5. Los `Projector` son funciones deterministas: mismo flujo de eventos → misma proyección. No leen reloj ni aleatoriedad.

---

## 7. Pruebas
| Capa | Obligatorio | Herramientas |
|---|---|---|
| Dominio (reglas de dinero, reembolso parcial, fondos, proporción de origen) | **Sí, 100% de reglas** | JUnit, kotlinx-coroutines-test |
| Proyectores (replay = estado esperado) | **Sí** | JUnit + Room in-memory |
| Migraciones de BD | **Sí, cada versión** | `room-testing` `MigrationTestHelper` |
| ViewModels | Sí, flujos principales | Turbine |
| UI | Pantallas críticas | Compose UI test |

---

## 8. Flujo de trabajo
- Git con *Conventional Commits* (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`), en inglés.
- Ramas: `main` (estable), `feat/<feature>`, `fix/<tema>`.
- Antes de commit: build + tests + lint (ktlint o detekt).
- Toda decisión de arquitectura relevante se anota en `docs/` (registro de decisiones).

---

### Referencias verificadas (oct. 2026)
- Room 3.0: https://developer.android.com/jetpack/androidx/releases/room3 · https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html
- Navigation 3 estable: https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html
- AGP 9 (built-in Kotlin): https://developer.android.com/build/releases/agp-9-0-0-release-notes
- Compose compiler / BOM: https://developer.android.com/develop/ui/compose/compiler
- SQLCipher for Android: https://github.com/sqlcipher/sqlcipher-android
