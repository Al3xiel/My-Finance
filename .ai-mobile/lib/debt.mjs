/**
 * Deuda estructural medible: lo que hace caro leer y cambiar el código, para una persona y para un
 * agente (un composable de 63 KB se paga en tokens cada vez que se toca: H13 de la guía).
 *
 * Dos usos, una definición:
 * - `am.mjs debt`: la foto del repo (línea base y puntos calientes) para priorizar qué pagar primero.
 * - las alertas de `am.mjs diff`: que ninguna tarea agregue deuda nueva sobre las líneas que escribe.
 *   Lo heredado no bloquea: se exige no empeorarlo.
 */
import { isTest, MODULE } from './android.mjs';

/** Guía §5 (H13): archivo ≤ ~300 líneas; una pantalla = Screen + Content + componentes. */
export const LARGE = 300;
export const WARN = 200;
/** Cuánto puede crecer un archivo ya grande sin extraer: un arreglo sí, una feature metida no. */
export const GROWTH_TOLERANCE = 25;

const CODE = new RegExp(`^${MODULE}/src/main/.*\\.kt$`);
export const GENERIC = /(Util|Utils|Helper|Helpers|Manager|Misc|Common)\.kt$|\/(utils?|helpers?)\//;
/** Acceso a la base fuera de data/: el resto pasa por un repositorio. */
export const DB_ACCESS = /\b(RoomDatabase|SupportSQLiteDatabase|SQLiteOpenHelper|rawQuery|execSQL|\w+Dao)\b/;
const DB_ALLOWED = /\/data\/|\/di\/|Module\.kt$/;

/** Dirección de dependencias (guía §4): ui → domain ← data; domain no depende de nadie. */
const LAYER_RULES = [
  { from: /\/domain\//, forbidden: /^import\s+[\w.]+\.(ui|data)\.[\w.]+$/, msg: 'domain/ no importa de ui/ ni de data/: el dominio no depende de nada' },
  { from: /\/ui\//, forbidden: /^import\s+[\w.]+\.data\.[\w.]+$/, msg: 'ui/ no importa de data/: la UI lee por repositorios y escribe por casos de uso' },
  { from: /\/data\//, forbidden: /^import\s+[\w.]+\.ui\.[\w.]+$/, msg: 'data/ no importa de ui/' },
];

export const isGenericName = (file) => !isTest(file) && CODE.test(file) && GENERIC.test(file);

export function layerViolations(file, lines) {
  if (!CODE.test(file)) return [];
  const out = [];
  for (const r of LAYER_RULES.filter((x) => x.from.test(file))) {
    for (const { n, line } of lines) if (r.forbidden.test(line.trim())) out.push({ line: n, msg: r.msg });
  }
  return out;
}

export function debtAlerts({ file, added, size, before, isNew }) {
  if (isTest(file) || !CODE.test(file)) return [];
  const out = [];
  if (isNew && isGenericName(file)) out.push({ rule: 'generic-file-name', line: 1, msg: 'nombre genérico (Utils/Helper/Manager): nómbralo por lo que hace' });
  const grew = size - (before ?? 0);
  if (size > LARGE && (isNew || grew > GROWTH_TOLERANCE)) {
    out.push({ rule: 'large-file-growth', line: 1, msg: isNew
      ? `archivo nuevo de ${size} líneas (> ${LARGE}, H13): Screen + Content + componentes, o una clase por responsabilidad`
      : `crece ${grew} línea(s) un archivo de ${size} (> ${LARGE}): lo nuevo va en su propio archivo` });
  } else if (isNew && size > WARN) {
    out.push({ rule: 'large-new-file', line: 1, msg: `archivo nuevo de ${size} líneas (> ${WARN}): ¿una sola responsabilidad?` });
  }
  if (!DB_ALLOWED.test(file)) {
    for (const { n, line } of added) {
      if (/^import\b/.test(line) && DB_ACCESS.test(line)) { out.push({ rule: 'db-access-outside-data', line: n, msg: 'Room/SQLite fuera de data/: pasa por un XxxRepository' }); break; }
    }
  }
  for (const v of layerViolations(file, added)) out.push({ rule: 'layer-violation', ...v });
  return out;
}

export function measure(corpus) {
  const files = corpus.filter((c) => !isTest(c.file) && CODE.test(c.file));
  const lines = (c) => c.text.split('\n').length;
  const m = {
    files: files.length,
    lines: files.reduce((s, c) => s + lines(c), 0),
    large: files.filter((c) => lines(c) > LARGE).map((c) => ({ file: c.file, lines: lines(c) })).sort((a, b) => b.lines - a.lines),
    medium: files.filter((c) => lines(c) > WARN && lines(c) <= LARGE).length,
    sizable: files.filter((c) => lines(c) > WARN).map((c) => ({ file: c.file, lines: lines(c) })),
    generic: files.filter((c) => isGenericName(c.file)).map((c) => c.file),
    dbOutside: files.filter((c) => !DB_ALLOWED.test(c.file) && c.text.split('\n').some((l) => /^import\b/.test(l) && DB_ACCESS.test(l))).map((c) => c.file),
    repositories: files.filter((c) => /Repository(Impl)?\.kt$/.test(c.file)).length,
    layer: files.flatMap((c) => layerViolations(c.file, c.text.split('\n').map((line, i) => ({ n: i + 1, line }))).map((v) => `${c.file}:${v.line}`)),
  };
  m.inLarge = m.large.reduce((s, x) => s + x.lines, 0);
  return m;
}
