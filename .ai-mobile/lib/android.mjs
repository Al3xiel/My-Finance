/**
 * Perfil genérico de una app Android nativa (Kotlin, Gradle con Kotlin DSL, Compose, Room, Hilt).
 * Todo lo que depende del stack vive aquí; lo propio de cada app (paquete, features, docs y reglas
 * de dominio) viene del perfil del proyecto (lib/profile.mjs). El resto de las herramientas
 * (sesiones, planes, cruces, deuda, ledger) es genérico.
 *
 * Estructura que se asume (la del curso y la de la guía de programación de cada proyecto):
 *   app/src/main/java/<paquete>/{core,feature/<f>/{ui,domain,data},data}/…
 *   app/src/test/java/…          tests JVM (JUnit, corrutinas, Turbine)
 *   app/src/androidTest/java/…   tests instrumentados (Room, migraciones, Compose UI)
 *   app/schemas/<Database>/<n>.json   esquemas exportados por Room
 */
import { existsSync, readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { PROFILE, PROFILE_SENSITIVE, PROFILE_SECURITY_SURFACE } from './profile.mjs';

export { PROFILE };
export const MODULE = PROFILE.appModule ?? 'app';

/**
 * El paquete real sale de `namespace` en app/build.gradle.kts cuando el proyecto ya existe; si no,
 * el del perfil. `root` es la raíz del repositorio (la pasa quien llama; sin ella, solo el perfil).
 */
export function packagePath(root) {
  const gradle = root && [`${root}/${MODULE}/build.gradle.kts`, `${root}/${MODULE}/build.gradle`].find((f) => existsSync(f));
  const ns = gradle ? readFileSync(gradle, 'utf8').match(/namespace\s*=?\s*["']([\w.]+)["']/)?.[1] : null;
  return (ns ?? PROFILE.package).replace(/\./g, '/');
}

const ROOT_GUESS = process.env.AM_ROOT || (() => {
  try { return execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim(); } catch { return null; }
})();
export const SRC_ROOT = (root = ROOT_GUESS) => `${MODULE}/src/main/java/${packagePath(root)}/`;
export const TEST_ROOT = `${MODULE}/src/test/`;
export const ANDROID_TEST_ROOT = `${MODULE}/src/androidTest/`;
export const SCHEMAS = `${MODULE}/schemas/`;
export const MANIFEST = `${MODULE}/src/main/AndroidManifest.xml`;

export const packageOf = (p) => (p.startsWith(`${MODULE}/`) ? MODULE : /^(build-logic|gradle)\//.test(p) ? 'build' : null);
export const isTest = (p) => /\/src\/(test|androidTest)\//.test(p);
/** Lo que cuenta como código para el tamaño de un cambio. */
export const isSource = (p) => /^[\w-]+\/src\/main\/.*\.(kt|java|xml)$/.test(p) && !isTest(p);
/** Ruido local o de las herramientas: nunca es parte del cambio. */
export const IGNORED = /^(\.idea|\.vscode|\.gradle|\.kotlin|\.claude|\.ai-mobile|build|[\w-]+\/build|local\.properties$)(\/|$)/;

/** Por encima de cualquiera, la auditoría pasa a `deep`. */
export const LIMITS = { lines: 600, files: 15, ...(PROFILE.limits ?? {}) };
/** Límites del carril rápido. */
export const QUICK = { tasks: 4, files: 8 };
/** Un archivo más largo no se lee entero: `outline` y lectura por rango. Guía: archivo ≤ ~300 líneas. */
export const LARGE_FILE = 300;

/** Rutas cuyo cambio siempre merece la auditoría profunda y saca un plan del carril rápido. */
export const SENSITIVE = [
  [/(^|\/)schemas\/.*\.json$|\/(migration|migrations)\/|Migration\w*\.kt$/, 'migración Room'],
  [/AndroidManifest\.xml$/, 'manifest (permisos, backup, exportados)'],
  [/(^|\/)(build\.gradle(\.kts)?|libs\.versions\.toml|settings\.gradle(\.kts)?|gradle\.properties|proguard-rules\.pro)$/, 'build / dependencias'],
  [/\/core\/security\/|Keystore|Biometric|Cipher|Crypto/i, 'seguridad (cifrado, bloqueo, claves)'],
  [/\/res\/xml\/(data_extraction_rules|backup_rules|file_paths)\.xml$/, 'respaldo / archivos compartidos'],
  [/^\.github\/workflows\//, 'CI'],
  ...PROFILE_SENSITIVE,
];
/** Superficie que pide la revisión de seguridad dedicada cuando se toca. */
export const SECURITY_SURFACE = new Set(['manifest (permisos, backup, exportados)', 'seguridad (cifrado, bloqueo, claves)', 'respaldo / archivos compartidos', ...PROFILE_SECURITY_SURFACE]);

// ── features ───────────────────────────────────────────────────────────────────────────
/** Feature de una ruta: `<paquete>/feature/<f>/…`, o `core/<x>` y `data/<x>` como piezas transversales. */
export function featuresOf(p, root = ROOT_GUESS) {
  const src = SRC_ROOT(root);
  if (!p.startsWith(src)) {
    const t = p.match(/\/src\/(?:test|androidTest)\/java\/(?:[\w]+\/)*?feature\/([\w]+)\//);
    return t ? [t[1]] : [];
  }
  const rel = p.slice(src.length).split('/');
  if (rel[0] === 'feature' && rel[1]) return [rel[1]];
  if ((rel[0] === 'core' || rel[0] === 'data') && rel[1] && !rel[1].endsWith('.kt')) return [`${rel[0]}/${rel[1]}`];
  return [];
}

// ── tests ──────────────────────────────────────────────────────────────────────────────
const className = (p) => p.split('/').pop().replace(/\.(kt|java)$/, '');

/**
 * Tests relacionados: `Foo.kt` → `FooTest.kt` en src/test o src/androidTest (cualquier carpeta).
 * Falta test en lo que la guía exige probar: casos de uso, reglas, proyectores, ViewModels, migraciones.
 */
export function relatedTests(paths, tracked, exists) {
  const unit = new Set(), instrumented = new Set(), missing = [];
  const tests = tracked.filter((f) => isTest(f) && /\.(kt|java)$/.test(f));
  for (const p of paths) {
    if (!/\.(kt|java)$/.test(p) || !p.startsWith(`${MODULE}/src/`)) continue;
    if (isTest(p)) { if (exists(p)) (p.includes('/androidTest/') ? instrumented : unit).add(className(p)); continue; }
    const name = className(p);
    const hits = tests.filter((t) => new RegExp(`/${name}(Test|Tests|IT)\\.(kt|java)$`).test(t));
    for (const h of hits) (h.includes('/androidTest/') ? instrumented : unit).add(className(h));
    if (!hits.length && /(UseCase|ViewModel|Projector|Upcaster|Repository(Impl)?|Migration\w*)\.kt$|\/domain\/(rules|model)\//.test(p)) missing.push(p);
  }
  return { unit: [...unit], instrumented: [...instrumented], missing };
}

const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

/** Tests JVM por clase; los instrumentados necesitan un emulador o dispositivo conectado. */
export function testCommands({ unit, instrumented }) {
  const out = [];
  if (unit.length) out.push({ tool: 'gradle', argv: [gradlew, '-q', '--console=plain', `:${MODULE}:testDebugUnitTest`, ...unit.flatMap((c) => ['--tests', `*${c}`])] });
  if (instrumented.length) {
    out.push({ tool: 'gradle', note: 'tests instrumentados: necesitan un emulador o dispositivo conectado (adb devices)',
      argv: [gradlew, '-q', '--console=plain', `:${MODULE}:connectedDebugAndroidTest`, `-Pandroid.testInstrumentationRunnerArguments.class=${instrumented.map((c) => `*${c}`).join(',')}`] });
  }
  return out;
}

/** Compilación sin tests (Kotlin + KSP): lo primero que rompe un cambio. */
export const typecheckCommand = () => ({ tool: 'gradle', argv: [gradlew, '-q', '--console=plain', `:${MODULE}:compileDebugKotlin`] });

/**
 * Formato y lint automáticos según lo que el proyecto tenga configurado: ktlint (plugin
 * org.jlleitschuh o kotlinter) o detekt con autoCorrect. Sin ninguno, solo Android Lint (no arregla).
 */
export function fixCommands(root) {
  const build = ['build.gradle.kts', `${MODULE}/build.gradle.kts`, 'gradle/libs.versions.toml'].map((f) => (root && existsSync(`${root}/${f}`) ? readFileSync(`${root}/${f}`, 'utf8') : '')).join('\n');
  if (/ktlint/.test(build)) return [{ tool: 'gradle', argv: [gradlew, '-q', '--console=plain', 'ktlintFormat'] }];
  if (/kotlinter/.test(build)) return [{ tool: 'gradle', argv: [gradlew, '-q', '--console=plain', 'formatKotlin'] }];
  if (/detekt/.test(build)) return [{ tool: 'gradle', argv: [gradlew, '-q', '--console=plain', 'detekt', '--auto-correct'] }];
  return [{ tool: 'gradle', argv: [gradlew, '-q', '--console=plain', `:${MODULE}:lintDebug`], note: 'sin ktlint ni detekt configurados: solo Android Lint (no corrige)' }];
}

/** Lo único que el filtro de salida acepta reescribir: una tarea de Gradle de test/compilación/lint. */
export const SAFE_EXEC = /^\s*(\.\/gradlew|gradlew(\.bat)?|\.\\gradlew(\.bat)?)(\s+(-q|--quiet|--console=plain|--offline|--continue|--auto-correct|--tests\s+['"]?[\w.*]+['"]?|-P[\w.=*,:-]+|(:[\w-]+)*:?(test\w*|connected\w*AndroidTest|compile\w*Kotlin|lint\w*|ktlint\w*|detekt\w*|formatKotlin|lintKotlin|check)))+\s*$/;

export function toolOf(command) {
  return /gradlew/.test(command) ? 'gradle' : 'salida';
}

// ── migraciones (Room) ─────────────────────────────────────────────────────────────────
/** La versión de la base declarada en `@Database(…, version = N)` y el archivo que la declara. */
export function databaseVersion(files, read) {
  for (const f of files.filter((x) => x.endsWith('.kt') && !isTest(x))) {
    const text = read(f) ?? '';
    const m = text.match(/@Database\s*\([\s\S]*?version\s*=\s*(\d+)/);
    if (m) return { file: f, version: Number(m[1]), name: text.match(/abstract\s+class\s+(\w+)/)?.[1] ?? 'AppDatabase' };
  }
  return null;
}

// ── PR ─────────────────────────────────────────────────────────────────────────────────
/** URL de comparación a partir del remoto `origin` (GitHub), o null si no hay. */
export function compareUrl(remote, base, branch) {
  const repo = remote?.match(/github\.com[:/]([^/]+\/[^/.\s]+?)(\.git)?\s*$/)?.[1];
  return repo ? `https://github.com/${repo}/compare/${base}...${branch}?expand=1` : null;
}
