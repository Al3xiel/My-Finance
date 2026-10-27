/**
 * Perfil de finanzas-personales: app Android local (sin Internet) de ingresos y gastos con Event
 * Sourcing. Fuente: docs/01-guia-programacion.md, 02-reglas-datos.md, 03-glosario.md,
 * 04-modelo-datos.md y 05-pantallas-navegacion.md del proyecto.
 */
export const PROFILE = {
  name: 'finanzas-personales',
  base: 'main',
  appModule: 'app',
  /** Paquete propuesto en la guía (§4, H17). Si app/build.gradle.kts declara otro `namespace`, manda ese. */
  package: 'pe.gcasas.finanzas',
  /** Carpeta de las herramientas en el proyecto. */
  tool: '.ai-mobile',
};

/** Features de la guía (§4). `core/*` y `data/eventstore` son transversales. */
export const FEATURES = ['accounts', 'movements', 'categories', 'tags', 'people', 'pools', 'loans', 'funds', 'expectedincome', 'reports', 'privacy', 'backup'];

/** Docs del proyecto y qué cubre cada uno: `am.mjs docs` dice cuál leer y si quedó atrás del código. */
export const DOCS = {
  guide: { file: 'docs/01-guia-programacion.md', covers: 'arquitectura, convenciones, stack, pruebas' },
  data: { file: 'docs/02-reglas-datos.md', covers: 'privacidad, seguridad, integridad (E*, R*, C*, S*, DG*), validaciones' },
  glossary: { file: 'docs/03-glosario.md', covers: 'nombres de dominio EN↔ES y catálogo de eventos' },
  model: { file: 'docs/04-modelo-datos.md', covers: 'eventos y payloads, tablas de proyección, proyectores, decisiones D*' },
  screens: { file: 'docs/05-pantallas-navegacion.md', covers: 'pantallas, flujos, rutas Navigation 3, componentes' },
};

/** Qué doc corresponde a cada pieza del código. */
export function docsFor(path) {
  const out = new Set();
  if (/\/domain\/(model|event)|Event\.kt$|\/data\/eventstore\//.test(path)) { out.add('glossary'); out.add('model'); }
  if (/\/data\/(projection|eventstore)\/|Projector\.kt$|Entity\.kt$|Dao\.kt$/.test(path)) out.add('model');
  if (/\/ui\/|Screen\.kt$|Route\w*\.kt$|\/navigation\//.test(path)) out.add('screens');
  if (/\/core\/security\/|\/backup\/|AndroidManifest\.xml$/.test(path)) out.add('data');
  if (/\/domain\/(rules|usecase)\//.test(path)) out.add('data');
  return [...out];
}

/** Rutas que, además de las de Android, siempre llevan auditoría profunda. */
export const SENSITIVE = [
  [/\/data\/eventstore\//, 'registro de eventos (append-only, cadena de hashes)'],
  [/\/core\/money\//, 'dinero (Money, redondeo)'],
  [/\/feature\/backup\/|\/backup\//, 'respaldo / restauración / diagnóstico'],
  [/Projector\.kt$|\/data\/projection\//, 'proyectores (replay determinista)'],
];
export const SECURITY_SURFACE = ['registro de eventos (append-only, cadena de hashes)', 'respaldo / restauración / diagnóstico'];
