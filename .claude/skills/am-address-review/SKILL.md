---
name: am-address-review
description: "App Android: atender la revisión de tu PR — clasificar cada comentario (defecto, mejora, decisión de producto, está bien así) antes de tocar código, preguntar antes de revertir una decisión, y un solo commit de correcciones en la misma rama. Costo: medio."
argument-hint: "[comentarios pegados | número de PR]"
disable-model-invocation: true
---

# Atender la revisión de un PR

**Primero clasificar, después tocar código**: el error más caro es implementar un comentario que no
había que implementar. Las correcciones van a la **misma rama**, en un commit.

## 1. Comentarios y contexto — un mensaje, en paralelo

- Los comentarios: los pegados en `$ARGUMENTS`, o con `gh`: `gh pr view <n> --comments` y
  `gh api repos/<owner>/<repo>/pulls/<n>/comments` (owner/repo del remoto `origin`).
- `node .ai-mobile/am.mjs tasks` (el plan de la rama, con sus *Decisions*).
- `node .ai-mobile/am.mjs diff` (qué cambió la rama y las alertas).

No leas código todavía.

## 2. Clasificar cada comentario

| Clase | Qué es | Qué se hace |
| --- | --- | --- |
| **defecto** | el código está mal (bug, dato que se pierde, test que no prueba lo que dice) | se arregla |
| **mejora** | está bien pero puede estar mejor, dentro del alcance | se arregla si es chica; si no, tarea aparte |
| **decisión de producto** | cambia qué hace la funcionalidad | se pregunta; si se decide, se anota en las decisiones del doc del proyecto |
| **está bien así** | malentendido o ya resuelto | respuesta con la evidencia (`archivo:línea`, test, doc §sección) |

Un comentario que **revierte una decisión** del plan o de los docs del proyecto es siempre decisión de
producto. Lo que queda fuera del alcance va a una propuesta (`/am-plan-feature`) o a otra tarea.

Muestra la tabla (comentario resumido · clase · qué harías) y espera el visto bueno, con las preguntas
de producto en **un** `AskUserQuestion`.

## 3. Arreglar — solo lo aprobado

- Por cada defecto, primero el test que lo reproduce (RED por la razón correcta), luego el arreglo:
  `node .ai-mobile/am.mjs test <clases>`; al final `test --compile` y `fix`.
- Si el arreglo cambia un nombre, un evento, una tabla o una pantalla: su doc en el mismo commit.
- Si cambia el alcance, actualiza el plan (modo actualización de `/am-define-task`).

## 4. Cerrar

- `node .ai-mobile/am.mjs diff`: sin alertas nuevas.
- **Un** commit con todas las correcciones (`fix(<feature>): address review …`). Muestra el mensaje y
  espera aprobación; push solo si el usuario lo pide.
- Para cada comentario, la respuesta propuesta en una línea. Publicarlas solo con aprobación.
