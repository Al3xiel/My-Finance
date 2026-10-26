#!/usr/bin/env node
/**
 * El trabajo mecánico de las skills am-*, hecho por código, para una app Android nativa.
 *
 * Todo lo que un modelo haría con varias llamadas y mucha salida (medir un diff, correr Gradle y
 * leer miles de líneas, formatear, ubicar una función en un composable largo, preparar una
 * migración de Room) se hace aquí en una sola llamada que devuelve un resumen corto. El modelo
 * gasta tokens en criterio; el script, en lo determinista.
 *
 *   node .ai-mobile/am.mjs diff      [--base main] [--depth lite|deep]   medir el cambio y elegir la profundidad de auditoría
 *   node .ai-mobile/am.mjs test      [--compile] [Clase…]                tests JVM o instrumentados: solo conteos y fallos
 *   node .ai-mobile/am.mjs fix                                           ktlint / detekt (lo que el proyecto tenga) y lo que queda
 *   node .ai-mobile/am.mjs outline   <archivo>                           símbolos con su rango de líneas, para leer solo uno
 *   node .ai-mobile/am.mjs migration <nombre>                            Migration<n>To<n+1> de Room y su test, con plantilla
 *   node .ai-mobile/am.mjs pr        [--base main]                       hechos para el cuerpo del PR
 *   node .ai-mobile/am.mjs docs      [ruta…]                             qué doc del proyecto leer antes de explorar, y si quedó atrás
 *   node .ai-mobile/am.mjs naming    <ruta>…                             ¿cumple la ruta nueva el estándar de estructura?
 *   node .ai-mobile/am.mjs gate      [plan]                              ¿cabe el plan en /am-quick?
 *   node .ai-mobile/am.mjs tasks     [plan]                              el plan sin lo ya cerrado
 *   node .ai-mobile/am.mjs debt      [--save] [--check] [--days 180]     deuda estructural, línea base y puntos calientes
 *   node .ai-mobile/am.mjs impact    <ruta|carpeta>…                     costo de tocar esas rutas: tamaño, deuda, superficie, tests
 *   node .ai-mobile/am.mjs session   new|add|remove|show|order|close|list …  agrupar planes y propuestas en sesiones
 *   node .ai-mobile/am.mjs convert   <prop-slug> [plan-slug]             tras /am-define-task: propuesta → plan en sesiones y referencias
 *   node .ai-mobile/am.mjs overlap   [doc…] [--session s]                dónde se cruzan planes y propuestas, con la línea de cada uno
 *   node .ai-mobile/am.mjs proposals [--status aprobada]                 propuestas de funcionalidades y si quedaron desactualizadas
 *   node .ai-mobile/am.mjs ledger    add <entradas.json> | report        registro de auditorías: precisión de reglas y qué promover
 *   node .ai-mobile/am.mjs backtest  [--last 30] [--labels <tsv>]        las reglas sobre merges ya hechos
 *   node .ai-mobile/am.mjs exec      --b64 <comando>                     corre una tarea de Gradle y filtra su salida (lo usa el hook)
 *
 * Planes en `.ai-mobile/work/plan-<slug>.md` y propuestas en `.ai-mobile/proposals/prop-<slug>.md`
 * (ignorados por git): son la memoria de trabajo de una rama, no documentación del proyecto.
 *
 * Nunca instala la app ni toca un dispositivo salvo para los tests instrumentados que se le piden.
 */
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync, mkdirSync, rmSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { findClones } from './lib/clones.mjs';
import { fileAlerts, changeAlerts, SECURITY_RULES } from './lib/rules.mjs';
import { buildIndex, findRedeclared, findDeadCode } from './lib/reuse.mjs';
import { debtAlerts, measure, LARGE } from './lib/debt.mjs';
import { checkNewPath } from './lib/naming.mjs';
import { PROFILE_DOCS, PROFILE_DOCS_FOR, PROFILE_FEATURES } from './lib/profile.mjs';
import {
  PROFILE, MODULE, SRC_ROOT, SCHEMAS, MANIFEST, LIMITS, QUICK, LARGE_FILE, SENSITIVE, SECURITY_SURFACE, IGNORED,
  SAFE_EXEC, packageOf, isSource, isTest, featuresOf, relatedTests as stackTests, testCommands, typecheckCommand,
  fixCommands, toolOf, databaseVersion, compareUrl,
} from './lib/android.mjs';

const ROOT = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
const TOOL = PROFILE.tool ?? '.ai-mobile';
const WORK = `${TOOL}/work`;
const MAX_FAIL_LINES = 80;
/** Con un solo módulo, "toca dos paquetes" no aplica: el umbral de ambos queda en el de líneas. */
const BOTH_LINES = LIMITS.lines;
/** Flags sin valor: lo que les sigue es un argumento posicional. */
const BOOLEAN_FLAGS = new Set(['compile', 'json', 'save', 'check', 'dry-run', 'keep', 'force']);
/** Secciones del plan que una sesión necesita; el resto se busca con Grep si hace falta. */
const KEEP_SECTIONS = /^(objective|scope|scope check|constraints|decisions)\b/i;

