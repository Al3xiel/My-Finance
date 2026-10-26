---
name: am-docs
description: "App Android: poner al día los docs del proyecto (glosario, modelo de datos, reglas de datos, pantallas, guía) con lo que el código ya hace, leyendo solo lo que cambió desde la última edición de cada doc. Mantiene el glosario como fuente de los nombres y el catálogo de eventos completo. Costo: medio una vez; ahorra exploración en cada tarea siguiente."
argument-hint: "[glossary | model | data | screens | guide | ruta…]  (sin argumento: los que el diff dejó atrás)"
disable-model-invocation: true
---

# Poner al día los docs del proyecto

Los docs del proyecto son la puerta de entrada de cada tarea: el glosario decide los nombres, el modelo
de datos decide eventos y tablas, las reglas de datos deciden qué se guarda y qué sale del teléfono. Si
mienten, cada tarea siguiente explora el código para enterarse. Un doc al día se paga una vez.

## 1. Estado — un mensaje, en paralelo

- `node .ai-mobile/am.mjs docs $ARGUMENTS` — qué doc cubre cada pieza, tamaño, y si quedó atrás.
- `node .ai-mobile/am.mjs diff` — si el motivo es la rama actual, sus alertas `docs-stale`,
  `glossary-missing` y `event-not-cataloged` dicen exactamente qué falta.
- `node .ai-mobile/am.mjs outline <doc>` de cada doc que se va a tocar.

**Refrescar**: `git log --oneline <último commit del doc>..HEAD -- app/src/main` y lee solo lo que esos
commits tocaron (`git show --stat`, `outline`, rangos). No releas el código entero.

## 2. Escribir

Respeta el formato de cada doc (sus tablas, sus IDs de regla, su encabezado de estado y fecha). Por doc:
- **Glosario**: cada término de dominio con su término ES, código EN, tipo y definición; cada evento en
  el catálogo con su significado; lo prohibido en *términos a evitar*. Un término nuevo se agrega aquí
  **antes** de programarlo.
- **Modelo de datos**: el payload de cada evento (`?` = opcional), las columnas de cada tabla de
  proyección, las reglas de cada proyector, y la decisión nueva en la tabla de decisiones con su fecha.
- **Reglas de datos**: dato nuevo con su sensibilidad y dónde vive; regla nueva con el siguiente ID de
  su serie (S*, E*, R*, C*, DG*).
- **Pantallas**: pantalla o ruta nueva en el inventario y en las rutas de navegación.

- Cada afirmación verificada en el código (`archivo:símbolo`) o en una decisión del usuario. Lo no
  confirmado va como `Pendiente: …`.
- Cambia el encabezado de estado del doc (versión y fecha) si el cambio es de fondo.
- Nada que se lea igual del código en un vistazo.

## 3. Cierre

Docs tocados, secciones, y qué quedó `Pendiente`. Sin commit sin aprobación
(`docs(<doc o feature>): …`, en inglés).
