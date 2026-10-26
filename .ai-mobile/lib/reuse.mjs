/**
 * Reutilización por nombre y código muerto en Kotlin: lo que un bloque copiado de 8+ líneas
 * (clones.mjs) no ve porque es corto, y lo que el diff agrega sin que nada lo use.
 *
 * Kotlin permite varias declaraciones de nivel superior por archivo: "redeclarado" es una clase,
 * función o valor público nuevo con el nombre de uno que ya existe en otro archivo; "muerto", algo
 * nuevo que ningún otro archivo nombra. Hilt, el manifest y Navigation cablean algunas piezas sin
 * nombrarlas en Kotlin (Activity, Application, módulos, workers): esas no cuentan como muertas.
 */
import { isTest, MODULE } from './android.mjs';

const TOP_DECL = /^(?:public\s+)?(?:(?:data|sealed|value|enum|abstract|open|inline|annotation|fun)\s+)*(class|interface|object|fun|val|typealias)\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?([A-Za-z_]\w*)/;
const PRIVATE = /^(private|internal)\s/;
/** Nombres de convención, declarados en muchos archivos a propósito. */
const CONVENTIONAL = new Set(['Companion', 'invoke', 'Preview', 'Content', 'Screen', 'UiState', 'UiAction']);
const conventional = (name) => name.length < 5 || CONVENTIONAL.has(name) || /Preview$/.test(name);
/** Entradas que el framework cablea sin que Kotlin las nombre. */
const ENTRYPOINT = /(Activity|Application|Module|Worker|Receiver|Service|Provider|Initializer)\.kt$/;
const isKt = (f) => f.endsWith('.kt') && f.startsWith(`${MODULE}/src/`);

function decls(text) {
  const out = [];
  text.split('\n').forEach((line, i) => {
    if (/^\s/.test(line) || PRIVATE.test(line)) return; // solo nivel superior y público
    const m = TOP_DECL.exec(line);
    if (m) out.push({ n: i + 1, name: m[2], kind: m[1] });
  });
  return out;
}

/** Índice del repo: nombre público de nivel superior → archivos (sin tests). */
export function buildIndex(corpus) {
  const exportsIdx = new Map();
  for (const { file, text } of corpus) {
    if (isTest(file) || !isKt(file)) continue;
    for (const d of decls(text)) (exportsIdx.get(d.name) ?? exportsIdx.set(d.name, new Set()).get(d.name)).add(file);
  }
  return { exportsIdx };
}

/** Algo que el diff declara y el repo ya tiene en otro archivo. */
export function findRedeclared({ file, added, index }) {
  if (isTest(file) || !isKt(file)) return [];
  const out = [];
  for (const { n, line } of added) {
    if (/^\s/.test(line) || PRIVATE.test(line)) continue;
    const m = TOP_DECL.exec(line);
    if (!m || conventional(m[2])) continue;
    const others = [...(index.exportsIdx.get(m[2]) ?? [])].filter((f) => f !== file).sort();
    if (others.length) out.push({ rule: 'redeclared-export', line: n, msg: `${m[2]} ya existe en ${others[0]}${others.length > 1 ? ` (+${others.length - 1})` : ''}: reutilízalo o nómbralo por lo que lo distingue` });
  }
  return out;
}

/**
 * Código muerto que agrega el diff: declaraciones públicas nuevas que ningún otro archivo nombra.
 * Que lo nombre un test también cuenta (lo prueba).
 */
export function findDeadCode({ file, added, isNew, corpus }) {
  if (isTest(file) || !isKt(file) || ENTRYPOINT.test(file)) return [];
  const others = corpus.filter((c) => c.file !== file && isKt(c.file));
  const own = corpus.find((c) => c.file === file)?.text ?? '';
  const out = [];
  for (const { n, line } of added) {
    if (/^\s/.test(line) || PRIVATE.test(line)) continue;
    const m = TOP_DECL.exec(line);
    if (!m || conventional(m[2])) continue;
    if ((own.match(new RegExp(`\\b${m[2]}\\b`, 'g')) || []).length > 1) continue;
    if (others.some((c) => new RegExp(`\\b${m[2]}\\b`).test(c.text))) continue;
    out.push({ rule: isNew ? 'dead-file' : 'dead-export', line: n, msg: `${m[2]} no lo nombra ningún otro archivo (ni un test): ¿falta cablearlo o sobra?` });
    if (isNew) break; // un archivo nuevo sin uso es una sola alerta
  }
  return out;
}
