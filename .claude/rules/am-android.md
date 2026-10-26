---
paths:
  - "app/**"
  - "gradle/**"
  - "*.gradle.kts"
---

# Android (Kotlin · Compose · Room · Hilt · corrutinas)

Lo que más se rompe. `node .ai-mobile/am.mjs diff` marca cada punto sobre las líneas nuevas.

- **Capas por feature** (`feature/<f>/{ui,domain,data,di}`): ui → domain ← data. El dominio es Kotlin
  puro (sin `Context`, Room ni Compose). El ViewModel llama casos de uso, nunca DAOs ni la base.
- **ViewModel**: expone `StateFlow<XxxUiState>` (privado `MutableStateFlow`), recibe `XxxUiAction`;
  nada de `mutableStateOf`, `Context` ni `LiveData`. Corrutinas en `viewModelScope`; `GlobalScope` y
  `runBlocking` prohibidos. Dispatchers inyectados.
- **Compose**: `XxxScreen` (con estado) + `XxxContent(state, onAction)` (sin estado, con `@Preview`
  claro/oscuro). `collectAsStateWithLifecycle()`. Textos en `strings.xml` (`stringResource`), nunca en
  el código. Rutas tipadas (`@Serializable` / `NavKey`), nunca strings. Archivo ≤ ~300 líneas.
- **Room**: DAO con `suspend` para escribir y `Flow` para leer. Cambio de entidad → versión nueva en
  `@Database`, `Migration` explícita y su test (`node .ai-mobile/am.mjs migration <nombre>`).
  `fallbackToDestructiveMigration` prohibido. Esquemas exportados en `app/schemas/` (nunca se editan).
  Enums como texto, nunca el ordinal. KSP, nunca kapt.
- **Hilt**: la base y los repositorios los provee un módulo `@Singleton`; nada de singletons manuales.
- **Kotlin**: `val` por defecto; `!!` prohibido; `data class` inmutables; errores de negocio como
  tipos (`sealed interface`), excepciones solo para fallos técnicos.
- **Nombres**: todo identificador en inglés; `XxxScreen`, `XxxViewModel`, `XxxUiState`, `XxxUiAction`,
  `VerboSustantivoUseCase`, `XxxRepository` / `XxxRepositoryImpl`, `XxxEntity`, `XxxDao`, `XxxModule`.
  Nada de `Utils`, `Helper` ni `Manager` (el hook lo exige al crear el archivo).
- **Tests**: `app/src/test` (JUnit, kotlinx-coroutines-test, Turbine) y `app/src/androidTest` (Room,
  migraciones, Compose UI). `node .ai-mobile/am.mjs test [--compile] <Clase>`.
- **Build**: dependencias en `gradle/libs.versions.toml`, solo las justificadas; R8 en release.
