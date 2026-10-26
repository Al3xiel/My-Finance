---
name: am-implement
description: "App Android: ejecuta las tareas abiertas del plan de la rama en lotes TDD y escribe la evidencia RED/GREEN en el plan, para que otra sesión retome solo con el plan. Subagentes Sonnet solo para 3+ tareas independientes. Nunca commitea. Costo: alto; `serial` nunca reparte."
argument-hint: "[.ai-mobile/work/plan-<slug>.md] [serial]"
disable-model-invocation: true
---

# Implementar

## 1. Cargar — un mensaje, en paralelo

- `node .ai-mobile/am.mjs tasks [plan]` — solo lo abierto; sin argumento, el plan de la rama.
- `git status --short` (cambios ajenos a la tarea: avisa y no los toques).
- `node .ai-mobile/am.mjs docs <rutas de las tareas>` — la sección del doc que cubre cada pieza.

Retomar = esto. Lo que la evidencia de las tareas cerradas ya resolvió no se vuelve a explorar.

## 2. Armar el lote (hasta 3 tareas, en orden)

Reparto **solo si** hay 3+ tareas listas, con archivos disjuntos, y `$ARGUMENTS` no dice `serial`. Ante
la duda, en serie: cada subagente arranca sin contexto y relee todo.

Si hay reparto: todos en **un** mensaje, cada uno con `model: "sonnet"` explícito (si se omite, hereda
el de la sesión). El prompt de cada uno lleva solo:
- su bloque de tarea, copiado de la salida de `tasks`;
- las rutas que puede tocar y las que no;
- "Las reglas de Android y del proyecto se cargan al abrir archivos de app/. Usa `node .ai-mobile/am.mjs
  outline|test|fix`. Reporta en ≤15 líneas, telegráfico: archivos, RED observado, GREEN n/n, dudas."

Verifica cada reporte con `node .ai-mobile/am.mjs test <sus tests>`, no releyendo su código.

## 3. El lote en serie — dos rondas de tests, no una por tarea

1. **Un mensaje**: todos los tests del lote. Dominio y proyectores en `app/src/test` (JVM, rápidos);
   lo que necesita Room real, las migraciones y la UI en `app/src/androidTest` (emulador). Cada
   proyector con un test de replay (mismos eventos → misma proyección).
2. **Una corrida**: `node .ai-mobile/am.mjs test <clases>` → RED por la razón correcta. Si los
   instrumentados no tienen emulador, dilo y sigue con los JVM.
3. **Un mensaje**: implementación del lote. Antes de declarar algo, `git grep -n` por su nombre (la
   tabla *Reutilización* del plan ya decidió). Archivos largos: `outline` + `Read` por rango.
4. **Una corrida**: `node .ai-mobile/am.mjs test --compile <clases>` → GREEN; luego `fix`.
5. Con `Edit` en el plan: `  - Evidence: RED <qué y por qué> · GREEN <n/n> · compile ok`. Una línea.

Si el lote cambia una entidad de Room: la migración en el mismo lote (`node .ai-mobile/am.mjs
migration <nombre>`, que deja la Migration y su test). Si cambia un evento existente: `schemaVersion`
nueva y su upcaster. Si cambia un nombre de dominio, un evento, una tabla o una pantalla: el doc que lo
describe en el mismo lote.

Un intento fallido se deshace con `/rewind`, no se parchea encima. No releas lo recién escrito.

## 4. Checkpoint

Una línea por lote: cerradas, abiertas, desvíos. Si el desvío cambia el alcance (migración no
planeada, permiso, dependencia, evento nuevo, una decisión que el plan no tomó), para: agrégalo como
`## Unplanned — <qué y por qué>` y pregunta. Entre lotes largos, `/compact` en ese punto de corte.

## 5. Final

Sin `- [ ]`: `node .ai-mobile/am.mjs diff` y la agrupación de commits propuesta (Conventional
Commits en inglés, una línea, sin atribución de IA). Sin commit sin aprobación. Siguiente: `/am-audit`.
