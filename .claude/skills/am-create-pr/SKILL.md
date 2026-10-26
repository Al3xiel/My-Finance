---
name: am-create-pr
description: "App Android: decide si la rama merece su propio PR o conviene juntarla, y abre el PR con los hechos que junta el script (versión de la base, eventos, permisos, dependencias, docs) sin releer código. Espera aprobación. Costo: liviano."
argument-hint: "[--base main]"
disable-model-invocation: true
effort: low
---

# Crear el PR

## 1. Hechos — una llamada

`node .ai-mobile/am.mjs pr`. No leas código: los hechos ya están ahí (commits, features, versión de la
base, eventos tocados, permisos nuevos, dependencias, docs tocados, otras ramas abiertas).

Paradas (pregunta antes de seguir): `PARADA` por cambios sin commitear, tareas abiertas en el plan,
`commits fuera de Conventional Commits` (propón reescribir el mensaje antes de publicar), `BASE DE
DATOS` sin migración y test en la rama, un evento `cambiado` sin upcaster, o `PERMISOS NUEVOS` (confirma
que cada uno lo pide una funcionalidad y que respeta las reglas de datos del proyecto).

## 2. ¿Merece su propio PR?

- **Solo docs o config local**: propón sumarlo a una rama abierta o al próximo PR con código.
- **Parte de una feature** cuyo resto sigue abierto: no; va en el PR de la feature completa.
- **Arreglo chico y hay otras ramas abiertas**: propón juntarlas (con aprobación) o esperar.
- PR propio si es la feature completa o si hace falta ya.

Pregunta en una línea qué prefiere, con tu recomendación. Si decide esperar, cierra ahí.

## 3. Cuerpo

```markdown
## What changes
<1-2 frases, del objective o de los commits; una viñeta por tarea si junta varias>

## How it was tested
- <clases> — n/n · compile ok · <instrumentados: emulador / no corridos>

## Risk
<versión de la base y migración · eventos nuevos o con schemaVersion nueva · permisos · dependencias · docs actualizados>
```

Título: Conventional Commit en inglés, una línea (`feat(movements): split an expense into parts`).
Sin atribución de IA.

## 4. Publicar

Muestra título y cuerpo y **espera un sí explícito**. Luego `git push -u origin <rama>` si no tiene
upstream, y:
- con `gh`: `gh pr create --base main --title "…" --body-file <tmp>`;
- sin `gh`: el enlace `compare` que imprimió el script, y el cuerpo listo para pegar.
