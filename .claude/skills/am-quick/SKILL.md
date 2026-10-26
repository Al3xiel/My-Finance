---
name: am-quick
description: "App Android, carril rápido: planear y construir un cambio chico y de bajo riesgo en una sesión, con las mismas pruebas TDD y sin subagentes. Un script rechaza migraciones de Room, manifest, build, seguridad, el registro de eventos, >4 tareas o >8 archivos. Costo: liviano-medio."
argument-hint: "<pedido> | .ai-mobile/work/plan-<slug>.md"
disable-model-invocation: true
---

# Carril rápido

Todo en esta sesión, sin subagentes. Una sesión cuesta **turnos × contexto**: se recortan turnos,
nunca controles.

## 1. Plan

- Si hay plan (la entrada o el de la rama): `node .ai-mobile/am.mjs tasks` y
  `node .ai-mobile/am.mjs docs <rutas del plan>` (la sección del doc antes que el código).
- Si no: pasos 1–3 de `/am-define-task` en esta misma sesión, con **Scope check**, **Reutilización** y
  máximo 4 tareas.

## 2. Compuerta

`node .ai-mobile/am.mjs gate` → exit 1: para, muestra las razones y propone `/am-implement`. No
reescribas el plan para que pase.

## 3. Construir en lotes de hasta 3 tareas — dos rondas de tests por lote

Las reglas de Android y del proyecto se cargan solas al abrir archivos de `app/` (`.claude/rules/`).

1. **Un mensaje**: escribe *todos* los tests del lote, en `app/src/test/…` (JUnit +
   kotlinx-coroutines-test; Turbine para los `StateFlow` del ViewModel). Cada criterio de aceptación
   tiene un test que falla sin el comportamiento. Un `Clock` y un `ZoneId` fijos en los tests de
   dominio.
2. **Una corrida**: `node .ai-mobile/am.mjs test <clases del lote>` → RED **por la razón correcta**
   (la función no existe, no un import roto).
3. **Un mensaje**: implementa el lote entero. Archivos largos: `outline` + `Read` por rango.
4. **Una corrida**: `node .ai-mobile/am.mjs test --compile <clases>` → GREEN (compila Kotlin + KSP
   antes). Luego `node .ai-mobile/am.mjs fix`.
5. Marca `- [x]` en el plan con `Edit`: `  - Evidence: RED … · GREEN n/n · compile ok`.

Antes de declarar una clase o función: `git grep -n` por su nombre. Si existe, se usa. Un término de
dominio nuevo va primero al glosario. Si un intento sale mal, `/rewind` en vez de parchear encima. No
releas lo recién escrito.

## 4. Lo no planificado

Si una tarea resulta necesitar subir la versión de la base, un evento nuevo o un cambio de payload, un
permiso, una dependencia, una feature nueva o una decisión que el plan no tomó, **para**: ya no es
este carril. Agrega al plan `## Unplanned — <qué y por qué>` y propone `/am-implement`.

## 5. Cierre

`node .ai-mobile/am.mjs diff` — si dice `deep`, el cambio creció: dilo. Revisa sus alertas de docs.
Propón commits Conventional Commits en inglés (`feat(movements): …`, una línea). Sin commit sin
aprobación. Siguiente: `/am-audit`.
