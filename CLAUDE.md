<!-- ai-mobile:start -->
## Herramientas am-* (Al3xiel/AI-Mobile-Skills)

Guía de qué usar en cada caso: `/am-guide` o [`.ai-mobile/GUIA.md`](.ai-mobile/GUIA.md).

- Trabajo mecánico con `node .ai-mobile/am.mjs <modo>`: `diff` (medir el cambio y alertas), `test
  [--compile] [Clase…]` (Gradle, solo conteos y fallos), `fix`, `outline <archivo>` (y leer solo el
  rango), `docs <ruta>` (qué doc y sección leer), `migration <nombre>` (Room), `pr`.
- Antes de explorar código, la sección del doc del proyecto que lo cubre (`am.mjs docs`); en archivos
  largos, `outline`. Un término de dominio nuevo va primero al glosario.
- Planes en `.ai-mobile/work/plan-<slug>.md` y propuestas en `.ai-mobile/proposals/prop-<slug>.md`
  (locales, ignorados por git). Una feature es un PR.
- Si `am.mjs` falla o no imprime nada, es un fallo: dilo en una línea y sigue a mano.
<!-- ai-mobile:end -->
