---
name: am-guide
description: "App Android: qué skill am-* o comando de am.mjs usar en cada situación, según la guía de comandos y casos (.ai-mobile/GUIA.md). Lee solo la sección que aplica. Costo: liviano."
argument-hint: "<qué quieres hacer, o vacío para ver el índice>"
disable-model-invocation: true
effort: low
---

# Guía de comandos

La guía completa está en `.ai-mobile/GUIA.md`. No la leas entera:

1. `node .ai-mobile/am.mjs outline .ai-mobile/GUIA.md` (secciones con su rango de líneas).
2. Sin `$ARGUMENTS`: muestra las tablas *Skills* y *Script* (solo esos rangos) y la lista de casos.
3. Con una pregunta: lee solo el caso (o los dos) que la responden y contesta en pocas líneas con el
   comando exacto y el orden. Si ningún caso aplica, dilo y propone el más cercano.

No ejecutes nada: esta skill solo orienta.
