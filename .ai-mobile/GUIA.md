# Guía de comandos y casos — herramientas am-* (apps Android)

Las instala `install.mjs` del repositorio `Al3xiel/AI-Mobile-Skills`, con el perfil del proyecto. Se
cambian allá, no aquí (ver *Actualizar*).

Dos tipos de comando:
- **Skills** (`/am-…`): se escriben en el chat de Claude Code y guían un flujo con criterio.
- **Script** (`node .ai-mobile/am.mjs <modo>`): hace lo mecánico en una llamada, sin gastar tokens del
  modelo en contar, buscar o leer salidas largas de Gradle. Las skills lo usan solas.

## Skills

| Skill | Para qué |
| --- | --- |
| `/am-plan-feature <idea>` | Planificar una funcionalidad sin comprometerse a construirla: deja una propuesta |
| `/am-define-task <pedido \| propuesta \| plan>` | Convertir un pedido o una propuesta aprobada en tarea; o actualizar el alcance de una tarea en curso |
| `/am-quick <pedido \| plan>` | Cambio chico y de bajo riesgo: planificar y construir en una sesión |
| `/am-implement [plan] [serial]` | Construir una tarea grande por lotes, con TDD y evidencia en el plan |
| `/am-audit [lite\|deep]` | Revisar lo que hizo la tarea antes del PR |
| `/am-create-pr` | Decidir si el cambio merece su propio PR y abrirlo |
| `/am-address-review [comentarios \| n° PR]` | Atender la revisión de un PR con un commit en la misma rama |
| `/am-reconcile --session <s> \| [slug…]` | Contradicciones, duplicaciones y dependencias entre tareas y propuestas |
| `/am-docs [doc \| ruta]` | Poner al día los docs del proyecto (glosario, modelo, reglas, pantallas) |
| `/am-guide <pregunta>` | Esta guía: qué comando usar para un caso |

## Script `am.mjs`

| Modo | Para qué |
| --- | --- |
| `diff [--depth lite\|deep]` | Medir el cambio: archivos, líneas, alertas de Android y del proyecto, profundidad de auditoría |
| `test [--compile] [Clase…]` | Tests JVM o instrumentados por Gradle; solo conteos y fallos |
| `fix` | ktlint o detekt (lo que el proyecto tenga configurado); muestra lo que queda |
| `outline <archivo>` | Mapa de símbolos (Kotlin, o secciones de un `.md`) con rangos de línea |
| `docs [ruta…]` | Qué doc del proyecto cubre esas rutas, qué sección leer y si quedó atrás del código |
| `impact <ruta>…` | Costo de tocar unas rutas: tamaño, cambios y fixes, superficie sensible, tests |
| `migration <nombre>` | `Migration<n>To<n+1>` de Room y su test con `MigrationTestHelper` |
| `naming <ruta>…` | ¿Cumple la ruta el estándar de estructura? (el mismo del hook) |
| `pr` | Hechos del PR: versión de la base, eventos, permisos, dependencias, docs, ramas abiertas |
| `gate [plan]` | ¿Cabe el plan en `/am-quick`? |
| `tasks [plan]` | El plan sin lo cerrado ni lo obsoleto |
| `proposals [--status x]` | Propuestas por estado, y si el código que citan cambió |
| `session new\|add\|remove\|show\|close\|list` | Agrupar planes y propuestas en sesiones |
| `session order <s> [--set "a; b + c"]` | Orden de ejecución: estado de cada paso, el siguiente y sus comandos |
| `convert <prop-slug> [--keep]` | Tras convertir una propuesta en tarea: la archiva en el plan, la borra y actualiza sesiones y referencias |
| `overlap [--session s] [slug…]` | Dónde se cruzan planes y propuestas, con la línea de cada uno |
| `debt [--check] [--save]` | Deuda estructural y puntos calientes; `--check` falla si creció |
| `ledger add\|report` | Registro de auditorías: precisión de reglas y qué promover a regla nueva |
| `backtest [--last N] [--labels tsv]` | Las reglas sobre merges ya hechos (para calibrarlas) |

## Casos

### Tengo una idea, pero no sé si se hará
`/am-plan-feature <idea>` → `.ai-mobile/proposals/prop-<slug>.md`. Para aprobarla, `Estado: aprobada`.
Para construirla: `/am-define-task .ai-mobile/proposals/prop-<slug>.md` (escribe el plan y corre `convert`).