// ── diff ────────────────────────────────────────────────────────────────────────────────
function cmdDiff() {
  const base = args.base || PROFILE.base || 'main';
  const mergeBase = git(['merge-base', base, 'HEAD']).trim();
  const branch = currentBranch();
  const files = changedFiles(base);

  const source = [...files].filter(([p]) => isSource(p));
  const lines = source.reduce((s, [, v]) => s + v.add + v.del, 0);
  const packages = packagesOf([...files.keys()]);
  const sensitive = sensitiveHits([...files.keys()]);
  const large = [...files.keys()].filter((p) => p.endsWith('.kt') && lineCount(p) > LARGE_FILE).map((p) => `${p} (${lineCount(p)} líneas)`);

  const reasons = [];
  if (lines > LIMITS.lines) reasons.push(`${lines} líneas de código > ${LIMITS.lines}`);
  if (source.length > LIMITS.files) reasons.push(`${source.length} archivos de código > ${LIMITS.files}`);
  for (const s of sensitive) reasons.push(`ruta sensible: ${s}`);
  let depth = reasons.length ? 'deep' : 'lite';
  if (args.depth === 'lite' || args.depth === 'deep') { depth = args.depth; reasons.unshift(`forzado: ${args.depth}`); }

  const out = [`rama: ${branch || '(detached)'}   base: ${base} (${mergeBase.slice(0, 7)})`];
  if (branch && branch === base.replace(/^origin\//, '')) out.push(`aviso: estás en ${branch}; solo se mide lo no commiteado. Crea una rama (feat/<slug>) antes de trabajar.`);
  out.push(`código: ${source.length} archivo(s), ${lines} líneas (+/-)   total con tests/docs: ${files.size} archivo(s)`);
  out.push(`profundidad: ${depth}${reasons.length ? ' — ' + reasons.join('; ') : ' — cambio chico y sin rutas sensibles'}`);
  const plan = planForBranch(false);
  if (plan) out.push(planSummary(plan));
  if (large.length) out.push(`archivos grandes (outline + lectura por rango):\n  ${large.join('\n  ')}`);

  const tests = relatedTests([...files.keys()]);
  out.push(`tests relacionados: ${tests.unit.length} JVM, ${tests.instrumented.length} instrumentados → node ${TOOL}/am.mjs test --compile`);
  if (tests.missing.length) out.push(`código sin test propio (casos de uso, reglas, proyectores, ViewModels, migraciones):\n  ${tests.missing.slice(0, 15).join('\n  ')}`);

  const alerts = mechanicalAlerts(mergeBase, files);
  const securityScan = suggestSecurityScan({ lines, files: source.length, sensitive, alerts });
  if (args.json) { console.log(JSON.stringify({ depth, reasons, lines, files: source.length, packages, sensitive, alerts, securityScan })); return; }
  if (securityScan) out.push(`revisión de seguridad recomendada (${securityScan.tier}) — ${securityScan.why}: /security-review (solo lo commiteado). Usa tokens: pide confirmación.`);
  out.push(alerts.length
    ? `alertas mecánicas (${alerts.length}):\n${alerts.map((a) => `  [${a.rule}] ${a.file}${a.line ? `:${a.line}` : ''} ${a.msg}`).join('\n')}`
    : 'alertas mecánicas: ninguna');
  out.push('archivos de código:');
  for (const [p, v] of source) out.push(`  ${p}  +${v.add} -${v.del}`);
  console.log(out.join('\n'));
}

function suggestSecurityScan({ lines, files, sensitive, alerts }) {
  const surface = sensitive.filter((s) => SECURITY_SURFACE.has(s));
  const holes = [...new Set(alerts.filter((a) => SECURITY_RULES.has(a.rule)).map((a) => a.rule))];
  if (!surface.length && !holes.length) return null;
  const tier = files > 25 || lines > 1500 ? 'high' : lines > 400 ? 'medium' : 'low';
  return { tier, why: [...surface.map((s) => `toca ${s}`), ...holes.map((h) => `alerta ${h}`)].join(', ') };
}

/** path → { add, del, isNew } contra el merge-base, más los archivos nuevos sin trackear. */
function changedFiles(base) {
  const mergeBase = git(['merge-base', base, 'HEAD']).trim();
  const files = new Map();
  const created = new Set(git(['diff', '--name-only', '--diff-filter=A', mergeBase]).split('\n').filter(Boolean).map(norm));
  for (const line of git(['diff', '--numstat', mergeBase]).split('\n').filter(Boolean)) {
    const [a, d, p] = line.split('\t');
    if (!IGNORED.test(norm(p))) files.set(norm(p), { add: num(a), del: num(d), isNew: created.has(norm(p)) });
  }
  for (const p of git(['ls-files', '--others', '--exclude-standard']).split('\n').filter(Boolean)) {
    const n = norm(p);
    if (!IGNORED.test(n) && !files.has(n)) files.set(n, { add: lineCount(n), del: 0, isNew: true });
  }
  return files;
}

const tracked = () => git(['ls-files']).split('\n').filter(Boolean).map(norm);
const relatedTests = (paths) => stackTests(paths, tracked(), (p) => existsSync(join(ROOT, p)));

/**
 * El piso mecánico de la auditoría: reglas de Android y del proyecto (lib/rules.mjs y el perfil),
 * deuda, estructura, duplicados y código muerto. El modelo confirma o descarta; no las busca él.
 */
function mechanicalAlerts(mergeBase, files) {
  const alerts = [];
  const push = (file, a) => alerts.push({ file, ...a });
  const targets = [];
  let corpus = null;
  const getCorpus = () => (corpus ??= loadCorpus());
  const cache = new Map();
  const added = (p) => cache.get(p) ?? cache.set(p, existsSync(join(ROOT, p)) ? addedLines(mergeBase, p) : []).get(p);
  const removed = (p) => removedLines(mergeBase, p);

  for (const [p, info] of files) {
    if (!existsSync(join(ROOT, p)) || !/\.(kt|kts|gradle|toml|xml)$/.test(p)) continue;
    const lines = added(p);
    if (!lines.length) continue;
    const text = readFileSync(join(ROOT, p), 'utf8');
    for (const a of fileAlerts({ file: p, text, added: lines, isNew: info.isNew })) push(p, a);
    if (isTest(p) || !p.endsWith('.kt')) continue;
    targets.push({ file: p, text, addedNums: new Set(lines.map((a) => a.n)) });
    const before = info.isNew ? 0 : (git(['show', `${mergeBase}:${p}`], true) ?? '').split('\n').length;
    for (const a of debtAlerts({ file: p, added: lines, size: text.split('\n').length, before, isNew: info.isNew })) push(p, a);
    if (info.isNew && !isException(p)) {
      const problem = checkNewPath(p, (dir) => git(['ls-tree', '-r', '--name-only', mergeBase, '--', dir]).split('\n').filter(Boolean));
      if (problem) push(p, { line: 1, ...problem });
    }
    for (const a of findRedeclared({ file: p, added: lines, index: buildIndex(getCorpus()) })) push(p, a);
    for (const a of findDeadCode({ file: p, added: lines, isNew: info.isNew, corpus: getCorpus() })) push(p, a);
  }

  const read = (p) => (existsSync(join(ROOT, p)) ? readFileSync(join(ROOT, p), 'utf8') : null);
  for (const a of changeAlerts({ files, added, removed, read, tracked: tracked() })) alerts.push(a);

  // Regla de tres: el bloque nuevo ya existe en 2+ lugares. Dos copias se toleran.
  if (targets.length) {
    for (const c of findClones({ targets, corpus: getCorpus() })) {
      if (c.verdict !== 'extract') continue;
      const where = c.locations.filter((l) => !(l.file === c.file && l.start === c.start)).slice(0, 3).map((l) => `${l.file}:${l.start}`).join(', ');
      push(c.file, { rule: 'duplicate-code', line: c.start, msg: `bloque ${c.kind === 'exact' ? 'idéntico' : 'con nombres cambiados'} (líneas ${c.start}-${c.end}) con ${c.copies} copias: ${where} — extraerlo` });
    }
  }
  return alerts;
}

/** Rutas aceptadas a propósito fuera del estándar, con su motivo en `.ai-mobile/naming-exceptions.txt`. */
function isException(p) {
  const f = join(ROOT, TOOL, 'naming-exceptions.txt');
  return existsSync(f) && readFileSync(f, 'utf8').split('\n').map((l) => l.replace(/#.*/, '').trim()).includes(p);
}

/** Todo el Kotlin del proyecto (trackeado o nuevo) para comparar duplicados y referencias. */
function loadCorpus() {
  return git(['ls-files', '--cached', '--others', '--exclude-standard']).split('\n')
    .map(norm).filter((f) => /\/src\/.*\.kt$/.test(f) && !IGNORED.test(f) && existsSync(join(ROOT, f)))
    .map((f) => ({ file: f, text: readFileSync(join(ROOT, f), 'utf8') }));
}

function addedLines(mergeBase, p) {
  if (git(['ls-files', '--error-unmatch', p], true) === null) {
    return readFileSync(join(ROOT, p), 'utf8').split('\n').map((line, i) => ({ n: i + 1, line }));
  }
  const out = [];
  let n = 0;
  for (const line of git(['diff', '-U0', mergeBase, '--', p]).split('\n')) {
    const h = line.match(/^@@ -\d+(?:,\d+)? \+(\d+)/);
    if (h) { n = Number(h[1]); continue; }
    if (line.startsWith('+') && !line.startsWith('+++')) out.push({ n: n++, line: line.slice(1) });
    else if (!line.startsWith('-')) n++;
  }
  return out;
}

function removedLines(mergeBase, p) {
  return (git(['diff', '-U0', mergeBase, '--', p], true) ?? '').split('\n').filter((l) => l.startsWith('-') && !l.startsWith('---')).map((l) => l.slice(1));
}

// ── test / exec ─────────────────────────────────────────────────────────────────────────
/**
 * Tests dados (nombres de clase o rutas) o los relacionados con el diff. `--compile` además compila
 * Kotlin + KSP, que es lo primero que rompe un cambio en Room o Hilt.
 */
function cmdTest(given) {
  let t;
  if (given.length) {
    t = { unit: [], instrumented: [] };
    for (const g of given.map(norm)) (g.includes('/androidTest/') ? t.instrumented : t.unit).push(g.split('/').pop().replace(/\.kt$/, ''));
  } else t = relatedTests([...changedFiles(args.base || PROFILE.base || 'main').keys()]);

  const out = [];
  let failed = false;
  if (args.compile) {
    const r = run(ROOT, typecheckCommand().argv);
    failed ||= r.status !== 0;
    out.push(...summarize('gradle', r).map((l, i) => (i === 0 ? l.replace('gradle:', 'compilación:') : l)));
  }
  if (!failed) {
    for (const c of testCommands(t)) {
      if (c.note) out.push(`nota: ${c.note}`);
      const r = run(ROOT, c.argv);
      failed ||= r.status !== 0;
      out.push(...summarize('gradle', r));
    }
  }
  if (!out.length) out.push('no hay tests que correr (pasa clases, o no hay tests relacionados con el diff)');
  console.log(out.join('\n'));
  process.exit(failed ? 1 : 0);
}

/** Corre una tarea de Gradle (la reescribe el hook) y devuelve su salida filtrada y su exit code. */
function cmdExec() {
  const command = args.b64 ? Buffer.from(args.b64, 'base64').toString('utf8') : args._.join(' ');
  // Solo test/compilación/lint, sin encadenar nada: `exec` corre sin preguntar.
  if (!SAFE_EXEC.test(command)) { console.error(`exec solo corre gradlew test|connected…AndroidTest|compile…Kotlin|lint|ktlint|detekt: ${command}`); process.exit(2); }
  const shell = process.platform === 'win32' ? 'bash' : '/bin/sh';
  const r = spawnSync(command, { cwd: process.cwd(), shell, encoding: 'utf8', maxBuffer: 256 << 20, env: quietEnv() });
  const res = { status: r.status ?? 1, text: stripAnsi(`${r.stdout || ''}\n${r.stderr || ''}`) };
  console.log([`$ ${command}  (salida filtrada por ${TOOL}; agrega | cat para verla entera)`, ...summarize(toolOf(command), res)].join('\n'));
  process.exit(res.status);
}

/** Lo que importa de Gradle: compilación, tests y lint, recortado a los fallos. */
function summarize(tool, r) {
  const lines = r.text.split('\n');
  const ok = r.status === 0;
  if (tool !== 'gradle') {
    const important = lines.filter((l) => /error|fail|✖|×|warn/i.test(l)).slice(0, 30);
    return [`exit ${r.status}`, ...[...new Set([...important, ...lines.filter((l) => l.trim()).slice(-15)])].slice(0, 45).map((l) => '  ' + l.trimEnd())];
  }
  const compile = lines.filter((l) => /^e: /.test(l)).map((l) => '  ' + l.replace(/^e: (file:\/\/\/?)?/, '').replace(/^.*?\/src\//, 'src/')).slice(0, 20);
  const counts = lines.find((l) => /\d+ tests? completed/.test(l))?.trim();
  const failedTests = [];
  lines.forEach((l, i) => {
    if (/ > .+ FAILED$/.test(l)) failedTests.push('  ' + l.trim(), ...lines.slice(i + 1, i + 4).filter((x) => x.trim() && !/^\s+at /.test(x)).map((x) => '    ' + x.trim()));
  });
  const lint = lines.filter((l) => /\.kt:\d+:\d+/.test(l) && !/^e: /.test(l)).slice(0, 30).map((l) => '  ' + l.replace(/^.*?\/src\//, 'src/').trim());
  if (ok) return [`gradle: ${counts ?? 'ok'}`];
  if (/No connected devices|No online devices/.test(r.text)) return ['gradle: no hay emulador ni dispositivo conectado (adb devices) para los tests instrumentados'];
  if (compile.length) return [`gradle: no compila (${compile.length} error(es))`, ...compile];
  if (failedTests.length) return [`gradle: ${counts ?? 'tests fallidos'}`, ...failedTests.slice(0, MAX_FAIL_LINES)];
  if (lint.length) return [`gradle: ${lint.length} problema(s) de estilo`, ...lint];
  const wrong = lines.findIndex((l) => /What went wrong:/.test(l));
  return [`gradle: exit ${r.status}`, ...(wrong >= 0 ? lines.slice(wrong + 1, wrong + 8) : lines.filter((l) => l.trim()).slice(-12)).map((l) => '  ' + l.trimEnd())];
}

// ── fix ─────────────────────────────────────────────────────────────────────────────────
function cmdFix() {
  const out = [];
  let failed = false;
  for (const c of fixCommands(ROOT)) {
    if (c.note) out.push(`nota: ${c.note}`);
    const r = run(ROOT, c.argv);
    failed ||= r.status !== 0;
    out.push(...summarize('gradle', r));
  }
  console.log(out.join('\n'));
  process.exit(failed ? 1 : 0);
}

// ── outline ─────────────────────────────────────────────────────────────────────────────
/** Mapa de un archivo: cada símbolo con su rango. Para leer 40 líneas en vez del archivo entero. */
function cmdOutline(file) {
  if (!file || !existsSync(join(ROOT, file))) { console.error(`no existe: ${file}`); process.exit(2); }
  const lines = readFileSync(join(ROOT, file), 'utf8').split('\n');
  if (file.endsWith('.md')) {
    const heads = lines.map((l, i) => ({ i, m: l.match(/^(#{1,4})\s+(.+)/) })).filter((h) => h.m);
    console.log([`${file}: ${lines.length} líneas, ${heads.length} secciones — Read con offset=<inicio> limit=<fin-inicio+1>`,
      ...heads.map((h, k) => `  ${String(h.i + 1).padStart(5)}-${String(heads[k + 1]?.i ?? lines.length).padEnd(5)} ${'  '.repeat(h.m[1].length - 1)}${h.m[2]}`)].join('\n'));
    return;
  }
  const syms = [];
  const MODS = '(?:(?:public|private|internal|protected|override|open|abstract|suspend|inline|operator|data|sealed|value|enum|annotation|inner|companion|const|lateinit)\\s+)*';
  lines.forEach((line, i) => {
    let m;
    const composable = /@Composable/.test(lines[i - 1] ?? '') || /@Composable/.test(lines[i - 2] ?? '');
    if ((m = line.match(new RegExp(`^(\\s{0,4})${MODS}(class|interface|object)\\s+(\\w+)`)))) syms.push({ i, kind: m[2] === 'class' ? 'class' : m[2], name: m[3], depth: m[1].length });
    else if ((m = line.match(new RegExp(`^(\\s{0,4})${MODS}fun\\s+(?:<[^>]+>\\s*)?(?:[\\w.]+\\.)?(\\w+)\\s*\\(`)))) syms.push({ i, kind: composable ? 'compose' : 'fun', name: m[2], depth: m[1].length });
    else if ((m = line.match(/^\s{4}(?:private\s+|override\s+)*val\s+(\w+)\s*(?::\s*[\w<>?, ]+)?\s*=\s*(MutableStateFlow|\w+\.stateIn|MutableSharedFlow)/))) syms.push({ i, kind: 'state', name: m[1], depth: 4 });
  });
  const out = [`${file}: ${lines.length} líneas, ${syms.length} símbolos — Read con offset=<inicio> limit=<fin-inicio+1>`];
  syms.forEach((s, k) => {
    const end = (syms.slice(k + 1).find((x) => x.depth <= s.depth)?.i ?? lines.length) - 1;
    out.push(`  ${String(s.i + 1).padStart(5)}-${String(end + 1).padEnd(5)} ${s.kind.padEnd(8)} ${s.depth ? '  ' : ''}${s.name}`);
  });
  if (out.length > 201) { out.splice(201); out.push('  … (más de 200 símbolos; usa Grep -n para el que buscas)'); }
  console.log(out.join('\n'));
}

// ── migration ───────────────────────────────────────────────────────────────────────────
/**
 * La siguiente migración de Room con su test. El SQL se escribe a mano; la plantilla deja escritas
 * las reglas que más se rompen. Nunca `fallbackToDestructiveMigration` (borra los datos del usuario).
 */
function cmdMigration(name) {
  if (!name || !/^[a-z0-9_]+$/.test(name)) { console.error('uso: migration <nombre_en_snake_case>'); process.exit(2); }
  const read = (p) => (existsSync(join(ROOT, p)) ? readFileSync(join(ROOT, p), 'utf8') : null);
  const db = databaseVersion(tracked().filter((f) => f.startsWith(`${MODULE}/src/main/`)), read);
  if (!db) { console.error('no hay ninguna clase @Database todavía: la versión 1 no necesita migración'); process.exit(2); }
  const from = db.version, to = from + 1;
  const build = ['gradle/libs.versions.toml', `${MODULE}/build.gradle.kts`].map(read).join('\n');
  const room3 = /room3/.test(build);
  const pkgDir = `${dirname(db.file)}/migration`;
  const pkg = pkgDir.replace(`${MODULE}/src/main/java/`, '').replace(/\//g, '.');
  const target = `${pkgDir}/Migration${from}To${to}.kt`;
  if (existsSync(join(ROOT, target)) && !args.force) { console.error(`ya existe ${target}`); process.exit(2); }
  const roomPkg = room3 ? 'androidx.room3' : 'androidx.room';
  mkdirSync(join(ROOT, pkgDir), { recursive: true });
  writeFileSync(join(ROOT, target), [
    `package ${pkg}`, '',
    `import ${roomPkg}.migration.Migration`,
    'import androidx.sqlite.SQLiteConnection',
    'import androidx.sqlite.execSQL', '',
    `/** ${name.replace(/_/g, ' ')} (v${from} → v${to}). */`,
    `val MIGRATION_${from}_${to} = object : Migration(${from}, ${to}) {`,
    '    override fun migrate(connection: SQLiteConnection) {',
    '        // Reglas: columna NOT NULL nueva con DEFAULT; dinero en INTEGER (céntimos), nunca REAL;',
    '        // enums como TEXT con el nombre; fechas del hecho como TEXT ISO-8601.',
    '        // connection.execSQL("ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …")',
    '    }',
    '}', '',
  ].join('\n'));
  const testDir = pkgDir.replace('/src/main/', '/src/androidTest/');
  const testFile = `${testDir}/Migration${from}To${to}Test.kt`;
  if (!existsSync(join(ROOT, testFile))) {
    mkdirSync(join(ROOT, testDir), { recursive: true });
    writeFileSync(join(ROOT, testFile), [
      `package ${pkg}`, '',
      `import ${roomPkg}.testing.MigrationTestHelper`,
      'import androidx.test.ext.junit.runners.AndroidJUnit4',
      'import androidx.test.platform.app.InstrumentationRegistry',
      'import org.junit.Rule',
      'import org.junit.Test',
      'import org.junit.runner.RunWith', '',
      '@RunWith(AndroidJUnit4::class)',
      `class Migration${from}To${to}Test {`,
      '    @get:Rule',
      `    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ${db.name}::class.java)`, '',
      '    @Test',
      `    fun migrates${from}To${to}KeepingData() {`,
      `        // 1. helper.createDatabase(DB_NAME, ${from}) e insertar filas de la versión ${from}`,
      `        // 2. helper.runMigrationsAndValidate(DB_NAME, ${to}, true, MIGRATION_${from}_${to})`,
      '        // 3. comprobar que las filas siguen y los valores por defecto son los esperados',
      '    }',
      '}', '',
    ].join('\n'));
  }
  console.log([
    `creadas: ${target} y ${testFile} (plantillas; verifica los imports con tu versión de Room${room3 ? ' 3' : ''})`,
    `falta: version = ${to} en @Database de ${db.file.split('/').pop()}, .addMigrations(MIGRATION_${from}_${to}) en el módulo que construye la base,`,
    `       y que el esquema ${SCHEMAS}…/${to}.json se genere al compilar (exportSchema = true, room.schemaLocation)`,
    `prueba: node ${TOOL}/am.mjs test ${testFile}  (instrumentado: emulador o dispositivo)`,
  ].join('\n'));
}

// ── pr ──────────────────────────────────────────────────────────────────────────────────
function cmdPr() {
  const base = args.base || PROFILE.base || 'main';
  const mergeBase = git(['merge-base', base, 'HEAD']).trim();
  const branch = currentBranch();
  const commits = git(['log', '--no-merges', '--format=%s', `${mergeBase}..HEAD`]).split('\n').filter(Boolean);
  const files = changedFiles(base);
  const paths = [...files.keys()];
  const uncommitted = git(['status', '--porcelain']).split('\n').filter((l) => l && !IGNORED.test(l.slice(3)));
  const read = (p) => (existsSync(join(ROOT, p)) ? readFileSync(join(ROOT, p), 'utf8') : null);
  const events = paths.filter((p) => p.endsWith('.kt') && !isTest(p)).flatMap((p) => {
    const names = [...(read(p) ?? '').matchAll(/class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*DomainEvent\b/g)].map((m) => m[1]);
    const before = files.get(p).isNew ? '' : git(['show', `${mergeBase}:${p}`], true) ?? '';
    return names.map((e) => `  ${e}${new RegExp(`class\\s+${e}\\b`).test(before) ? ' (cambiado: ¿schemaVersion y upcaster?)' : ' (nuevo: catálogo del glosario y payload en el modelo)'}`);
  });
  const db = databaseVersion(tracked().filter((f) => f.startsWith(`${MODULE}/src/main/`)), read);
  const dbBumped = db && files.has(db.file) && addedLines(mergeBase, db.file).some(({ line }) => /version\s*=/.test(line));
  const permissions = files.has(MANIFEST) ? addedLines(mergeBase, MANIFEST).map(({ line }) => line.match(/<uses-permission[^>]*android:name="([^"]+)"/)?.[1]).filter(Boolean) : [];
  const deps = paths.filter((p) => /libs\.versions\.toml$|build\.gradle(\.kts)?$/.test(p)).flatMap((p) => addedLines(mergeBase, p).map(({ line }) => line.trim()).filter((l) => /=\s*\{|implementation|ksp\(|version\s*=/.test(l))).slice(0, 12);
  const features = [...new Set(paths.filter(isSource).flatMap((p) => featuresOf(p, ROOT)))];
  const upstream = git(['rev-parse', '--abbrev-ref', '@{u}'], true)?.trim();
  const remote = git(['remote', 'get-url', 'origin'], true)?.trim();
  const recent = Date.now() / 1000 - 14 * 86400;
  const open = git(['for-each-ref', '--no-merged', base, '--sort=-committerdate', '--format=%(refname:short)\t%(committerdate:relative)\t%(committerdate:unix)', 'refs/heads', 'refs/remotes/origin'], true)
    ?.split('\n').filter(Boolean).map((l) => l.split('\t'))
    .filter(([ref, , ts]) => Number(ts) > recent && ref !== branch && ref !== `origin/${branch}` && ref !== 'origin/HEAD')
    .filter(([ref], _, all) => !ref.startsWith('origin/') || !all.some(([r]) => r === ref.slice(7))) ?? [];
  const plan = planForBranch(false);
  const objective = plan ? (readFileSync(join(ROOT, plan), 'utf8').match(/^## Objective\s*\n+([^\n#]+)/m)?.[1]?.trim()) : null;
  const badCommits = commits.filter((c) => !/^(feat|fix|refactor|perf|test|docs|chore|ci|build|style|revert)(\([\w./-]+\))?!?: [a-z0-9]/.test(c));
  const url = compareUrl(remote, base, branch);
  console.log([
    `rama: ${branch}   upstream: ${upstream || (remote ? `ninguno (git push -u origin ${branch})` : 'sin remoto origin')}`,
    uncommitted.length ? `PARADA: ${uncommitted.length} cambio(s) sin commitear` : 'árbol limpio',
    plan ? planSummary(plan) : `plan: ninguno en ${WORK}/`,
    objective ? `objective: ${objective}` : '',
    `commits (${commits.length}):`, ...commits.map((c) => `  ${c}`),
    badCommits.length ? `commits fuera de Conventional Commits en inglés (tipo(ámbito): minúscula): ${badCommits.length}` : '',
    `archivos: ${files.size}   features: ${features.join(', ') || 'ninguna'}`,
    dbBumped ? `BASE DE DATOS: sube a la versión ${db.version} (migración y su test en el PR)` : 'base de datos: sin cambio de versión',
    events.length ? `eventos de dominio tocados:\n${events.join('\n')}` : 'eventos de dominio: ninguno',
    permissions.length ? `PERMISOS NUEVOS en el manifest: ${permissions.join(', ')}` : 'permisos nuevos: ninguno',
    deps.length ? `dependencias / versiones:\n${deps.map((d) => '  ' + d).join('\n')}` : 'dependencias: sin cambios',
    `docs tocados: ${paths.filter((f) => /^docs\//.test(f)).join(', ') || 'ninguno'}`,
    open.length ? `otras ramas abiertas sin mergear (candidatas a juntar):\n${open.slice(0, 8).map(([r, d]) => `  ${r} (${d})`).join('\n')}` : 'otras ramas abiertas sin mergear: ninguna',
    url ? `compare: ${url}` : '',
  ].filter(Boolean).join('\n'));
}

// ── naming ──────────────────────────────────────────────────────────────────────────────
function cmdNaming(paths) {
  if (!paths.length) { console.error('uso: am.mjs naming <ruta>…'); process.exit(2); }
  const listDir = (dir) => git(['ls-files', '--', dir]).split('\n').filter(Boolean);
  let bad = 0;
  for (const p of paths.map(norm)) {
    const problem = isException(p) ? null : checkNewPath(p, listDir);
    if (problem) bad++;
    console.log(problem ? `NO  ${p}\n    → ${problem.msg}` : `ok  ${p}`);
  }
  process.exit(bad ? 1 : 0);
}

// ── docs ────────────────────────────────────────────────────────────────────────────────
/**
 * Qué doc del proyecto leer antes de explorar código, para las rutas dadas (o el diff): el doc que
 * cubre cada pieza, su tamaño y secciones, y si quedó atrás (commits en el código desde su última
 * edición). Leer la sección justa de un doc reemplaza abrir 10-20 archivos.
 */
function cmdDocs(targets) {
  let paths = targets.map(norm);
  if (!paths.length) paths = [...changedFiles(args.base || PROFILE.base || 'main').keys()];
  const keys = new Set(paths.flatMap((p) => (PROFILE_DOCS[p] ? [p] : PROFILE_DOCS_FOR(p))));
  if (!keys.size) keys.add('guide');
  const code = git(['ls-files', '--', `${MODULE}/src/main`], true)?.split('\n').filter(Boolean).length ? [`${MODULE}/src/main`] : [];
  const out = [];
  for (const k of keys) {
    const d = PROFILE_DOCS[k];
    if (!d) continue;
    if (!existsSync(join(ROOT, d.file))) { out.push(`${d.file} — no existe`); continue; }
    const text = readFileSync(join(ROOT, d.file), 'utf8');
    const last = git(['log', '-1', '--format=%H', '--', d.file], true)?.trim();
    const behind = last && code.length ? Number(git(['rev-list', '--count', `${last}..HEAD`, '--', ...code]).trim()) : 0;
    out.push(`${d.file} — ${d.covers} · ${text.split('\n').length} líneas, ${(text.match(/^#{2,3} /gm) || []).length} secciones${text.split('\n').length > 150 ? ` (largo: node ${TOOL}/am.mjs outline ${d.file} y lee solo la sección)` : ''}${behind > 10 ? ` · ${behind} commits de código desde su última edición: puede estar desactualizado` : ''}`);
  }
  const feats = [...new Set(paths.flatMap((p) => featuresOf(p, ROOT)))];
  if (feats.length) out.push(`features: ${feats.join(', ')}${PROFILE_FEATURES.length ? ` (registradas: ${PROFILE_FEATURES.join(', ')})` : ''}`);
  console.log(out.join('\n'));
}

// ── gate / tasks (planes en .ai-mobile/work) ───────────────────────────────────────────
function cmdGate(file) {
  file = file || planForBranch(true);
  const text = withoutArchive(readFileSync(join(ROOT, file), 'utf8').replace(/\r\n/g, '\n'));
  const tasks = (text.match(/^\s*- \[[ x]\]/gim) || []).length;
  const mentioned = [...new Set([...text.matchAll(/`((?:[\w-]+\/src|gradle|docs|\.github)\/[^`\s:]+|[\w-]+\/build\.gradle\.kts)`/g)].map((m) => m[1]))];
  const source = mentioned.filter(isSource);
  const packages = packagesOf(mentioned);
  const refuse = [];
  if (tasks > QUICK.tasks) refuse.push(`${tasks} tareas > ${QUICK.tasks}`);
  if (source.length > QUICK.files) refuse.push(`${source.length} archivos de código > ${QUICK.files}`);
  for (const s of sensitiveHits(mentioned)) refuse.push(`ruta sensible: ${s}`);
  if (plansMigration(text)) refuse.push('incluye migración');
  if (/feature nueva|nueva feature|new feature|dependencia nueva|nueva dependencia/i.test(text)) refuse.push('crea una feature o agrega una dependencia');
  if (/^##\s*(Open questions|Preguntas abiertas)\s*\n+\s*-\s+(?!\(?ning|none)/im.test(text)) refuse.push('preguntas de producto abiertas');
  if (!/^##\s*Scope check/im.test(text)) refuse.push('falta "## Scope check"');
  if (/^##\s*Unplanned\b/im.test(text)) refuse.push('apareció trabajo no planificado (## Unplanned)');
  const large = source.filter((p) => existsSync(join(ROOT, p)) && lineCount(p) > LARGE_FILE).map((p) => `${p} (${lineCount(p)})`);
  console.log([
    `plan: ${file}   tareas: ${tasks}   archivos de código citados: ${source.length}   paquetes: ${packages.join(', ') || '?'}`,
    refuse.length ? `carril: COMPLETO — ${refuse.join('; ')}` : 'carril: RÁPIDO — cabe en /am-quick',
    large.length ? `archivos grandes (outline + lectura por rango): ${large.join(', ')}` : '',
  ].filter(Boolean).join('\n'));
  process.exit(refuse.length ? 1 : 0);
}

/** El plan sin lo cerrado: encabezado, alcance, restricciones y las tareas abiertas. */
function cmdTasks(file) {
  file = file || planForBranch(true);
  const lines = readFileSync(join(ROOT, file), 'utf8').split('\n');
  const out = [], skipped = [];
  let section = null, inTasks = false, block = null, heading = null, open = 0, done = 0, obsolete = 0;
  const flush = () => {
    if (block?.open) { if (heading) { out.push(heading); heading = null; } out.push(...block.lines); }
    block = null;
  };
  for (const line of lines) {
    const h2 = line.match(/^## (.+)/);
    if (h2) {
      flush();
      section = h2[1].trim();
      inTasks = /^tasks\b/i.test(section);
      if (inTasks || KEEP_SECTIONS.test(section)) out.push('', line); else skipped.push(section);
      continue;
    }
    if (section === null) { if (line.trim()) out.push(line); continue; }
    if (inTasks) {
      if (/^###\s/.test(line)) { flush(); heading = line; continue; }
      // `- [-]` = obsoleta tras un cambio de alcance: se conserva su historia, no se trabaja ni se cuenta.
      const t = line.match(/^- \[( |x|-)\]/i);
      if (t) { flush(); t[1] === ' ' ? open++ : t[1] === '-' ? obsolete++ : done++; block = { open: t[1] === ' ', lines: [line] }; continue; }
      if (block) block.lines.push(line);
      continue;
    }
    if (KEEP_SECTIONS.test(section) && line.trim()) out.push(line);
  }
  flush();
  console.log([
    `${file}: ${done} tareas cerradas (omitidas), ${open} abiertas${obsolete ? `, ${obsolete} obsoletas (omitidas)` : ''}`,
    skipped.length ? `secciones omitidas (Grep si hacen falta): ${skipped.join(' · ')}` : '',
    ...out,
  ].filter((l, i) => i > 1 || l).join('\n'));
}

/**
 * ¿El plan hace una migración? Se decide por sus tareas (abiertas o hechas, no las obsoletas): citar
 * una migración en el Problema como referencia no es migrar.
 */
function plansMigration(text) {
  const tasks = text.split('\n').filter((l) => /^\s*- \[( |x)\]/i.test(l)).join('\n');
  return /schemas\/|Migration\d*To\d*|\bmigraci[oó]n\b(?!:? no)|@Database|version de la base/i.test(tasks);
}

/** `feat/<slug>` → `.ai-mobile/work/<slug>.md`. */
function planForBranch(required) {
  const branch = currentBranch();
  const slug = branch.replace(/^[^/]+\//, '');
  // feat/<slug> → plan-<slug>.md (y el nombre viejo sin prefijo, por si queda alguno)
  const file = [`${WORK}/plan-${slug}.md`, `${WORK}/${slug}.md`].find((f) => existsSync(join(ROOT, f))) ?? `${WORK}/plan-${slug}.md`;
  if (existsSync(join(ROOT, file))) return file;
  if (!required) return null;
  console.error(`no hay plan para la rama "${branch}" (${file}); pásalo como argumento o créalo con /am-define-task`);
  process.exit(2);
}

function planSummary(file) {
  const text = readFileSync(join(ROOT, file), 'utf8');
  const obsolete = (text.match(/^\s*- \[-\]/gm) || []).length;
  return `plan (${file}): ${(text.match(/^\s*- \[x\]/gim) || []).length} hechas, ${(text.match(/^\s*- \[ \]/gm) || []).length} abiertas${obsolete ? `, ${obsolete} obsoletas` : ''}`;
}

// ── debt ────────────────────────────────────────────────────────────────────────────────
/**
 * La deuda estructural del repo en una foto: archivos gigantes, nombres genéricos, acceso a la base
 * fuera de persistence, capas invertidas. Y los puntos calientes: archivos grandes que
 * además cambian seguido (tamaño × cambios) — ahí cada tarea paga más, en tiempo y en tokens, y ahí
 * conviene pagar primero. `--save` guarda la línea base; sin flag compara contra ella.
 */
const DEBT_BASELINE = `${TOOL}/debt-baseline.json`;

function cmdDebt() {
  const m = measure(loadCorpus());
  const days = Number(args.days || 180);
  const churn = new Map();
  for (const f of git(['log', `--since=${days}.days`, '--format=', '--name-only', '--', MODULE]).split('\n').filter(Boolean)) {
    churn.set(norm(f), (churn.get(norm(f)) || 0) + 1);
  }
  const fixes = new Map();
  for (const block of git(['log', `--since=${days}.days`, '--format=%x00%s', '--name-only', '--', MODULE]).split('\0').filter(Boolean)) {
    const [subject, ...names] = block.split('\n');
    if (!/^fix/.test(subject)) continue;
    for (const f of names.filter(Boolean)) fixes.set(norm(f), (fixes.get(norm(f)) || 0) + 1);
  }
  const hot = (m.large.length ? m.large : m.sizable).map((x) => ({ ...x, changes: churn.get(x.file) || 0, fixes: fixes.get(x.file) || 0 }))
    .map((x) => ({ ...x, score: x.lines * x.changes })).sort((a, b) => b.score - a.score);

  const snapshot = {
    date: new Date().toISOString().slice(0, 10), commit: git(['rev-parse', '--short', 'HEAD']).trim(),
    files: m.files, lines: m.lines, large: m.large.length, inLarge: m.inLarge, medium: m.medium,
    generic: m.generic.length, dbOutside: m.dbOutside.length, repositories: m.repositories, layer: m.layer.length,
  };
  const base = existsSync(join(ROOT, DEBT_BASELINE)) ? JSON.parse(readFileSync(join(ROOT, DEBT_BASELINE), 'utf8')) : null;
  const delta = (k) => (base && !args.save ? ` (${snapshot[k] - base[k] >= 0 ? '+' : ''}${snapshot[k] - base[k]} vs ${base.date})` : '');
  const pct = (a, b) => `${Math.round((100 * a) / Math.max(b, 1))}%`;

  const out = [
    `deuda estructural @ ${snapshot.commit}: ${m.files} archivos, ${m.lines} líneas de código (sin tests)`,
    `  archivos > ${LARGE} líneas: ${m.large.length}${delta('large')} — concentran ${m.inLarge} líneas (${pct(m.inLarge, m.lines)} del código)${delta('inLarge')}`,
    `  archivos de 200-${LARGE} líneas: ${m.medium}${delta('medium')}`,
    `  nombres genéricos (Utils/Helper/Manager): ${m.generic.length}${delta('generic')}`,
    `  EntityManager/JdbcTemplate fuera de persistence: ${m.dbOutside.length}${delta('dbOutside')} · repositorios: ${m.repositories}${delta('repositories')}`,
    `  imports que invierten capas (domain → infrastructure, controlador → repositorio, presentation → infrastructure): ${m.layer.length}${delta('layer')}`,
    `puntos calientes (tamaño × cambios en ${days} días) — pagar primero:`,
    ...hot.slice(0, Number(args.top || 10)).map((x, i) => `  ${String(i + 1).padStart(2)}. ${x.file}  ${x.lines} líneas · ${x.changes} cambios · ${x.fixes} fixes`),
  ];
  if (args.save) {
    mkdirSync(dirname(join(ROOT, args.out || DEBT_BASELINE)), { recursive: true });
    writeFileSync(join(ROOT, args.out || DEBT_BASELINE), JSON.stringify(snapshot, null, 2) + '\n');
    out.push(`línea base guardada en ${args.out || DEBT_BASELINE}: las próximas corridas muestran la diferencia`);
  } else if (!base) out.push(`sin línea base: node ${TOOL}/am.mjs debt --save`);
  // Trinquete: la deuda puede bajar, nunca subir. Para CI o antes del PR.
  const WORSE_IF_HIGHER = ['large', 'inLarge', 'generic', 'dbOutside', 'layer'];
  const worse = base && args.check ? WORSE_IF_HIGHER.filter((k) => snapshot[k] > base[k]) : [];
  if (args.check) out.push(worse.length ? `CHECK: empeora ${worse.map((k) => `${k} ${base[k]} → ${snapshot[k]}`).join(', ')}` : 'CHECK: la deuda no creció');
  console.log(out.join('\n'));
  if (worse.length) process.exit(1);
}

// ── impact / proposals (planificación de funcionalidades) ──────────────────────────────
/**
 * Lo que costaría tocar estas rutas, en una llamada: feature y doc del proyecto, tamaño, si es un punto caliente
 * de deuda (cambios y fixes recientes), superficie sensible y si tiene test. Para planificar sin
 * abrir los archivos: el modelo decide con estos hechos, no explorando.
 */
function cmdImpact(targets) {
  if (!targets.length) { console.error('uso: am.mjs impact <ruta|carpeta>…'); process.exit(2); }
  const all = tracked();
  const files = [...new Set(targets.map(norm).flatMap((t) => all.filter((f) => f === t || f.startsWith(t.replace(/\/?$/, '/')))))]
    .filter((f) => /\/src\/main\/.*\.kt$/.test(f) && !isTest(f));
  if (!files.length) { console.log(`ningún archivo de código bajo: ${targets.join(', ')}`); return; }
  const since = git(['log', '--since=180.days', '--format=%x00%s', '--name-only', '--', ...targets.map(norm)]);
  const changes = new Map(), fixes = new Map();
  for (const block of since.split('\0').filter(Boolean)) {
    const [subject, ...names] = block.split('\n');
    for (const f of names.filter(Boolean)) {
      changes.set(f, (changes.get(f) || 0) + 1);
      if (/^fix/.test(subject)) fixes.set(f, (fixes.get(f) || 0) + 1);
    }
  }
  const rt = relatedTests(files);
  const covered = new Set([...rt.unit, ...rt.instrumented]);
  const hasTest = (f) => [...covered].some((c) => c.startsWith(f.split('/').pop().replace(/\.kt$/, '')));
  const rows = files.map((f) => ({ f, lines: lineCount(f), ch: changes.get(f) || 0, fx: fixes.get(f) || 0, spec: hasTest(f) }))
    .sort((a, b) => b.lines * (b.ch + 1) - a.lines * (a.ch + 1));
  const sensitive = sensitiveHits(files);
  const total = rows.reduce((s, r) => s + r.lines, 0);
  console.log([
    `${files.length} archivo(s), ${total} líneas · features: ${[...new Set(files.flatMap((f) => featuresOf(f, ROOT)))].join(', ') || '—'}`,
    sensitive.length ? `superficie sensible: ${sensitive.join(', ')}` : 'superficie sensible: ninguna',
    `puntos calientes (> ${LARGE} líneas): ${rows.filter((r) => r.lines > LARGE).length} · sin test: ${rows.filter((r) => !r.spec && /(UseCase|ViewModel|Projector|Repository(Impl)?)\.kt$|\/domain\/rules\//.test(r.f)).length}`,
    ...rows.slice(0, 12).map((r) => `  ${r.f}  ${r.lines} líneas · ${r.ch} cambios/${r.fx} fixes en 180 días${r.lines > LARGE ? ' · GIGANTE' : ''}${r.spec ? '' : ' · sin test'}`),
    rows.length > 12 ? `  … y ${rows.length - 12} más` : '',
  ].filter(Boolean).join('\n'));
  cmdDocs(files.slice(0, 20));
}

const PROPOSALS = `${TOOL}/proposals`;

/** Las propuestas por estado, y si el código que citan cambió desde que se escribieron. */
function cmdProposals() {
  for (const n of syncSessions()) console.log(n);
  const dir = join(ROOT, PROPOSALS);
  const files = existsSync(dir) ? readdirSync(dir).filter((f) => f.endsWith('.md')).sort() : [];
  if (!files.length) { console.log('sin propuestas: /am-plan-feature <idea> crea la primera'); return; }
  const out = [];
  for (const f of files) {
    const text = readFileSync(join(dir, f), 'utf8');
    const status = text.match(/^Estado:\s*([^\n·]+)/m)?.[1]?.trim() ?? '?';
    if (args.status && !status.toLowerCase().startsWith(String(args.status).toLowerCase())) continue;
    const title = text.match(/^#\s+(.+)/m)?.[1] ?? f;
    const sha = text.match(/^Fecha:.*Base:\s*\S+\s+([0-9a-f]{7,})/m)?.[1];
    const effort = text.match(/^##\s*Esfuerzo\s*\n+\s*\**([SML])\b/m)?.[1] ?? '?';
    const cited = [...new Set([...text.matchAll(/`([\w-]+\/src\/[^`\s:]+)`/g)].map((m) => m[1]))];
    let stale = '';
    if (sha && cited.length && !/^(convertida|descartada)/i.test(status)) {
      const n = git(['rev-list', '--count', `${sha}..HEAD`, '--', ...cited], true)?.trim();
      if (n && Number(n) > 0) stale = ` · el código citado tuvo ${n} commit(s) desde la propuesta: revisar antes de aprobar`;
    }
    out.push(`  [${status}] ${f.replace(/\.md$/, '')} — ${title} · esfuerzo ${effort}${stale}`);
  }
  console.log([`${out.length} propuesta(s) en ${PROPOSALS}:`, ...out].join('\n'));
}

// ── session (agrupar planes y propuestas) ──────────────────────────────────────────────
/**
 * Una sesión agrupa planes y propuestas que se trabajan juntos (un frente, un sprint, un grupo de
 * funcionalidades que se cruzan). No mueve nada: los planes siguen en work/ y las propuestas en
 * proposals/; la sesión solo los referencia en `.ai-mobile/sessions/<nombre>/session.md`, y su carpeta
 * guarda lo que se decida sobre el grupo (la sección Coordinación de /am-reconcile).
 */
const SESSIONS = `${TOOL}/sessions`;

function sessionFile(name) { return join(ROOT, SESSIONS, name, 'session.md'); }

/**
 * Prefijos: `plan-<slug>.md` en work/ (la tarea de una rama) y `prop-<slug>.md` en proposals/. El
 * prefijo dice qué es sin abrir el archivo; la rama sigue siendo `feat/<slug>`, sin prefijo.
 */
export const PREFIX = { plan: 'plan-', propuesta: 'prop-' };

function docKind(slug) {
  if (existsSync(join(ROOT, WORK, `${slug}.md`))) return 'plan';
  if (existsSync(join(ROOT, PROPOSALS, `${slug}.md`))) return 'propuesta';
  return null;
}

/** Todos los .md que pueden citar a otro documento: planes, propuestas y sesiones. */
function allDocFiles() {
  const out = [];
  for (const dir of [WORK, PROPOSALS]) {
    const abs = join(ROOT, dir);
    if (existsSync(abs)) out.push(...readdirSync(abs).filter((f) => f.endsWith('.md') && !f.startsWith('backtest-')).map((f) => join(abs, f)));
  }
  const sdir = join(ROOT, SESSIONS);
  if (existsSync(sdir)) out.push(...readdirSync(sdir).map((d) => join(sdir, d, 'session.md')).filter((f) => existsSync(f)));
  return out;
}

/**
 * Cambia un slug por otro en un texto: las rutas `work/<slug>.md` / `proposals/<slug>.md` y el slug como
 * palabra suelta. Nunca dentro de otra ruta ni de un nombre de rama: `feat/<slug>` queda igual.
 */
function replaceSlug(text, from, to, { paths = true } = {}) {
  const s = from.replace(/[-]/g, '\\-');
  return (paths ? text.replace(new RegExp(`((?:work|proposals)/)${s}\\.md`, 'g'), `$1${to}.md`) : text)
    // Un punto final de frase sí se reemplaza; `.md` (parte de una ruta) no.
    .replace(new RegExp(`(?<![\\w\\-/])${s}(?![\\w\\-/]|\\.md)`, 'g'), to);
}

/** Reemplaza un slug por otro en todos los documentos; devuelve cuántos tocó. */
function replaceSlugEverywhere(from, to) {
  let touched = 0;
  for (const f of allDocFiles()) {
    const text = readFileSync(f, 'utf8');
    const next = replaceSlug(text, from, to);
    if (next !== text) { writeFileSync(f, next); touched++; }
  }
  return touched;
}

/**
 * Las sesiones se corrigen solas: una propuesta convertida pasa a ser su plan como miembro; un miembro
 * que ya no existe se avisa. Mecánico y barato, corre antes de session/overlap/proposals.
 */
function syncSessions() {
  const dir = join(ROOT, SESSIONS);
  if (!existsSync(dir)) return [];
  const notes = [];
  for (const name of readdirSync(dir).filter((d) => existsSync(join(dir, d, 'session.md')))) {
    const { members } = readSession(name);
    let changed = false;
    const next = members.map((m) => {
      if (m.kind !== 'propuesta') return m;
      // Convertida: se borró y existe plan-<mismo slug>, o quedó marcada (convert --keep o a mano).
      const p = join(ROOT, PROPOSALS, `${m.slug}.md`);
      const twin = `${PREFIX.plan}${m.slug.replace(new RegExp(`^${PREFIX.propuesta}`), '')}`;
      const target = existsSync(p)
        ? readFileSync(p, 'utf8').match(/^Estado:\s*convertida\s*→\s*\S*?([a-z0-9-]+)\.md/m)?.[1]
        : twin;
      if (!target || !existsSync(join(ROOT, WORK, `${target}.md`))) return m;
      changed = true;
      notes.push(`sesión ${name}: ${m.slug} → ${target} (propuesta convertida)`);
      return { kind: 'plan', slug: target };
    }).filter((m, i, all) => all.findIndex((x) => x.slug === m.slug) === i);
    if (changed) writeMembers(name, next);
  }
  return notes;
}

/** Sección donde `convert` archiva la propuesta dentro del plan. `tasks`, `gate` y `overlap` la omiten. */
const ARCHIVE = '## Propuesta original';
function withoutArchive(text) {
  const i = text.indexOf(`\n${ARCHIVE}`);
  if (i < 0) return text;
  const rest = text.slice(i + 1);
  const next = rest.slice(ARCHIVE.length).search(/\n## /);
  return next < 0 ? text.slice(0, i + 1) : text.slice(0, i + 1) + rest.slice(ARCHIVE.length + next + 1);
}

/**
 * Después de que /am-define-task escribió el plan desde una propuesta: archiva lo que la propuesta
 * tenía y el plan no repite (alternativas, riesgos, preguntas) en una sección del plan que no se lee al
 * trabajar, enlaza el origen, pasa las sesiones y las referencias al plan y **borra la propuesta**
 * (`--keep` la conserva marcada como convertida). Todo por script: cero tokens del modelo.
 */
function cmdConvert(prop, plan) {
  if (!prop || !existsSync(join(ROOT, PROPOSALS, `${prop}.md`))) { console.error('uso: am.mjs convert <prop-slug> [plan-slug] [--keep]  (la propuesta no existe)'); process.exit(2); }
  plan = plan || `${PREFIX.plan}${prop.replace(new RegExp(`^${PREFIX.propuesta}`), '')}`;
  const planPath = join(ROOT, WORK, `${plan}.md`);
  if (!existsSync(planPath)) { console.error(`primero el plan: ${WORK}/${plan}.md no existe (lo escribe /am-define-task)`); process.exit(2); }
  const date = new Date().toISOString().slice(0, 10);
  const propPath = join(ROOT, PROPOSALS, `${prop}.md`);
  const propText = readFileSync(propPath, 'utf8').replace(/\r\n/g, '\n');

  // 1. Archivo: el cuerpo de la propuesta sin título ni estado, y sin su Coordinación (el plan tiene la suya).
  let planText = readFileSync(planPath, 'utf8').replace(/\r\n/g, '\n');
  if (!planText.includes(ARCHIVE)) {
    const body = propText.replace(/^#[^\n]*\n/, '').replace(/^(Estado|Fecha):[^\n]*\n/gm, '')
      .replace(/\n## Coordinación\n[\s\S]*?(?=\n## |$)/, '').trim()
      .replace(/^## /gm, '### ');
    const title = propText.match(/^# (.+)/m)?.[1] ?? prop;
    planText = `${planText.trimEnd()}\n\n${ARCHIVE}\n<!-- ${prop}: "${title}", archivada al convertir (${date}). tasks, gate y overlap no leen esta sección. -->\n${body}\n`;
  }
  const origin = `Origen: propuesta \`${prop}\` (convertida ${date}; su contenido, en *Propuesta original*)`;
  planText = /^Origen:/m.test(planText) ? planText.replace(/^Origen:.*$/m, origin) : planText.replace(/^(# .*\n)/, `$1${origin}\n`);
  writeFileSync(planPath, planText);

  // 2. Sesiones: el miembro pasa de la propuesta al plan, antes de borrarla.
  const sdir = join(ROOT, SESSIONS);
  const sessions = [];
  if (existsSync(sdir)) for (const name of readdirSync(sdir).filter((d) => existsSync(join(sdir, d, 'session.md')))) {
    const { members } = readSession(name);
    if (!members.some((m) => m.slug === prop)) continue;
    writeMembers(name, members.map((m) => (m.slug === prop ? { kind: 'plan', slug: plan } : m))
      .filter((m, i, all) => all.findIndex((x) => x.slug === m.slug) === i));
    sessions.push(`sesión ${name}: ${prop} → ${plan}`);
  }

  // 3. Referencias: slug y ruta de la propuesta pasan a ser los del plan (la línea Origen ya está bien).
  let refs = 0;
  for (const f of allDocFiles().filter((x) => !x.endsWith(`${prop}.md`))) {
    const text = readFileSync(f, 'utf8');
    // Origen y el comentario del archivo nombran la propuesta a propósito; los miembros ya se cambiaron.
    const next = text.split('\n').map((l) => (/^Origen:|^- (plan|propuesta): |archivada al convertir/.test(l) ? l
      : replaceSlug(l, prop, plan).replace(new RegExp(`proposals/${plan}\\.md`, 'g'), `work/${plan}.md`))).join('\n');
    if (next !== text) { writeFileSync(f, next); refs++; }
  }

  // 4. La propuesta se borra (o queda marcada como convertida, con --keep).
  if (args.keep) writeFileSync(propPath, propText.replace(/^Estado:.*$/m, `Estado: convertida → ${WORK}/${plan}.md (${date})`));
  else rmSync(propPath);
  console.log([`convertida: ${prop} → ${plan}`,
    args.keep ? 'propuesta conservada (--keep)' : `propuesta borrada; su contenido quedó en ${WORK}/${plan}.md, sección "Propuesta original"`,
    `referencias actualizadas en ${refs} documento(s)`, ...sessions].join('\n'));
}

function readSession(name) {
  const f = sessionFile(name);
  if (!existsSync(f)) { console.error(`no existe la sesión "${name}" (node ${TOOL}/am.mjs session list)`); process.exit(2); }
  // Normalizado a \n: un session.md editado en Windows (CRLF) no debe romper la lectura de miembros.
  const text = readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
  const members = [...text.matchAll(/^- (plan|propuesta): ([a-z0-9-]+)/gm)].map((m) => ({ kind: m[1], slug: m[2] }));
  return { text, members, status: text.match(/^Estado:\s*(\S+)/m)?.[1] ?? 'activa' };
}

function writeMembers(name, members) {
  const f = sessionFile(name);
  const text = readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
  const block = `## Miembros\n${members.map((m) => `- ${m.kind}: ${m.slug}`).join('\n') || '- (ninguno)'}\n`;
  writeFileSync(f, text.replace(/## Miembros\n(?:- [^\n]*\n)*/, block));
}

function resolveMembers(slugs) {
  const ok = [], missing = [];
  for (const s of slugs) { const kind = docKind(s); kind ? ok.push({ kind, slug: s }) : missing.push(s); }
  if (missing.length) { console.error(`no existe plan ni propuesta: ${missing.join(', ')}`); process.exit(2); }
  return ok;
}

function cmdSession(sub, name, rest) {
  for (const n of syncSessions()) console.log(n);
  if (sub === 'list' || !sub) return sessionList();
  if (!name || !/^[a-z0-9][a-z0-9-]*$/.test(name)) { console.error('uso: am.mjs session new|add|remove|show|order|close <nombre-kebab> [slug…] | session list'); process.exit(2); }
  if (sub === 'new') {
    if (existsSync(sessionFile(name))) { console.error(`la sesión "${name}" ya existe: usa session add`); process.exit(2); }
    const members = resolveMembers(rest);
    mkdirSync(dirname(sessionFile(name)), { recursive: true });
    writeFileSync(sessionFile(name), [
      `# Sesión: ${name}`, '', 'Estado: activa', `Fecha: ${new Date().toISOString().slice(0, 10)}`,
      `Objetivo: ${typeof args.goal === 'string' ? args.goal : '<qué agrupa esta sesión y por qué>'}`, '',
      '## Miembros', ...(members.length ? members.map((m) => `- ${m.kind}: ${m.slug}`) : ['- (ninguno)']), '',
      '## Coordinación', '<!-- la escribe /am-reconcile --session: un cruce por línea, con su decisión -->', '',
    ].join('\n'));
    console.log(`sesión creada: ${SESSIONS}/${name}/session.md con ${members.length} miembro(s)`);
    return;
  }
  const s = readSession(name);
  if (sub === 'add' || sub === 'remove') {
    const change = resolveMembers(rest);
    const members = sub === 'add'
      ? [...s.members, ...change.filter((c) => !s.members.some((m) => m.slug === c.slug))]
      : s.members.filter((m) => !change.some((c) => c.slug === m.slug));
    writeMembers(name, members);
    console.log(`sesión ${name}: ${members.length} miembro(s)`);
    return;
  }
  if (sub === 'order') return sessionOrder(name, s);
  if (sub === 'close') {
    writeFileSync(sessionFile(name), s.text.replace(/^Estado:.*$/m, `Estado: cerrada (${new Date().toISOString().slice(0, 10)})`));
    console.log(`sesión ${name} cerrada (sus planes y propuestas siguen donde están)`);
    return;
  }
  if (sub === 'show') {
    const out = [`sesión ${name} [${s.status}] — ${s.text.match(/^Objetivo:\s*(.*)$/m)?.[1] ?? ''}`];
    for (const m of s.members) {
      const path = join(ROOT, m.kind === 'plan' ? WORK : PROPOSALS, `${m.slug}.md`);
      if (!existsSync(path)) { out.push(`  ${m.kind} ${m.slug} — NO EXISTE (¿se borró o renombró?)`); continue; }
      const text = readFileSync(path, 'utf8');
      const detail = m.kind === 'plan'
        ? `${(text.match(/^\s*- \[ \]/gm) || []).length} abiertas, ${(text.match(/^\s*- \[x\]/gim) || []).length} hechas · ${text.match(/^Branch:\s*`([^`]+)`/m)?.[1] ?? 'sin rama'}`
        : `${text.match(/^Estado:\s*([^\n·]+)/m)?.[1]?.trim() ?? '?'}`;
      const coord = (text.match(/^## Coordinación\n((?:- .*\n?)*)/m)?.[1] ?? '').split('\n').filter(Boolean).length;
      out.push(`  ${m.kind.padEnd(9)} ${m.slug} — ${detail}${coord ? ` · ${coord} nota(s) de coordinación` : ''}`);
    }
    const ord = readOrder(s.text);
    out.push(ord.length ? `orden: ${ord.map((o) => `${o.n}. ${o.plans.join(' + ')}`).join(' → ')}   (estado y siguiente: session order ${name})` : `orden: sin definir (session order ${name} --set "…")`);
    out.push(`cruces: node ${TOOL}/am.mjs overlap --session ${name}   ·   conciliar: /am-reconcile --session ${name}`);
    console.log(out.join('\n'));
    return;
  }
  console.error(`subcomando desconocido: ${sub}`);
  process.exit(2);
}

/**
 * El orden de ejecución de una sesión, en una sección estructurada del session.md:
 *
 *   ## Orden
 *   1. plan-a · rama feat/a
 *   2. plan-b + plan-c · rama feat/c        (varios planes, una rama y un PR)
 *
 * `session order <s>` muestra cada paso con su estado calculado (mergeado a main, rama abierta, tareas
 * abiertas), marca el siguiente y da los comandos. `--set "plan-a; plan-b + plan-c"` lo escribe,
 * tomando la rama de la línea Branch de cada plan.
 */
function readOrder(text) {
  const block = text.replace(/\r\n/g, '\n').match(/^## Orden\n((?:\d+\..*\n?)*)/m)?.[1] ?? '';
  return block.split('\n').filter(Boolean).map((l) => {
    const m = l.match(/^(\d+)\.\s*(.+?)\s*·\s*rama\s+`?([^`\s]+)`?/);
    return m ? { n: Number(m[1]), plans: m[2].split('+').map((x) => x.trim()), branch: m[3] } : null;
  }).filter(Boolean);
}

function planBranch(slug) {
  const p = join(ROOT, WORK, `${slug}.md`);
  return existsSync(p) ? readFileSync(p, 'utf8').match(/^Branch:\s*`([^`]+)`/m)?.[1] ?? null : null;
}

function branchState(branch) {
  const base = git(['rev-parse', '--verify', '-q', 'origin/main'], true) ? 'origin/main' : 'main';
  const merged = git(['log', base, '--merges', '--format=%s', '-n', '400'], true)?.split('\n')
    .some((s) => new RegExp(`from [^/]+/${branch.replace(/[/.-]/g, '\\$&')}$`).test(s));
  const local = git(['rev-parse', '--verify', '-q', branch], true) !== null;
  const remote = git(['rev-parse', '--verify', '-q', `origin/${branch}`], true) !== null;
  return { merged, exists: local || remote, local };
}

function sessionOrder(name, s) {
  if (typeof args.set === 'string') {
    const steps = args.set.split(';').map((x) => x.trim()).filter(Boolean).map((x) => x.split('+').map((y) => y.trim()));
    const missing = steps.flat().filter((slug) => !s.members.some((m) => m.slug === slug));
    if (missing.length) { console.error(`no son miembros de la sesión: ${missing.join(', ')} (session add primero)`); process.exit(2); }
    const lines = steps.map((plans, i) => {
      const branches = [...new Set(plans.map(planBranch).filter(Boolean))];
      if (branches.length > 1) console.warn(`aviso: el paso ${i + 1} junta planes con ramas distintas (${branches.join(', ')}): se usa ${branches[0]}; alinea la línea Branch de los planes`);
      return `${i + 1}. ${plans.join(' + ')} · rama \`${branches[0] ?? `feat/${plans[0].replace(/^(plan|prop)-/, '')}`}\``;
    });
    const f = sessionFile(name);
    const text = readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
    const block = `## Orden\n${lines.join('\n')}\n`;
    writeFileSync(f, /^## Orden\n/m.test(text) ? text.replace(/^## Orden\n(?:\d+\..*\n?)*/m, block) : text.replace(/\n## Coordinación/, `\n${block}\n## Coordinación`));
    s = readSession(name);
  }
  const order = readOrder(s.text);
  if (!order.length) {
    console.log([`sesión ${name}: sin orden definido.`,
      `  defínelo: node ${TOOL}/am.mjs session order ${name} --set "plan-a; plan-b + plan-c; plan-d"`,
      `  (lo normal es que /am-reconcile lo escriba al acordar la estrategia de ramas)`,
      `  miembros: ${s.members.map((m) => m.slug).join(', ')}`].join('\n'));
    return;
  }
  const out = [`sesión ${name} — orden de ejecución (${order.length} PR${order.length === 1 ? '' : 's'}):`];
  let next = null;
  for (const step of order) {
    const st = branchState(step.branch);
    const tasks = step.plans.map((slug) => {
      const p = join(ROOT, WORK, `${slug}.md`);
      if (!existsSync(p)) return { slug, open: null, done: 0 };
      const t = readFileSync(p, 'utf8');
      return { slug, open: (t.match(/^\s*- \[ \]/gm) || []).length, done: (t.match(/^\s*- \[x\]/gim) || []).length };
    });
    const open = tasks.reduce((n, t) => n + (t.open ?? 0), 0);
    const state = st.merged ? 'hecho (mergeado a main)'
      : st.exists ? (open ? `en curso · ${open} tarea(s) abierta(s)` : 'tareas cerradas · falta /am-audit y /am-create-pr')
      : 'pendiente';
    const mark = !st.merged && !next ? (next = step, '→') : ' ';
    out.push(`${mark} ${step.n}. ${step.plans.join(' + ')}  [${step.branch}]  ${state}`);
    for (const t of tasks) out.push(`       ${t.slug}: ${t.open === null ? 'NO EXISTE el plan' : `${t.done} hechas, ${t.open} abiertas`}`);
  }
  if (!next) { out.push('', `todo mergeado: node ${TOOL}/am.mjs session close ${name}`); console.log(out.join('\n')); return; }
  const st = branchState(next.branch);
  const shared = next.plans.length > 1;
  out.push('', `siguiente: paso ${next.n}`);
  if (!st.exists) out.push(`  git switch main && git pull && git switch -c ${next.branch}`);
  else if (!st.local) out.push(`  git fetch && git switch ${next.branch}`);
  else out.push(`  git switch ${next.branch}`);
  for (const slug of next.plans) {
    const p = join(ROOT, WORK, `${slug}.md`);
    const open = existsSync(p) ? (readFileSync(p, 'utf8').match(/^\s*- \[ \]/gm) || []).length : 0;
    if (open) out.push(`  /clear  →  /am-implement${shared || planForBranchName(next.branch) !== slug ? ` ${WORK}/${slug}.md` : ''}`);
  }
  out.push('  /am-audit  →  /am-create-pr  (un PR para todo el paso; después del merge, vuelve a session order)');
  const earlier = order.filter((o) => o.n < next.n && !branchState(o.branch).merged);
  if (earlier.length) out.push(`  ojo: los pasos ${earlier.map((o) => o.n).join(', ')} aún no están mergeados; esta rama debería salir de main con ellos`);
  console.log(out.join('\n'));
}

/** El plan que `planForBranch` encontraría para una rama (feat/<slug> → plan-<slug>). */
function planForBranchName(branch) { return `plan-${branch.replace(/^[^/]+\//, '')}`; }

function sessionList() {
  const dir = join(ROOT, SESSIONS);
  const names = existsSync(dir) ? readdirSync(dir).filter((d) => existsSync(join(dir, d, 'session.md'))).sort() : [];
  const assigned = new Set();
  const out = [`${names.length} sesión(es) en ${SESSIONS}:`];
  for (const n of names) {
    const s = readSession(n);
    s.members.forEach((m) => assigned.add(m.slug));
    out.push(`  [${s.status}] ${n} — ${s.members.length} miembro(s): ${s.members.map((m) => m.slug).join(', ') || '—'}`);
  }
  const all = overlapDocs().map((d) => d.id);
  const loose = all.filter((id) => !assigned.has(id));
  out.push(loose.length ? `sin sesión: ${loose.join(', ')}` : 'todos los planes y propuestas activos tienen sesión');
  console.log(out.join('\n'));
}

// ── overlap (cruce entre planes y propuestas) ──────────────────────────────────────────
/**
 * Dónde se tocan varios planes y propuestas: rutas, endpoints, símbolos, tablas, permisos y
 * migraciones que citan dos o más documentos, con la línea de cada uno. La skill /am-reconcile lee
 * solo esas líneas para juzgar si hay contradicción, duplicación o dependencia, en vez de releer
 * todos los documentos enteros (5 documentos ≈ 9k tokens; las líneas que se cruzan, una fracción).
 */
const OVERLAP_KINDS = [
  ['ruta', /`((?:[\w-]+\/src|docs)\/[^`\s:]+?)(?::\d+)?`/g, (m) => m[1]],
  ['endpoint', /\b(GET|POST|PUT|PATCH|DELETE)\s+(\/[A-Za-z0-9/:{}_.-]+)/g, (m) => `${m[1]} ${m[2].replace(/\/$/, '')}`],
  // Tablas (snake_case plural, modelo §4), eventos (sustantivo + verbo en pasado) y features (guía §4).
  ['tabla', /\btabla\s+`?([a-z][a-z0-9_]+)`?|`([a-z]+(?:_[a-z]+)+)`/gi, (m) => (m[1] ?? m[2]).toLowerCase()],
  ['evento', /`([A-Z][a-z]+(?:[A-Z][a-z]+)*(?:Registered|Created|Renamed|Archived|Corrected|Voided|Recorded|Resolved|Cancelled|Generated|Received|Granted|ToppedUp|Marked|Unmarked|Counted|Closed|Scheduled|Confirmed|Restored|Rebuilt|Exported|Linked\w*))`/g, (m) => m[1]],
  ['feature', /\bfeature\/(\w+)\//g, (m) => m[1]],
  ['símbolo', /`([A-Z][A-Za-z0-9]{3,})`/g, (m) => m[1]],
];

function overlapDocs() {
  const docs = [];
  for (const [dir, kind] of [[WORK, 'plan'], [PROPOSALS, 'propuesta']]) {
    const abs = join(ROOT, dir);
    if (!existsSync(abs)) continue;
    for (const f of readdirSync(abs).filter((x) => x.endsWith('.md') && !x.startsWith('backtest-'))) {
      const text = withoutArchive(readFileSync(join(abs, f), 'utf8').replace(/\r\n/g, '\n'));
      const status = text.match(/^Estado:\s*([^\n·]+)/m)?.[1]?.trim() ?? '';
      if (/^(convertida|descartada)/i.test(status)) continue; // ya no compiten por nada
      docs.push({ id: f.replace(/\.md$/, ''), path: `${dir}/${f}`, kind, text,
        branch: text.match(/^Branch:\s*`([^`]+)`/m)?.[1] ?? null,
        migration: kind === 'plan' ? plansMigration(text) : /migración: sí|tabla nueva|nueva tabla|columna nueva|versión de la base/i.test(text) });
    }
  }
  return docs;
}

function cmdOverlap(only) {
  for (const n of syncSessions()) console.log(n);
  let docs = overlapDocs();
  if (typeof args.session === 'string') {
    const { members } = readSession(args.session);
    only = [...only, ...members.map((m) => m.slug)];
  }
  if (only.length) docs = docs.filter((d) => only.some((o) => d.id === o || d.path === norm(o)));
  if (docs.length < 2) { console.log(`hacen falta al menos 2 documentos activos (hay ${docs.length})`); return; }
  // ítem → doc → líneas
  const index = new Map();
  for (const d of docs) {
    d.text.split('\n').forEach((line, i) => {
      for (const [kind, re, pick] of OVERLAP_KINDS) {
        for (const m of line.matchAll(re)) {
          const key = `${kind}\u0000${pick(m)}`;
          const byDoc = index.get(key) ?? index.set(key, new Map()).get(key);
          (byDoc.get(d.id) ?? byDoc.set(d.id, []).get(d.id)).push(i + 1);
        }
      }
    });
  }
  // Endpoints (si el proyecto tuviera API) que comparten prefijo también se tocan.
  const endpoints = [...index.keys()].filter((k) => k.startsWith('endpoint\u0000'));
  for (const a of endpoints) for (const b of endpoints) {
    const pa = a.split(' ')[1], pb = b.split(' ')[1];
    if (a !== b && pb.startsWith(`${pa}/`)) for (const [doc, ls] of index.get(b)) {
      const tgt = index.get(a);
      if (!tgt.has(doc)) tgt.set(doc, ls.map((l) => -l)); // negativo = por prefijo
    }
  }
  const pairs = new Map();
  for (const [key, byDoc] of index) {
    const ids = [...byDoc.keys()];
    if (ids.length < 2) continue;
    const [kind, value] = key.split('\u0000');
    for (let i = 0; i < ids.length; i++) for (let j = i + 1; j < ids.length; j++) {
      const k = [ids[i], ids[j]].sort().join(' ↔ ');
      (pairs.get(k) ?? pairs.set(k, []).get(k)).push({ kind, value, lines: Object.fromEntries(ids.filter((x) => x === ids[i] || x === ids[j]).map((x) => [x, byDoc.get(x)])) });
    }
  }
  const WEIGHT = { ruta: 3, endpoint: 3, tabla: 3, evento: 3, feature: 1, símbolo: 2 };
  const ranked = [...pairs].map(([k, items]) => ({ k, items, score: items.reduce((s, it) => s + WEIGHT[it.kind], 0) }))
    .sort((a, b) => b.score - a.score);
  const byId = Object.fromEntries(docs.map((d) => [d.id, d]));
  const out = [`${docs.length} documentos activos: ${docs.map((d) => `${d.id} (${d.kind}${d.branch ? `, ${d.branch}` : ''})`).join(' · ')}`];
  for (const { k, items, score } of ranked) {
    const [a, b] = k.split(' ↔ ');
    const notes = [];
    if (byId[a].migration && byId[b].migration) notes.push('los dos suben la versión de la base: Migration<n>To<n+1> choca — ordenarlas en un solo PR o en secuencia');
    if (byId[a].branch && byId[b].branch && byId[a].branch !== byId[b].branch && items.some((it) => it.kind === 'ruta')) notes.push('ramas distintas sobre los mismos archivos: conflicto de merge probable — candidatas a una sola rama/PR');
    out.push('', `${k}  (peso ${score}, ${items.length} cruce(s))${notes.length ? `\n  ! ${notes.join('\n  ! ')}` : ''}`);
    for (const it of items.sort((x, y) => WEIGHT[y.kind] - WEIGHT[x.kind]).slice(0, Number(args.top || 15))) {
      const where = Object.entries(it.lines).map(([doc, ls]) => `${doc}:${[...new Set(ls.map((l) => Math.abs(l)))].slice(0, 4).join(',')}${ls.some((l) => l < 0) ? '(prefijo)' : ''}`).join(' · ');
      out.push(`  ${it.kind.padEnd(8)} ${it.value.slice(0, 70)}  →  ${where}`);
    }
    if (items.length > Number(args.top || 15)) out.push(`  … ${items.length - Number(args.top || 15)} cruce(s) más (--top N)`);
  }
  const isolated = docs.filter((d) => ![...pairs.keys()].some((k) => k.split(' ↔ ').includes(d.id)));
  if (isolated.length) out.push('', `sin cruces: ${isolated.map((d) => d.id).join(', ')}`);
  console.log(out.join('\n'));
}

// ── ledger ──────────────────────────────────────────────────────────────────────────────
/**
 * Registro de auditorías: cada `/am-audit` anota qué alertas mecánicas confirmó o descartó (y
 * por qué) y qué encontró por criterio que ningún script vio. Un archivo por rama en
 * `.ai-mobile/ledger/` (ignorado por git, como work/). `report` dice qué hacer con eso:
 * reglas que se equivocan demasiado (afinar) y hallazgos de criterio que se repiten en ramas
 * distintas y una máquina podría detectar (promover a regla). Es el mismo ciclo que llevó a
 * abet-plugins de "un auditor encuentra este IDOR leyendo" a la regla scope-param-unused.
 */
const LEDGER = `${TOOL}/ledger`;
const LEDGER_FIELDS = {
  mechanical: ['rule', 'file', 'verdict', 'reason'],
  judgment: ['severity', 'file', 'shape', 'detect', 'convertible'],
};

function cmdLedger(sub, file) {
  if (sub === 'add') return ledgerAdd(file);
  if (sub === 'report') return ledgerReport();
  console.error('uso: am.mjs ledger add <entradas.json> | ledger report');
  process.exit(2);
}

function ledgerAdd(file) {
  if (!file || !existsSync(join(ROOT, file))) { console.error(`no existe: ${file}`); process.exit(2); }
  const entries = JSON.parse(readFileSync(join(ROOT, file), 'utf8'));
  const list = Array.isArray(entries) ? entries : [entries];
  const errors = [];
  list.forEach((e, i) => {
    const need = LEDGER_FIELDS[e.kind];
    if (!need) return errors.push(`#${i}: kind debe ser mechanical o judgment`);
    const missing = need.filter((f) => !e[f]);
    if (missing.length) errors.push(`#${i} (${e.kind}): falta ${missing.join(', ')}`);
    if (e.kind === 'mechanical' && !['confirmed', 'dismissed'].includes(e.verdict)) errors.push(`#${i}: verdict debe ser confirmed o dismissed`);
    if (e.kind === 'judgment' && !['yes', 'maybe', 'no'].includes(e.convertible)) errors.push(`#${i}: convertible debe ser yes, maybe o no`);
    if (e.scope && !['task', 'out-of-scope'].includes(e.scope)) errors.push(`#${i}: scope debe ser task u out-of-scope`);
  });
  if (errors.length) { console.error(`nada escrito:\n  ${errors.join('\n  ')}`); process.exit(1); }

  const branch = currentBranch() || 'detached';
  const path = join(ROOT, LEDGER, `${branch.replace(/[^\w.-]+/g, '_')}.jsonl`);
  mkdirSync(dirname(path), { recursive: true });
  const key = (e) => [e.kind, e.rule || e.shape, e.file, e.line ?? ''].join('|');
  const prior = existsSync(path) ? readFileSync(path, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l)) : [];
  // Re-auditar la misma rama reemplaza, no suma: una alerta cuenta una vez por rama.
  const byKey = new Map(prior.map((e) => [key(e), e]));
  const date = new Date().toISOString().slice(0, 10);
  for (const e of list) byKey.set(key(e), { date, branch, scope: 'task', ...e });
  writeFileSync(path, [...byKey.values()].map((e) => JSON.stringify(e)).join('\n') + '\n');
  console.log(`ledger: ${list.length} entrada(s) en ${LEDGER}/${branch.replace(/[^\w.-]+/g, '_')}.jsonl (${byKey.size} en total para la rama)`);
}

function ledgerReport() {
  const dir = join(ROOT, LEDGER);
  const files = existsSync(dir) ? readdirSync(dir).filter((f) => f.endsWith('.jsonl')) : [];
  const all = files.flatMap((f) => readFileSync(join(dir, f), 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l)));
  if (!all.length) { console.log('ledger vacío: se llena con cada /am-audit'); return; }
  const out = [`ledger: ${all.length} entradas de ${files.length} rama(s)`];

  const rules = new Map();
  for (const e of all.filter((x) => x.kind === 'mechanical')) {
    const r = rules.get(e.rule) ?? rules.set(e.rule, { ok: 0, no: 0, reasons: new Map() }).get(e.rule);
    if (e.verdict === 'confirmed') r.ok++;
    else { r.no++; r.reasons.set(e.reason, (r.reasons.get(e.reason) || 0) + 1); }
  }
  out.push('precisión de las reglas (confirmadas / revisadas):');
  for (const [rule, r] of [...rules].sort((a, b) => (a[1].ok / (a[1].ok + a[1].no)) - (b[1].ok / (b[1].ok + b[1].no)))) {
    const total = r.ok + r.no, p = Math.round((100 * r.ok) / total);
    const top = [...r.reasons].sort((a, b) => b[1] - a[1])[0];
    out.push(`  ${rule.padEnd(26)} ${String(p).padStart(3)}% (${r.ok}/${total})${total >= 4 && p < 50 ? `  → AFINAR: el motivo más común de descarte es "${top?.[0]}"` : ''}`);
  }

  const shapes = new Map();
  for (const e of all.filter((x) => x.kind === 'judgment')) {
    const k = e.shape.toLowerCase().replace(/\s+/g, ' ').trim();
    const s = shapes.get(k) ?? shapes.set(k, { branches: new Set(), detect: e.detect, convertible: new Set(), n: 0 }).get(k);
    s.branches.add(e.branch); s.convertible.add(e.convertible); s.n++;
  }
  const promote = [...shapes].filter(([, s]) => s.branches.size >= 2 && !s.convertible.has('no'));
  out.push(promote.length ? 'promover a regla mecánica (se repite en ramas distintas):' : 'hallazgos de criterio: ninguno se repite todavía en dos ramas');
  for (const [shape, s] of promote) out.push(`  "${shape}" — ${s.n} vez(es) en ${s.branches.size} ramas · detectar: ${s.detect}`);

  const outOfScope = all.filter((e) => e.scope === 'out-of-scope');
  if (outOfScope.length) out.push(`fuera de alcance reportados como bloqueantes: ${outOfScope.length}`);
  console.log(out.join('\n'));
}

// ── backtest ────────────────────────────────────────────────────────────────────────────
/**
 * Reproduce `diff` sobre PRs ya mergeados: para cada merge M, la rama del PR (M^2) contra lo que
 * había antes (M^1), en un worktree temporal. Mide lo que cada regla habría marcado en su momento
 * y cuánto tarda, y deja un TSV para etiquetar una muestra (real / intencional / falso). Así se
 * calibra una regla o un umbral con evidencia antes de confiar en él.
 */
function cmdBacktest() {
  if (args.labels) return backtestLabels(args.labels);
  const ref = args.ref || 'origin/main';
  const last = Number(args.last || 30);
  const merges = git(['log', '--merges', '--first-parent', ref, '--format=%H%x09%s', `-n${last * 2}`]).split('\n')
    .filter((l) => /Merge (pull request #\d+|branch )/.test(l)).slice(0, last)
    .map((l) => ({ sha: l.split('\t')[0], pr: l.match(/#\d+/)?.[0] ?? l.split('\t')[1].slice(0, 40) }));
  const wt = join(tmpdir(), `am-backtest-${process.pid}`);
  git(['worktree', 'add', '-q', '--detach', wt, ref]);
  const script = fileURLToPath(import.meta.url);
  const results = [];
  try {
    for (const { sha, pr } of merges) {
      execFileSync('git', ['-C', wt, 'checkout', '-q', '--detach', `${sha}^2`], { stdio: 'ignore' });
      const t0 = Date.now();
      const r = spawnSync(process.execPath, [script, 'diff', '--base', `${sha}^1`, '--json'], { cwd: wt, encoding: 'utf8', maxBuffer: 64 << 20 });
      const ms = Date.now() - t0;
      try { results.push({ pr, ms, ...JSON.parse(r.stdout) }); } catch { results.push({ pr, ms, error: (r.stderr || '').split('\n')[0] }); }
      process.stderr.write('.');
    }
  } finally {
    git(['worktree', 'remove', '--force', wt], true);
    process.stderr.write('\n');
  }

  const ok = results.filter((r) => !r.error);
  const pct = (n) => `${Math.round((100 * n) / Math.max(ok.length, 1))}%`;
  const quantile = (xs, q) => [...xs].sort((a, b) => a - b)[Math.min(xs.length - 1, Math.floor(q * xs.length))] ?? 0;
  const reasonKinds = new Map();
  for (const r of ok) for (const why of new Set(r.reasons.map((x) => x.replace(/^\d+ /, '').replace(/ > \d+$/, '').replace(/^ruta sensible: /, 'sensible: ')))) reasonKinds.set(why, (reasonKinds.get(why) || 0) + 1);

  const byRule = new Map();
  for (const r of ok) for (const a of r.alerts) {
    const e = byRule.get(a.rule) ?? byRule.set(a.rule, { n: 0, prs: new Set(), sample: [] }).get(a.rule);
    e.n++; e.prs.add(r.pr); if (e.sample.length < 2) e.sample.push(`${r.pr} ${a.file.split('/').pop()}:${a.line} ${a.msg.slice(0, 70)}`);
  }
  const lines = ok.map((r) => r.lines), files = ok.map((r) => r.files);
  const deepWith = (L, F, both = true) => ok.filter((r) => r.lines > L || r.files > F || (both && r.packages.length > 1 && r.lines > BOTH_LINES) || r.sensitive.length).length;

  const out = [
    `backtest: ${ok.length} PRs de ${ref}${results.length > ok.length ? ` (${results.length - ok.length} con error)` : ''} · ${(ok.reduce((s, r) => s + r.ms, 0) / 1000 / Math.max(ok.length, 1)).toFixed(1)} s/PR (máx ${(Math.max(...ok.map((r) => r.ms)) / 1000).toFixed(1)} s)`,
    `profundidad: deep ${ok.filter((r) => r.depth === 'deep').length} (${pct(ok.filter((r) => r.depth === 'deep').length)}) · lite ${ok.filter((r) => r.depth === 'lite').length}`,
    `  motivos: ${[...reasonKinds].sort((a, b) => b[1] - a[1]).map(([k, n]) => `${k} ${n}`).join(' · ')}`,
    `  tamaño: líneas p50 ${quantile(lines, 0.5)} · p75 ${quantile(lines, 0.75)} · p90 ${quantile(lines, 0.9)} | archivos p50 ${quantile(files, 0.5)} · p75 ${quantile(files, 0.75)} · p90 ${quantile(files, 0.9)}`,
    `  deep si los límites fueran: ${[[LIMITS.lines, LIMITS.files], [quantile(lines, 0.75), quantile(files, 0.75)], [quantile(lines, 0.9), quantile(files, 0.9)]]
      .map(([L, F]) => `${L} líneas/${F} archivos → ${pct(deepWith(L, F))}`).join(' · ')}`,
    `alertas: ${ok.reduce((s, r) => s + r.alerts.length, 0)} (${(ok.reduce((s, r) => s + r.alerts.length, 0) / Math.max(ok.length, 1)).toFixed(1)} por PR)`,
    ...[...byRule].sort((a, b) => b[1].n - a[1].n).map(([rule, e]) => `  ${rule.padEnd(26)} ${String(e.n).padStart(4)} en ${String(e.prs.size).padStart(3)} PRs   p.ej. ${e.sample[0]}`),
  ];
  mkdirSync(join(ROOT, WORK), { recursive: true });
  const tsv = `${WORK}/backtest-${new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '')}.tsv`;
  writeFileSync(join(ROOT, tsv), ['pr\trule\tfile\tline\tmsg\tlabel(real|intencional|falso)',
    ...ok.flatMap((r) => r.alerts.map((a) => [r.pr, a.rule, a.file, a.line, a.msg, ''].join('\t')))].join('\n') + '\n');
  writeFileSync(join(ROOT, tsv.replace(/\.tsv$/, '.json')), JSON.stringify(ok));
  out.push(`detalle para etiquetar: ${tsv} (datos crudos en .json, para simular umbrales sin volver a correr)`);
  console.log(out.join('\n'));
}

/** Precisión por regla a partir de un TSV del backtest con la columna `label` llena. */
function backtestLabels(file) {
  const rows = readFileSync(join(ROOT, file), 'utf8').split('\n').slice(1).filter(Boolean).map((l) => l.split('\t'));
  const by = new Map();
  for (const [, rule, , , , label] of rows) {
    const l = (label || '').trim().toLowerCase();
    const r = by.get(rule) ?? by.set(rule, { real: 0, intencional: 0, falso: 0, sin: 0 }).get(rule);
    if (l in r) r[l]++; else r.sin++;
  }
  console.log(['regla                       real  intenc.  falso  sin etiqueta  precisión', ...[...by].map(([rule, r]) => {
    const labeled = r.real + r.intencional + r.falso;
    return `${rule.padEnd(26)} ${String(r.real).padStart(5)} ${String(r.intencional).padStart(8)} ${String(r.falso).padStart(6)} ${String(r.sin).padStart(13)}  ${labeled ? `${Math.round((100 * (r.real + r.intencional)) / labeled)}%` : '—'}`;
  })].join('\n'));
}

// ── utilidades ──────────────────────────────────────────────────────────────────────────
function run(cwd, argv, env = {}, keepStdout = false) {
  const quoted = argv.map((a) => (/[\s()&]/.test(a) ? `"${a}"` : a));
  const r = spawnSync(quoted.join(' '), { cwd, shell: true, encoding: 'utf8', maxBuffer: 256 << 20, env: { ...quietEnv(), ...env } });
  const res = { status: r.status ?? 1, text: stripAnsi(`${r.stdout || ''}\n${r.stderr || ''}`) };
  if (keepStdout) res.stdout = stripAnsi(r.stdout || '');
  return res;
}

const quietEnv = () => ({ ...process.env, FORCE_COLOR: '0', NO_COLOR: '1', CI: '1' });
const stripAnsi = (s) => s.replace(/\x1b\[[0-9;]*m/g, '');
const currentBranch = () => git(['branch', '--show-current']).trim();
const packagesOf = (paths) => [...new Set(paths.map(packageOf).filter(Boolean))];

function sensitiveHits(paths) {
  const hits = new Set();
  for (const p of paths) for (const [re, label] of SENSITIVE) if (re.test(p)) hits.add(label);
  return [...hits];
}

function git(argv, soft = false) {
  try {
    return execFileSync('git', argv, { cwd: ROOT, encoding: 'utf8', maxBuffer: 64 << 20, stdio: ['ignore', 'pipe', 'ignore'] });
  } catch {
    if (soft) return null;
    console.error(`git ${argv.join(' ')} falló`);
    process.exit(2);
  }
}

function lineCount(p) {
  try { return readFileSync(join(ROOT, p), 'utf8').split('\n').length; } catch { return 0; }
}

function parseArgs(list) {
  const out = { _: [] };
  for (let i = 0; i < list.length; i++) {
    const key = list[i].startsWith('--') ? list[i].slice(2) : null;
    if (key && BOOLEAN_FLAGS.has(key)) out[key] = true;
    else if (key) out[key] = list[i + 1] === undefined || list[i + 1].startsWith('--') ? true : list[++i];
    else out._.push(list[i]);
  }
  return out;
}

function norm(p) { return p.replace(/\\/g, '/'); }
function num(s) { return s === '-' ? 0 : Number(s); }

// ── entrada (al final: las constantes de arriba ya están inicializadas) ─────────────────
const [mode, ...rest] = process.argv.slice(2);
const args = parseArgs(rest);
const COMMANDS = {
  diff: cmdDiff, test: () => cmdTest(args._), fix: cmdFix, outline: () => cmdOutline(norm(args._[0] || '')),
  migration: () => cmdMigration(args._[0]), pr: cmdPr, gate: () => cmdGate(args._[0]),
  tasks: () => cmdTasks(args._[0]), exec: cmdExec, backtest: cmdBacktest,
  docs: () => cmdDocs(args._), naming: () => cmdNaming(args._), debt: cmdDebt, impact: () => cmdImpact(args._), proposals: cmdProposals, overlap: () => cmdOverlap(args._), session: () => cmdSession(args._[0], args._[1], args._.slice(2)),
  convert: () => cmdConvert(args._[0], args._[1]), ledger: () => cmdLedger(args._[0], args._[1]),
};
if (COMMANDS[mode]) COMMANDS[mode]();
else { console.error(`uso: am.mjs ${Object.keys(COMMANDS).join('|')} (ver la cabecera del archivo)`); process.exit(2); }
