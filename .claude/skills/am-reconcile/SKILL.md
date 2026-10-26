---
name: am-reconcile
description: "App Android: cruzar varios planes y propuestas que tocan lo mismo. Un script ubica los cruces (rutas, tablas, eventos, features, símbolos, migraciones) con número de línea y solo esas líneas se leen para clasificar cada uno como contradicción, duplicación, dependencia, choque de merge o compatible. Escribe una sección Coordinación en cada documento. Costo: liviano-medio."
argument-hint: "--session <nombre> | [slug…]  (sin argumento: todos los planes y propuestas activos)"
disable-model-invocation: true
---

# Conciliar planes y propuestas que se cruzan

Cuando varias tareas o propuestas tocan lo mismo, las fallas aparecen en los bordes: dos decisiones
opuestas sobre el mismo evento o tabla, el mismo trabajo dos veces, una que necesita lo que otra crea,
dos ramas sobre el mismo archivo o dos migraciones que suben a la misma versión de la base. Se buscan
**sin releer los documentos enteros**: el script ubica los cruces y se leen solo esas líneas.

## 0. El grupo

Lo normal es conciliar una **sesión** (`.ai-mobile/sessions/<nombre>/`).
`node .ai-mobile/am.mjs session show <nombre>` muestra miembros, estado y notas. Si no hay sesión y el
usuario nombra varios documentos que van juntos, propone crearla
(`session new <nombre> --goal "…" <slug…>`). `session list` muestra los documentos sin sesión.

## 1. Cruces — una llamada

`node .ai-mobile/am.mjs overlap --session <nombre>` (o `overlap [slugs]`): cada par que se toca, por
peso, con cada cruce y la **línea** en cada documento, más avisos de migraciones en ambos y de ramas
distintas sobre los mismos archivos. Sin cruces → dilo en una línea y termina.

## 2. Leer solo lo que se cruza

Para cada par, en un mensaje: `Read` de cada documento con `offset` = línea del cruce − 3 y
`limit` ≈ 8, juntando cruces cercanos. **Nunca el documento entero**: si los rangos cubren casi todo,
el par es una sola funcionalidad (ver *duplicación*).

## 3. Clasificar cada cruce

| Clase | Señal | Qué se propone |
| --- | --- | --- |
| **contradicción** | mismo evento, tabla o regla con decisiones distintas (campos, reglas, quién lo emite) | elegir una; decide el usuario |
| **duplicación** | los dos planean construir lo mismo (mismo caso de uso, evento, proyección o componente) | quién lo hace; el otro lo reutiliza |
| **dependencia** | uno necesita lo que el otro crea (un evento, una proyección, una pantalla) | orden, o **una sola rama y PR** |
| **choque de merge** | ramas distintas sobre los mismos archivos, o dos migraciones a la misma versión | una sola rama y PR, o secuencia explícita (la versión la toma el que mergea después) |
| **compatible** | tocan lo mismo sin pisarse | se anota que se revisó |

Ante *dependencia* o *choque de merge*, la recomendación por defecto es **juntar en una rama y un PR**.

## 4. Decidir y registrar

- Muestra la tabla de cruces no compatibles (par · clase · qué · recomendación) y pregunta las
  contradicciones y la estrategia de ramas en **un** `AskUserQuestion` (máx. 4).
- Con las respuestas, agrega o actualiza en **cada** documento afectado, con `Edit`:

```markdown
## Coordinación
- con <otro-slug>: <clase> — <qué, en una línea> — <decisión o "pendiente"> (<fecha>)
```

  Si la decisión cambia el alcance de una tarea, modo actualización de `/am-define-task`. Si dos
  planes se juntan, el absorbido queda `Estado: descartada (absorbida por <slug>)` y sale de la sesión.
- En `session.md`, `## Coordinación`: la estrategia del **grupo**, una línea por decisión.
- El **orden de ejecución**, estructurado:
  `node .ai-mobile/am.mjs session order <nombre> --set "plan-a; plan-b + plan-c; plan-d"` (un paso por
  PR; `+` = misma rama y PR). Después `session order <nombre>` muestra el estado y el siguiente paso.
- Si una decisión cambia el modelo del proyecto (un evento, una tabla, una regla), también va al doc
  del proyecto que la registra (decisiones del modelo de datos).
- Cierre: cuántos cruces, cuántos requieren decisión y la estrategia de ramas/PR acordada.