### Hay que construir una funcionalidad
`/am-define-task <pedido>` → plan en `.ai-mobile/work/plan-<slug>.md` y el carril. `/clear`. Rápido:
`/am-quick <plan>`. Completo: `/am-implement`. Después `/am-audit` y `/am-create-pr`.

### Necesito un término o un evento nuevo
Primero al glosario (término ES, código EN, definición; el evento en el catálogo) y el payload en el
modelo de datos. `diff` marca `glossary-missing` y `event-not-cataloged` si falta.

### Cambia un evento que ya existe
Nunca se reescriben los eventos guardados: `schemaVersion` nueva en el evento y un upcaster que lee la
versión vieja como la nueva. `diff` marca `event-schema-changed` si falta.

### Cambia una entidad de Room
`node .ai-mobile/am.mjs migration <nombre_snake>` → la `Migration` y su test. Sube `version` en
`@Database`, agrégala con `.addMigrations(…)` y compila (genera el esquema nuevo en `app/schemas/`).
El test necesita emulador: `node .ai-mobile/am.mjs test <ruta del test>`.

### Toca el manifest, la seguridad, el respaldo o el registro de eventos
`/am-audit` lo detecta y recomienda `/security-review` (solo lo commiteado). Pide confirmación.

### Cambió lo que se pidió a mitad de la tarea
`/am-define-task` sobre el plan existente: tareas nuevas, `- [-] … (obsoleta: motivo)`, `gate` otra vez.

### Varias tareas o propuestas tocan lo mismo
1. `node .ai-mobile/am.mjs session new <nombre> --goal "…" <slug…>`.
2. `node .ai-mobile/am.mjs overlap --session <nombre>`.
3. `/am-reconcile --session <nombre>` → clasifica, pregunta, escribe *Coordinación* y el orden.

### Ejecutar una sesión (varios planes en orden)
`node .ai-mobile/am.mjs session order <s>`: cada paso, su estado y los comandos del siguiente. Al
terminar todo: `session close <s>`.

### Me dejaron comentarios en el PR
`/am-address-review` → clasifica, pregunta lo que cambia decisiones, un commit en la misma rama.

### Retomo una tarea otro día
Sesión nueva en la rama → `/am-implement`: lee solo lo abierto del plan.

### No sé qué doc leer
`node .ai-mobile/am.mjs docs <ruta>`: el doc y la sección. Si quedó atrás: `/am-docs <doc>`.

### Tengo que tocar un archivo largo
`node .ai-mobile/am.mjs outline <archivo>` y leer solo el rango. Por encima de 300 líneas, lo nuevo va
en su propio archivo (Screen + Content + componentes).

### El hook rechazó un archivo nuevo
Dice por qué y qué usar (sufijo de su capa, carpeta de la feature). Una feature nueva se registra en la
guía y en `FEATURES` de `.ai-mobile/profile/profile.mjs`. Una excepción justificada: la ruta en
`.ai-mobile/naming-exceptions.txt` con `# motivo`.

### Quiero saber cuánta deuda hay o si la empeoré
`node .ai-mobile/am.mjs debt`; `debt --check` antes del PR. Tras un refactor que la baja: `debt --save`.

## Nombres

- Tareas: `.ai-mobile/work/plan-<slug>.md`; su rama, `feat/<slug>` o `fix/<slug>`.
- Propuestas: `.ai-mobile/proposals/prop-<slug>.md`. El hook rechaza un documento sin su prefijo.
- Commits: Conventional Commits en inglés, una línea.

## Dónde vive cada cosa

```
CLAUDE.md                          el bloque am-* (lo agrega install.mjs)
.ai-mobile/am.mjs, lib/, hooks/    el script, las reglas de Android y los hooks
.ai-mobile/profile/                el perfil del proyecto: paquete, features, docs y sus reglas
.claude/skills/am-*/               las skills · .claude/rules/am-*.md: reglas que se cargan al tocar app/
.claude/settings.json              los hooks
.ai-mobile/work/ proposals/ sessions/ ledger/   memoria de trabajo local (ignorada por git)
```

## Actualizar

`git pull` en `AI-Mobile-Skills` y `node install.mjs <proyecto> --profile <perfil>`. La memoria de
trabajo local no se toca.
