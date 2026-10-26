---
name: am-audit
description: "App Android: auditoría de la tarea antes del PR, no del repo — solo las líneas que la rama agrega o cambia. El script mide el diff, corre las reglas de Android y del proyecto y los tests, y elige lite o deep; termina en una tabla de severidad, un veredicto y una entrada del ledger. Costo: medio; alto con deep."
argument-hint: "[lite|deep] [--base main]"
disable-model-invocation: true
---

# Auditar la tarea antes del PR

## Alcance — la tarea, no el repositorio

Se audita lo que la tarea hizo: las líneas que la rama **agrega o cambia** y lo que esas líneas
**rompen directamente** (un llamador, un proyector, una migración, un doc que ahora miente).

Lo preexistente que aparece como contexto **no se investiga ni se reporta**. Única excepción, si lo
ves sin buscarlo y es **bloqueante** — pérdida o corrupción de datos del usuario, datos que salen del
teléfono, la app no abre su base —: va en *Fuera de alcance — bloqueante*, con `archivo:línea`.

## 1. Medir — un mensaje, en paralelo

- `node .ai-mobile/am.mjs diff [--depth lite|deep]` (`--depth` solo si `$ARGUMENTS` lo dice).
- `node .ai-mobile/am.mjs test --compile` (tests relacionados con el diff; solo fallos).

Tareas abiertas en el plan o tests en rojo → bloqueante: repórtalo y para. No corras `git diff`
completo: la lista de archivos ya está.

## 2. Revisar

Cada alerta mecánica se **confirma** o se **descarta con una razón** de una línea. Severidad cuando se
confirma:

| Regla | Severidad |
| --- | --- |
| `money-floating`, `events-mutation`, `event-schema-changed`, `projector-nondeterministic`, `internet-permission`, `network-dependency`, `allow-backup`, `sensitive-permission`, `sensitive-log`, `destructive-migration`, `schema-edited`, `entity-without-migration`, `version-without-migration` | blocker |
| `migration-without-test`, `clock-direct`, `rounding-mode`, `usecase-writes-projection`, `viewmodel-skips-usecase`, `domain-android-import`, `global-scope`, `not-null-assertion`, `context-in-viewmodel`, `blocking-dao`, `shared-preferences`, `autoincrement-id`, `permission-added`, `exported-component`, `duplicate-code` (tercera copia) | major |
| `glossary-missing`, `event-not-cataloged`, `forbidden-term`, `spanish-identifier`, `docs-stale` | major — los docs del proyecto son la fuente de los nombres y del modelo: se arreglan en la rama |
| `large-file-growth`, `db-access-outside-data`, `layer-violation`, `naming-standard` | major — **deuda nueva**: va en su archivo, su capa o su nombre. Lo heredado no se reporta |
| `mutable-state-in-viewmodel`, `exposed-mutable-flow`, `mutable-model`, `hardcoded-ui-text`, `string-route`, `hardcoded-dispatcher`, `collect-without-lifecycle`, `kapt`, `minify-off`, `preview-missing` | minor |
| `log-call`, `livedata`, `gson`, `accompanist`, `enum-ordinal`, `run-blocking`, `redeclared-export`, `dead-export`, `dead-file`, `generic-file-name`, `large-new-file`, `ignored-test-without-reason`, `sleep-in-test` | minor — `dead-*` puede ser parte de una feature que se cablea después: confirmar |

| Lente | Qué mira, en lo que la tarea agregó |
| --- | --- |
| Datos del usuario | Nada sale del teléfono sin una acción explícita; sin INTERNET; nada sensible en logs, preferencias ni archivos temporales; la base cifrada; respaldo y diagnóstico con su protección. |
| Integridad | Eventos append-only; correcciones como eventos nuevos con motivo; proyectores deterministas y reconstruibles; migraciones que no pierden filas; dinero en céntimos (`Long`) y redondeo que cuadra al céntimo. |
| Arquitectura | UI → caso de uso → evento → proyector; el dominio sin Android; ViewModel con `StateFlow<UiState>`; Hilt en vez de singletons; nombres del glosario. |
| Tests | Cada "código sin test propio" justificado; replay probado en cada proyector; cada migración con `MigrationTestHelper`; reglas de dinero con casos de borde (redondeo, partes que suman el total). |

**Criterio de seguridad** (el de `/security-review` de Anthropic):
- Solo problemas con un camino concreto: *escenario* (quién, con qué acceso, qué obtiene o pierde). La
  amenaza principal de una app local es otra app del teléfono, alguien con el teléfono desbloqueado o
  un archivo exportado que llega a otra persona.
- Confianza de 1 a 10; **menos de 8 no se reporta** como vulnerabilidad (si es regla del proyecto, va
  como incumplimiento con su severidad).
- No son hallazgos: DoS, rendimiento, hardening genérico, dependencias desactualizadas, archivos de test.

**lite** — en esta sesión: `git diff -U3 main -- <archivo>` de varios archivos en un mensaje.

**deep** — tres subagentes en **un** mensaje, cada uno con `model: "sonnet"`: *Datos del usuario*,
*Integridad*, *Arquitectura y tests*. Prompt: la lista de archivos y las alertas que le tocan
(copiadas de `diff`); "lee solo los hunks con `git diff -U3 main -- <archivo>`"; "para las reglas del
proyecto, lee solo la sección del doc que aplica (`node .ai-mobile/am.mjs outline docs/<doc>`)";
"solo lo que el diff agrega o cambia, o que esas líneas rompen; lo preexistente solo si es
bloqueante, marcado `fuera de alcance`"; "máximo 10 hallazgos, una línea cada uno: severidad,
`archivo:línea`, qué, arreglo, y si una regla podría detectarlo". Tú deduplicas y sintetizas.

### Revisión de seguridad dedicada

`diff` la recomienda cuando el cambio toca el manifest, la seguridad (cifrado, bloqueo, claves), el
respaldo o el registro de eventos, o ya ve un agujero. No la lances tú: pide confirmación (usa muchos
tokens) y sugiere `/security-review` (solo revisa lo commiteado).

## 3. Veredicto

| Sev. | Archivo:línea | Hallazgo | Arreglo |
| --- | --- | --- | --- |

**blocker**, **major**, **minor**, **sugerencia**. Si hubo, *Fuera de alcance — bloqueante* debajo.
Termina con **LISTO** / **NO LISTO**. Si está listo, `/am-create-pr`. No arregles nada sin que te lo
pidan.

## 4. Registrar — una llamada

Escribe `.ai-mobile/work/ledger.json` con una entrada por alerta y por hallazgo de criterio, y
`node .ai-mobile/am.mjs ledger add .ai-mobile/work/ledger.json`:

```json
[
  { "kind": "mechanical", "rule": "<regla>", "file": "<ruta>", "line": 0, "verdict": "confirmed|dismissed", "reason": "<una línea>" },
  { "kind": "judgment", "severity": "<sev>", "file": "<ruta>", "line": 0, "scope": "task|out-of-scope",
    "shape": "<la forma del problema, sin nombres propios>", "detect": "<cómo lo vería un script>", "convertible": "yes|maybe|no" }
]
```

Es lo que mide la precisión de cada regla y decide qué promover (`node .ai-mobile/am.mjs ledger report`).
Si el script rechaza una entrada, corrígela y vuelve a correrlo: no se escribe nada a medias.
