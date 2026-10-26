---
name: am-define-task
description: "App Android: pedido → plan corto en .ai-mobile/work/plan-<slug>.md con hechos verificados, Scope check (glosario, eventos, base de datos, permisos) y tareas comprobables; termina eligiendo /am-quick o /am-implement. También actualiza un plan existente o convierte una propuesta aprobada. Costo: liviano."
argument-hint: "<qué quieres construir o cambiar> | .ai-mobile/proposals/prop-<slug>.md"
disable-model-invocation: true
---

# Definir una tarea

Entrada: `$ARGUMENTS`. Salida: un plan corto y, si el usuario aprueba, la rama. El plan es la memoria
de trabajo de la rama (no se commitea): permite `/clear` antes de implementar sin perder nada.

**Actualizar una tarea** (la rama ya tiene `.ai-mobile/work/plan-<slug>.md`, o la entrada lo nombra):
no se escribe un plan nuevo encima. `node .ai-mobile/am.mjs tasks` (solo lo abierto) y edita el plan
con `Edit`:
- lo nuevo, como tareas `- [ ]` al final del lote que corresponda;
- lo que ya no aplica, `- [-] T3 … (obsoleta: <motivo>, <fecha>)`: se conserva su historia;
- las tareas cerradas y su evidencia no se tocan; si una queda invalidada, se abre otra que la cita;
- el cambio, una línea en *Decisions* con la fecha.
Luego `node .ai-mobile/am.mjs gate` y, si toca lo que otros planes tocan, `am.mjs overlap <slug>`.

**Desde una propuesta** (`.ai-mobile/proposals/prop-<slug>.md`, de `/am-plan-feature`):
`node .ai-mobile/am.mjs proposals` primero. Si está `aprobada` y no desactualizada, su *Necesidad*,
*Alcance*, *Impacto* y *Riesgos* ya están verificados: **no los re-explores**, baja al nivel de tarea.
Si no está aprobada, pregunta. Al terminar el plan (`plan-<slug>.md`, mismo slug sin `prop-`):
`node .ai-mobile/am.mjs convert prop-<slug>` (archiva la propuesta en el plan, la borra, actualiza
sesiones y referencias). No la edites a mano.

## 1. Contexto — un solo mensaje, todo en paralelo

- `git branch --show-current`, `git log -1 --format=%h main`, `git status --short`.
- `node .ai-mobile/am.mjs docs <rutas o feature que nombra la petición>`: qué doc del proyecto cubre
  cada pieza y qué sección leer.
- 1–3 `Grep` (`files_with_matches`) con los términos de la petición, **en inglés** (el código usa los
  nombres del glosario).

**Primero los docs, después el código.** De cada doc, solo la sección que aplica
(`node .ai-mobile/am.mjs outline <doc>` y `Read` por rango): el glosario para los nombres, el modelo de
datos para eventos y tablas, las reglas de datos para privacidad y validaciones, pantallas para la UI.
Luego `Read` solo de los rangos de código que respaldan el problema. Lo que no verificaste no va en el
plan como hecho.

## 2. Preguntas — una sola vez

Lo que falte de producto va en **un** `AskUserQuestion` (máx. 4). Lo que se decide por convención (la
guía de programación del proyecto) se decide y se anota.

## 3. El plan

`.ai-mobile/work/plan-<slug>.md` (kebab-case con prefijo `plan-`; la rama será `feat/<slug>` o `fix/<slug>`):

```
# <slug>
Branch: `feat/<slug>` (from `main` <sha>)

## Objective
<1-2 frases>

## Problem
- <hecho> (`archivo:línea` o `doc §sección`)

## Scope check
- features que toca (feature/<f>/…) y piezas de core/ o data/
- términos nuevos → primero al glosario (ES, código EN, definición)
- eventos nuevos o que cambian → catálogo del glosario, payload en el modelo; uno existente que
  cambia de forma = schemaVersion nueva + upcaster (nunca se reescriben eventos guardados)
- proyecciones y tablas que cambian → versión de la base, Migration y su test
- permisos, datos que salen del teléfono, logs: ¿respeta las reglas de datos?

## Reutilización
| Necesito | Ya existe en (o "nada") | Decisión: usar · extraer · nuevo |

## Constraints
- <reglas del proyecto que aplican: dinero en Long, reloj inyectado, append-only, sin INTERNET…>

## Tasks
- [ ] T1 <criterio verificable> — `app/src/main/java/.../XxxUseCase.kt` — Test: `XxxUseCaseTest`
```

- **Reutilización** se llena buscando, no recordando: `git grep -n` por el nombre que vas a declarar
  y por los literales clave. Dos copias se toleran; la tercera se extrae.
- **Capas**: la UI escribe ejecutando casos de uso y lee proyecciones por repositorios; un caso de uso
  emite eventos, nunca escribe tablas de proyección; el dominio es Kotlin puro.
- **Tests obligatorios** (guía, pruebas): cada regla de dinero o de dominio, cada proyector (replay =
  estado esperado), cada migración (`MigrationTestHelper`), los flujos principales de cada ViewModel.
- **Docs**: una tarea para cada doc que el cambio altera (glosario, modelo, pantallas, reglas).

Rutas completas desde la raíz (la compuerta solo cuenta esas). Tareas chicas y verticales para
implementar y probar; **la feature entera va en un solo PR**. Partir solo si una parte tiene que
llegar antes; anótalo en *Decisions*.

## 4. Carril y rama

`node .ai-mobile/am.mjs gate .ai-mobile/work/plan-<slug>.md` → `RÁPIDO` (`/am-quick`) o `COMPLETO`
(`/am-implement`, con las razones del script).

**Rama:** antes de crear una, `git branch --no-merged main`. Si hay una rama abierta de la misma
feature, propón **sumar** esta tarea ahí. Rama nueva solo con aprobación.

Cierre en 3 líneas: plan, tareas, carril. Sugiere `/clear` antes del siguiente paso.
