---
name: am-plan-feature
description: "App Android: planificar una idea de funcionalidad que puede o no construirse — una propuesta corta en .ai-mobile/proposals/prop-<slug>.md con necesidad, alcance, impacto (eventos, tablas, pantallas, permisos), alternativas, riesgos y esfuerzo. Sin rama, código ni commits. Una propuesta aprobada alimenta /am-define-task. Costo: liviano."
argument-hint: "<idea de funcionalidad> | <slug de una propuesta existente>"
disable-model-invocation: true
---

# Planificar una funcionalidad

Flujo **aparte** del de tareas: planificar no compromete a construir. El resultado es una propuesta
que se discute, aprueba, deja en espera o descarta. Si se aprueba, `/am-define-task` la convierte en
tarea sin volver a explorar. Nada de ramas, código ni commits.

## 1. ¿Nueva o existente?

`node .ai-mobile/am.mjs proposals`. Si `$ARGUMENTS` coincide con una propuesta (o una parecida ya
existe), se **actualiza** esa. Si la marca como desactualizada, revisa solo lo que cambió.

## 2. Contexto — barato, en un mensaje

- `node .ai-mobile/am.mjs docs <feature o ruta>` y la sección que aplica de cada doc: glosario (¿los
  términos existen?), modelo de datos (¿qué eventos y tablas toca?), pantallas (¿dónde vive en la
  navegación?), reglas de datos (¿qué dato nuevo guarda y con qué sensibilidad?).
- 1–3 `Grep` (`files_with_matches`) para ubicar dónde caería, si ya hay código.
- `node .ai-mobile/am.mjs impact <carpetas candidatas>`: tamaño, puntos calientes, superficie sensible
  y tests, sin abrir archivos.

No se diseña la implementación: eso es de `/am-define-task` cuando se apruebe.

## 3. Preguntas — una sola vez

Lo que no se puede decidir sin el usuario (para quién, qué problema, qué queda fuera) va en **un**
`AskUserQuestion` (máx. 4). El resto, en *Preguntas abiertas*.

## 4. Escribir la propuesta — máximo ~80 líneas

`.ai-mobile/proposals/prop-<slug>.md` (kebab-case, prefijo `prop-`: el hook lo exige):

```markdown
# <Título de la funcionalidad>

Estado: idea | planificada | aprobada | en espera | convertida → <plan> | descartada (<motivo>)
Fecha: <YYYY-MM-DD> · Base: main <sha corto>

## Necesidad
<qué problema resuelve, cómo se hace hoy>

## Propuesta
<qué haría, en 3-6 líneas, desde el punto de vista del usuario>

## Alcance
- Incluye: …
- No incluye: …

## Impacto
- Docs: <secciones de glosario, modelo, pantallas, reglas que cambiarían>
- Términos nuevos: <ES → código EN> (van al glosario antes de programar)
- Eventos: <nuevos, o existentes que cambian de forma → schemaVersion + upcaster>
- Proyecciones y tablas: <cuáles; versión de la base sí/no>
- Pantallas y rutas: <nuevas o tocadas>
- Dónde caería: `app/src/main/java/…/feature/<f>/…` (rutas completas: así `proposals` detecta cambios)

## Alternativas
1. <opción> — <a favor / en contra>
2. <opción> — <a favor / en contra>
Recomendación: <cuál y por qué>

## Riesgos y costos
- Datos del usuario: <qué dato nuevo, sensibilidad, si sale del teléfono>
- Integridad: <migración, replay, redondeo de dinero>
- Permisos y dependencias: <nuevos, por qué>
- Deuda: <puntos calientes de `am.mjs impact`>

## Esfuerzo
<S | M | L> — <por qué>
Tareas tentativas: <3-6 viñetas de alto nivel>
Entrega: un PR

## Preguntas abiertas
- …
```

- Cada hecho del *Impacto* y de *Riesgos* sale de un doc, del script o de una lectura verificada.
- **S** = una feature, pocos archivos, sin migración ni eventos nuevos; **L** = varias features,
  migración, eventos nuevos, permisos o seguridad; **M** = lo demás.
- Estado inicial `planificada` si está completa, `idea` si quedan preguntas que bloquean.

## 5. Cierre

Tres líneas: ruta, esfuerzo y recomendación, y qué falta decidir. Si el usuario la aprueba, cambia
`Estado:` a `aprobada`; para construirla, `/am-define-task .ai-mobile/proposals/prop-<slug>.md`.
