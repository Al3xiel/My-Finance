/**
 * Reglas mecánicas de Android (Kotlin, Compose, Room, Hilt, corrutinas) más las del perfil del
 * proyecto. Cada una es una búsqueda barata sobre las líneas que agrega el diff; el modelo no las
 * busca: confirma o descarta cada alerta con una razón en /am-audit. Las que se equivocan mucho se
 * afinan con `am.mjs ledger report`; un hallazgo de criterio que se repite se promueve a regla.
 *
 *   fileAlerts({ file, text, added, isNew })                 un archivo y sus líneas nuevas
 *   changeAlerts({ files, added, removed, read, tracked })   el cambio entero (Room, manifest, tests)
 *
 * Las reglas genéricas salen de los errores que la guía del curso ya documentó en proyectos viejos
 * (H1–H20 de la guía de programación) y de la documentación oficial de Android.
 */
import { isTest, MODULE, SCHEMAS, databaseVersion } from './android.mjs';
import { projectFileAlerts, projectChangeAlerts, PROJECT_SECURITY_RULES } from './profile.mjs';

export const SECURITY_RULES = new Set(['permission-added', 'exported-component', 'destructive-migration', 'shared-preferences', ...PROJECT_SECURITY_RULES]);

const inLayer = (file, layer) => new RegExp(`/${layer}/`).test(file);
/** Quita strings y comentarios de una línea, para no marcar `!!` dentro de un texto. */
const code = (line) => line.replace(/"(?:\\.|[^"\\])*"/g, '""').replace(/\/\/.*$/, '');

export function fileAlerts(args) {
  const { file } = args;
  if (isTest(file)) return [...testAlerts(file, args.added), ...projectFileAlerts(args)];
  const out = file.endsWith('.kt') ? kotlinAlerts(args)
    : /\.gradle(\.kts)?$|libs\.versions\.toml$/.test(file) ? buildAlerts(args)
    : file.endsWith('AndroidManifest.xml') ? manifestAlerts(args)
    : [];
  return [...out, ...projectFileAlerts(args)];
}

// ── Kotlin ─────────────────────────────────────────────────────────────────────────────
function kotlinAlerts({ file, text, added }) {
  const out = [];
  const push = (rule, line, msg) => out.push({ rule, line, msg });
  const isViewModel = /ViewModel\.kt$/.test(file);
  const isDao = /Dao\.kt$/.test(file) || /@Dao\b/.test(text);
  const isDomain = inLayer(file, 'domain');
  const isUi = inLayer(file, 'ui') || /Screen\.kt$/.test(file);
  const isDi = inLayer(file, 'di') || /Module\.kt$/.test(file);

  for (const { n, line } of added) {
    const c = code(line);
    // Un import solo cuenta para las reglas de capas; el uso se marca donde ocurre.
    if (/^\s*import\b/.test(line)) {
      if (isViewModel && /^import\s+[\w.]+\.(data\.|.*Dao\b|.*Database\b|.*EventStore\b)/.test(line)) push('viewmodel-skips-usecase', n, 'el ViewModel usa la capa de datos directo (H8): ViewModel → UseCase → Repository');
      if (isDomain && /^import\s+(android\.|androidx\.(?!annotation\.)|dagger\.hilt\.android)/.test(line)) push('domain-android-import', n, 'domain/ es Kotlin puro (sin Context, Room ni Compose): esto va en data/ o ui/');
      if (/^import\s+com\.google\.gson/.test(line)) push('gson', n, 'Gson: kotlinx.serialization');
      continue;
    }
    if (/\bGlobalScope\b/.test(c)) push('global-scope', n, 'GlobalScope no se cancela nunca (H1): viewModelScope o un scope inyectado');
    if (/!!/.test(c)) push('not-null-assertion', n, '`!!` revienta en runtime (H15): ?., ?: con un error de dominio, o requireNotNull(x) { "motivo" }');
    if (/\brunBlocking\b/.test(c)) push('run-blocking', n, 'runBlocking bloquea el hilo: suspend o un scope');
    if (/fallbackToDestructiveMigration/.test(c)) push('destructive-migration', n, 'borra todos los datos del usuario al subir de versión (H11): una Migration explícita y su test');
    if (/getSharedPreferences|PreferenceManager\.|\bSharedPreferences\b/.test(c)) push('shared-preferences', n, 'SharedPreferences en texto plano (H10): DataStore para ajustes; nada sensible fuera del Keystore');
    if (/\b(Log\.(v|d|i|w|e|wtf)|println)\(/.test(c)) push('log-call', n, 'log en código de la app (H9): solo en debug y sin datos del usuario');
    if (/\.collectAsState\(/.test(c)) push('collect-without-lifecycle', n, 'collectAsState sigue recolectando en segundo plano: collectAsStateWithLifecycle()');
    if (/\b(Mutable)?LiveData\b/.test(c)) push('livedata', n, 'LiveData: el ViewModel expone StateFlow<UiState>');
    if (/^\s*package\s+com\.example\b/.test(line)) push('example-package', n, 'paquete com.example (H17): el paquete propio del proyecto');
    if (/\bcomposable\(\s*"|\.navigate\(\s*"/.test(line)) push('string-route', n, 'ruta de navegación como string (H12): rutas tipadas (@Serializable / NavKey)');
    if (/\.ordinal\b/.test(c) && inLayer(file, 'data')) push('enum-ordinal', n, 'el ordinal de un enum cambia si se reordena: guardar el nombre');
    if (/Dispatchers\.(IO|Default|Main|Unconfined)\b/.test(c) && !isDi) push('hardcoded-dispatcher', n, 'Dispatcher fijo: se inyecta (@IoDispatcher) para poder testear');
    if (isViewModel) {
      if (/:\s*(android\.content\.)?Context\b/.test(c)) push('context-in-viewmodel', n, 'el ViewModel no recibe Context (H2): la dependencia llega por Hilt');
      if (/\bmutableStateOf\b/.test(c)) push('mutable-state-in-viewmodel', n, 'mutableStateOf en el ViewModel (H16): MutableStateFlow privado + StateFlow<UiState> público');
      if (/^\s*(val|var)\s+\w+\s*=\s*MutableStateFlow\b/.test(line)) push('exposed-mutable-flow', n, 'MutableStateFlow público: private val _state + val state: StateFlow');
    }
    if (isDao && /^\s*(abstract\s+)?fun\s+\w+\s*\(/.test(line) && !/\bsuspend\b/.test(line) && !/:\s*(Flow|PagingSource|Cursor)\b/.test(line)) {
      push('blocking-dao', n, 'función de DAO bloqueante (H3): suspend para escribir, Flow para leer');
    }
    if (/@Volatile/.test(c) && /RoomDatabase|INSTANCE/.test(text)) push('manual-db-singleton', n, 'singleton manual de la base (H5): la provee un módulo Hilt @Singleton');
    if (isUi && /\bText\(\s*(text\s*=\s*)?"[^"]*\p{L}{2,}/u.test(line)) push('hardcoded-ui-text', n, 'texto de UI escrito en el código (H14): stringResource(R.string.…) en res/values/strings.xml');
    if ((isDomain || /(Entity|UiState)\.kt$/.test(file)) && /(\(|,)\s*var\s+\w+\s*:|^\s*var\s+\w+\s*:/.test(line) && /data class|class /.test(text)) {
      push('mutable-model', n, 'var en un modelo o entidad (H6): data class con val; un cambio es una copia');
    }
  }
  return out;
}

// ── build y manifest ───────────────────────────────────────────────────────────────────
function buildAlerts({ added }) {
  const out = [];
  for (const { n, line } of added) {
    if (/\bkapt\b|kotlin-kapt/.test(line)) out.push({ rule: 'kapt', line: n, msg: 'kapt está deprecado y Room 3 solo admite KSP (H4)' });
    if (/accompanist/i.test(line)) out.push({ rule: 'accompanist', line: n, msg: 'Accompanist está en retiro (H20): la API nativa de AndroidX' });
    if (/isMinifyEnabled\s*=\s*false|minifyEnabled\s+false/.test(line)) out.push({ rule: 'minify-off', line: n, msg: 'si es el bloque release: R8 activado (H18)' });
    if (/fallbackToDestructiveMigration/.test(line)) out.push({ rule: 'destructive-migration', line: n, msg: 'borra los datos al migrar (H11)' });
  }
  return out;
}

function manifestAlerts({ added }) {
  const out = [];
  for (const { n, line } of added) {
    const perm = line.match(/<uses-permission[^>]*android:name="([^"]+)"/);
    if (perm) out.push({ rule: 'permission-added', line: n, msg: `permiso nuevo ${perm[1]}: ¿lo usa una funcionalidad y se pide en contexto? (mínimo necesario)` });
    if (/android:exported="true"/.test(line)) out.push({ rule: 'exported-component', line: n, msg: 'componente exportado: otra app puede abrirlo; solo la Activity de entrada' });
  }
  return out;
}

// ── tests ──────────────────────────────────────────────────────────────────────────────
function testAlerts(file, added) {
  const out = [];
  for (const { n, line } of added) {
    if (/@Ignore\b|@Disabled\b/.test(line) && !/(@Ignore|@Disabled)\(\s*"[^"]{6,}/.test(line)) out.push({ rule: 'ignored-test-without-reason', line: n, msg: 'test deshabilitado sin motivo: @Ignore("motivo y qué lo habilita")' });
    if (/Thread\.sleep\(/.test(line)) out.push({ rule: 'sleep-in-test', line: n, msg: 'Thread.sleep en un test: runTest + advanceUntilIdle, o Turbine' });
  }
  return out;
}

// ── el cambio entero ───────────────────────────────────────────────────────────────────
/**
 *   files:   Map path → { add, del, isNew }
 *   added:   (path) → [{ n, line }]   líneas nuevas · removed: (path) → [line] líneas quitadas
 *   read:    (path) → texto | null    tracked: string[] (todo el repo)
 */
export function changeAlerts(args) {
  const { files, added, read, tracked } = args;
  const out = [];
  const paths = [...files.keys()];

  // Room: un esquema exportado de una versión pasada es el contrato de su migración.
  for (const p of paths.filter((x) => x.startsWith(SCHEMAS) && x.endsWith('.json') && !files.get(x).isNew)) {
    out.push({ rule: 'schema-edited', file: p, line: 1, msg: 'esquema exportado de una versión ya publicada editado: la versión nueva va en su propio <n>.json y su Migration' });
  }
  const migrationsAdded = paths.filter((p) => files.get(p).isNew && /Migration\w*\.kt$|\/migrations?\//.test(p) && !isTest(p));
  const entities = paths.filter((p) => /\.kt$/.test(p) && !isTest(p) && (/Entity\.kt$/.test(p) || /@Entity\b/.test(read(p) ?? ''))
    && added(p).some(({ line }) => /@(Entity|ColumnInfo|PrimaryKey|Embedded|Relation|ForeignKey|Index)\b|^\s*val\s+\w+\s*:/.test(line)));
  const db = databaseVersion(tracked.filter((f) => f.startsWith(`${MODULE}/src/main/`)), read);
  const versionBumped = db && added(db.file).some(({ line }) => /version\s*=\s*\d+/.test(line)) && !files.get(db.file)?.isNew;
  const existingEntities = entities.filter((p) => !files.get(p).isNew || tracked.includes(p));
  if (existingEntities.length && !versionBumped && db && !files.get(db.file)?.isNew) {
    out.push({ rule: 'entity-without-migration', file: existingEntities[0], line: 1, msg: `cambia el esquema (${existingEntities.length} entidad(es)) sin subir version en @Database (${db.file.split('/').pop()}): Room falla al abrir la base → am.mjs migration <nombre>` });
  }
  if (versionBumped && !migrationsAdded.length && !added(db.file).some(({ line }) => /AutoMigration/.test(line))) {
    out.push({ rule: 'version-without-migration', file: db.file, line: 1, msg: 'sube la versión de la base sin Migration ni AutoMigration: los usuarios con la versión anterior no pueden abrirla' });
  }
  const migrationTests = paths.filter((p) => isTest(p) && /Migration\w*Test\.kt$/.test(p));
  if (migrationsAdded.length && !migrationTests.length) {
    out.push({ rule: 'migration-without-test', file: migrationsAdded[0], line: 1, msg: 'migración sin test: MigrationTestHelper en androidTest abre la versión anterior, migra y valida' });
  }

  // Compose: cada componente reutilizable con su @Preview (guía §5.5).
  for (const p of paths.filter((x) => files.get(x).isNew && /\/ui\/.*\.kt$/.test(x) && !/ViewModel|UiState|UiAction|Route|Navigation/.test(x))) {
    const text = read(p) ?? '';
    if (/@Composable/.test(text) && !/@Preview/.test(text)) out.push({ rule: 'preview-missing', file: p, line: 1, msg: 'componente nuevo sin @Preview (claro/oscuro): sin preview no se revisa ni se prueba el estado sin ViewModel' });
  }
  return [...out, ...projectChangeAlerts(args)];
}
