// Tests de las reglas: cada una con un caso que la dispara y uno que no. Correr con
// `node --test .ai-mobile/test/` en el proyecto, o `npm test` en AI-Mobile-Skills.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync, execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, mkdirSync, cpSync, chmodSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fileAlerts, changeAlerts } from '../lib/rules.mjs';
import { checkNewPath } from '../lib/naming.mjs';
import { debtAlerts, layerViolations } from '../lib/debt.mjs';
import { SAFE_EXEC, relatedTests, featuresOf, databaseVersion, compareUrl, SRC_ROOT } from '../lib/android.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const S = SRC_ROOT();
const F = `${S}feature/movements/`;
const lines = (text) => text.split('\n').map((line, i) => ({ n: i + 1, line }));
const rules = (file, text) => fileAlerts({ file, text, added: lines(text) }).map((a) => a.rule);

test('ViewModel: GlobalScope, Context, mutableStateOf, !! y acceso directo a datos', () => {
  const text = [
    'import pe.gcasas.finanzas.data.database.AppDatabase',
    'import kotlinx.coroutines.GlobalScope',
    'class BudgetViewModel(private val context: Context) : ViewModel() {',
    '    val state = mutableStateOf(0)',
    '    fun load() { GlobalScope.launch { repo.get()!! } }',
    '}',
  ].join('\n');
  const r = rules(`${F}ui/BudgetViewModel.kt`, text);
  for (const rule of ['viewmodel-skips-usecase', 'context-in-viewmodel', 'mutable-state-in-viewmodel', 'global-scope', 'not-null-assertion']) assert.ok(r.includes(rule), `${rule} en ${r}`);
  // Un import solo cuenta para las capas: GlobalScope una vez (el uso), no dos.
  assert.equal(r.filter((x) => x === 'global-scope').length, 1);
});

test('`!!` dentro de un string no es una aserción', () => {
  assert.deepEqual(rules(`${F}ui/BudgetScreen.kt`, 'val msg = stringResource(R.string.alert) + "!!"'), []);
});

test('DAO: bloqueante sí; suspend y Flow no', () => {
  const dao = (l) => rules(`${F}data/MovementDao.kt`, `@Dao\ninterface MovementDao {\n${l}\n}`);
  assert.ok(dao('    fun all(): List<MovementEntity>').includes('blocking-dao'));
  assert.deepEqual(dao('    fun observe(): Flow<List<MovementEntity>>'), []);
  assert.deepEqual(dao('    suspend fun insert(e: MovementEntity)'), []);
});

test('Compose: texto escrito en el código y rutas como string', () => {
  assert.deepEqual(rules(`${F}ui/BudgetScreen.kt`, 'Text("Presupuesto mensual")'), ['hardcoded-ui-text']);
  assert.deepEqual(rules(`${F}ui/BudgetScreen.kt`, 'Text(stringResource(R.string.budget_title))'), []);
  assert.deepEqual(rules(`${F}ui/MovementsNavigation.kt`, 'composable("movements/{id}") { }'), ['string-route']);
});

test('dominio: Kotlin puro, sin Android ni reloj directo', () => {
  assert.deepEqual(rules(`${F}domain/model/Budget.kt`, 'import android.content.Context'), ['domain-android-import']);
  assert.deepEqual(rules(`${F}domain/usecase/RegisterExpenseUseCase.kt`, 'val today = LocalDate.now()'), ['clock-direct']);
  assert.deepEqual(rules(`${F}domain/usecase/RegisterExpenseUseCase.kt`, 'val today = LocalDate.now(clock)'), []);
});

test('dinero: Double/Float prohibido; Long en céntimos sí', () => {
  assert.ok(rules(`${F}domain/model/Budget.kt`, 'data class Budget(val limit: Double)').includes('money-floating'));
  assert.ok(rules(`${F}domain/model/Budget.kt`, 'val m = Money(12.50, PEN)').includes('money-floating'));
  assert.deepEqual(rules(`${F}domain/model/Budget.kt`, 'data class Budget(val limitMinor: Long)'), []);
  assert.deepEqual(rules(`${F}domain/rules/Proportion.kt`, 'val ratio: Double = 0.5'), []); // no es dinero
  assert.deepEqual(rules(`${F}domain/rules/Split.kt`, 'x.setScale(2, RoundingMode.HALF_UP)'), ['rounding-mode']);
});

test('registro de eventos append-only y proyectores deterministas', () => {
  assert.deepEqual(rules(`${S}data/eventstore/EventDao.kt`, '    @Update\n    suspend fun fix(e: EventEntity)').filter((r) => r === 'events-mutation'), ['events-mutation']);
  assert.deepEqual(rules(`${S}data/eventstore/EventDao.kt`, '    @Insert\n    suspend fun append(e: EventEntity)'), []);
  assert.ok(rules(`${F}data/BalanceProjector.kt`, 'val id = UUID.randomUUID()').includes('projector-nondeterministic'));
  assert.ok(rules(`${F}data/BalanceProjector.kt`, 'val at = Instant.now()').includes('projector-nondeterministic'));
});

test('privacidad: INTERNET, backup, permisos, red y logs con datos', () => {
  const m = 'app/src/main/AndroidManifest.xml';
  assert.ok(rules(m, '<uses-permission android:name="android.permission.INTERNET" />').includes('internet-permission'));
  assert.ok(rules(m, '<application android:allowBackup="true">').includes('allow-backup'));
  assert.ok(rules(m, '<uses-permission android:name="android.permission.READ_CONTACTS" />').includes('sensitive-permission'));
  assert.deepEqual(rules(m, '<uses-permission android:name="android.permission.USE_BIOMETRIC" />'), ['permission-added']);
  assert.ok(rules('gradle/libs.versions.toml', 'retrofit = { module = "com.squareup.retrofit2:retrofit" }').includes('network-dependency'));
  assert.ok(rules(`${F}ui/X.kt`, 'Log.d("tag", "amount=$amountMinor")').includes('sensitive-log'));
  assert.deepEqual(rules(`${F}ui/X.kt`, 'Log.d("tag", "loaded")'), ['log-call']);
});

test('glosario: términos a evitar e identificadores en español', () => {
  assert.ok(rules(`${F}domain/model/X.kt`, 'data class Transaction(val id: String)').includes('forbidden-term'));
  assert.deepEqual(rules(`${F}data/MovementDao.kt`, '    @Transaction\n    suspend fun save(e: MovementEntity)'), []);
  assert.ok(rules(`${F}domain/model/X.kt`, 'data class GastoMensual(val id: String)').includes('spanish-identifier'));
});

test('build: kapt, minify y destructiva', () => {
  assert.deepEqual(rules('app/build.gradle.kts', 'kapt(libs.room.compiler)'), ['kapt']);
  assert.deepEqual(rules('app/build.gradle.kts', 'ksp(libs.room.compiler)'), []);
  assert.deepEqual(rules('app/build.gradle.kts', 'isMinifyEnabled = false'), ['minify-off']);
  assert.ok(rules(`${S}data/database/DatabaseModule.kt`, '.fallbackToDestructiveMigration()').includes('destructive-migration'));
});

test('cambio entero: entidad sin migración, esquema editado, evento cambiado, glosario y docs', () => {
  const db = `${S}data/database/AppDatabase.kt`;
  const entity = `${F}data/MovementEntity.kt`;
  const event = `${F}domain/model/ExpenseRegistered.kt`;
  const budget = `${F}domain/model/Budget.kt`;
  const files = new Map([
    [entity, { isNew: false }], [event, { isNew: false }], [budget, { isNew: true }],
    ['app/schemas/pe.gcasas.finanzas.data.database.AppDatabase/1.json', { isNew: false }],
  ]);
  const text = {
    [db]: '@Database(entities = [MovementEntity::class], version = 1)\nabstract class AppDatabase : RoomDatabase()',
    [entity]: '@Entity(tableName = "movements")\ndata class MovementEntity(@PrimaryKey val id: String, val categoryId: String)',
    [event]: 'data class ExpenseRegistered(val movementId: String, val note: String?) : DomainEvent',
    [budget]: 'data class Budget(val id: String)\ndata class BudgetExceeded(val id: String) : DomainEvent',
    'docs/03-glosario.md': '| Movimiento | `Movement` |\n| `ExpenseRegistered` | Gasto registrado |',
    'docs/04-modelo-datos.md': '# modelo',
  };
  const added = (p) => ({ [entity]: lines('    val categoryId: String,'), [event]: lines('    val note: String?,'), [budget]: lines(text[budget]) }[p] ?? []);
  const out = changeAlerts({ files, added, removed: () => [], read: (p) => text[p] ?? null, tracked: [db, entity, event] }).map((a) => a.rule);
  for (const rule of ['entity-without-migration', 'schema-edited', 'event-schema-changed', 'glossary-missing', 'event-not-cataloged', 'docs-stale']) assert.ok(out.includes(rule), `${rule} en ${out}`);
  assert.equal(out.filter((r) => r === 'event-not-cataloged').length, 1); // Budget no es un evento
});

test('versión subida con su migración y su test: sin alertas de Room', () => {
  const db = `${S}data/database/AppDatabase.kt`;
  const mig = `${S}data/database/migration/Migration1To2.kt`;
  const mtest = 'app/src/androidTest/java/pe/gcasas/finanzas/data/database/migration/Migration1To2Test.kt';
  const files = new Map([[db, { isNew: false }], [mig, { isNew: true }], [mtest, { isNew: true }]]);
  const read = (p) => (p === db ? '@Database(entities = [A::class], version = 2)\nabstract class AppDatabase' : '');
  const out = changeAlerts({ files, added: (p) => (p === db ? lines('@Database(entities = [A::class], version = 2)') : []), read, tracked: [db] }).map((a) => a.rule);
  assert.ok(!out.some((r) => /migration|schema|version/.test(r)), out.join(','));
});

test('estructura: lo de la guía pasa; lo genérico, fuera de capa o de feature desconocida no', () => {
  for (const ok of [`${F}domain/usecase/RegisterExpenseUseCase.kt`, `${F}ui/MovementListScreen.kt`, `${F}ui/components/AmountField.kt`,
    `${F}data/RoomMovementRepository.kt`, `${F}di/MovementsModule.kt`, `${S}core/money/MoneyFormatter.kt`, `${S}data/eventstore/EventStore.kt`, `${S}MainActivity.kt`]) {
    assert.equal(checkNewPath(ok), null, ok);
  }
  assert.match(checkNewPath(`${F}ui/Helpers.kt`).msg, /no dice para qué es/);
  assert.match(checkNewPath(`${F}ui/MovementList.kt`).msg, /XxxScreen/);
  assert.match(checkNewPath(`${F}domain/usecase/RegisterExpense.kt`).msg, /UseCase/);
  assert.match(checkNewPath(`${S}feature/budgets/ui/BudgetScreen.kt`).msg, /feature nueva/);
  assert.match(checkNewPath(`${S}Screens.kt`).msg, /raíz del paquete/);
  assert.equal(checkNewPath('app/src/test/java/pe/gcasas/finanzas/feature/movements/Whatever.kt'), null);
});

test('deuda: capas y archivo largo que crece', () => {
  assert.equal(layerViolations(`${F}domain/model/X.kt`, lines('import pe.gcasas.finanzas.feature.movements.data.MovementEntity')).length, 1);
  assert.equal(layerViolations(`${F}ui/X.kt`, lines('import pe.gcasas.finanzas.feature.movements.domain.model.Movement')).length, 0);
  assert.deepEqual(debtAlerts({ file: `${F}ui/MovementListScreen.kt`, added: lines('x'), size: 340, before: 300, isNew: false }).map((a) => a.rule), ['large-file-growth']);
  assert.deepEqual(debtAlerts({ file: `${F}ui/MovementListScreen.kt`, added: lines('x'), size: 310, before: 300, isNew: false }), []);
});

test('stack: comandos seguros, tests relacionados, features, versión de la base y compare', () => {
  for (const ok of ["./gradlew :app:testDebugUnitTest --tests '*BudgetTest'", './gradlew -q compileDebugKotlin', 'gradlew.bat lintDebug', './gradlew ktlintCheck detekt']) assert.ok(SAFE_EXEC.test(ok), ok);
  for (const bad of ['./gradlew assembleRelease', './gradlew test && rm -rf /', './gradlew installDebug', './gradlew test | cat', 'npm test']) assert.ok(!SAFE_EXEC.test(bad), bad);
  const t = relatedTests([`${F}domain/usecase/RegisterExpenseUseCase.kt`, `${F}ui/BudgetViewModel.kt`],
    ['app/src/test/java/pe/gcasas/finanzas/feature/movements/RegisterExpenseUseCaseTest.kt'], () => true);
  assert.deepEqual(t.unit, ['RegisterExpenseUseCaseTest']);
  assert.deepEqual(t.missing, [`${F}ui/BudgetViewModel.kt`]);
  assert.deepEqual(featuresOf(`${F}ui/X.kt`), ['movements']);
  assert.deepEqual(featuresOf(`${S}core/money/Money.kt`), ['core/money']);
  assert.equal(databaseVersion(['a/src/main/A.kt'], () => '@Database(entities = [X::class], version = 7)\nabstract class AppDatabase').version, 7);
  assert.equal(compareUrl('https://github.com/Al3xiel/finanzas-personales.git', 'main', 'feat/x'), 'https://github.com/Al3xiel/finanzas-personales/compare/main...feat/x?expand=1');
});

test('exec filtra la salida de Gradle: compilación, tests fallidos y éxito', () => {
  const dir = mkdtempSync(join(tmpdir(), 'am-exec-'));
  try {
    execFileSync('git', ['init', '-q'], { cwd: dir });
    cpSync(join(HERE, '..'), join(dir, '.ai-mobile'), { recursive: true, filter: (s) => !/[\\/]test[\\/]|[\\/]work[\\/]/.test(s) });
    mkdirSync(join(dir, '.ai-mobile/profile'), { recursive: true });
    // Instalado, el perfil está en .ai-mobile/profile; en el repo de las skills, en profiles/.
    const profileDir = existsSync(join(HERE, '../profile/profile.mjs')) ? join(HERE, '../profile') : join(HERE, '../../profiles/finanzas-personales');
    for (const f of ['profile.mjs', 'rules.mjs']) cpSync(join(profileDir, f), join(dir, '.ai-mobile/profile', f));
    const fake = (body) => { writeFileSync(join(dir, 'gradlew'), `#!/bin/sh\ncat <<'OUT'\n${body}\nOUT\nexit ${/FAILED|e: /.test(body) ? 1 : 0}\n`); chmodSync(join(dir, 'gradlew'), 0o755); };
    const exec = (cmd) => spawnSync(process.execPath, [join(dir, '.ai-mobile/am.mjs'), 'exec', '--b64', Buffer.from(cmd).toString('base64')], { cwd: dir, encoding: 'utf8' });
    fake('> Task :app:compileDebugKotlin FAILED\ne: file:///x/app/src/main/java/pe/A.kt:12:5 Unresolved reference: foo\nFAILURE: Build failed');
    assert.match(exec('./gradlew compileDebugKotlin').stdout, /no compila \(1 error\(es\)\)[\s\S]*src\/main\/java\/pe\/A\.kt:12:5 Unresolved reference/);
    fake('BudgetTest > rejectsNegative FAILED\n    java.lang.AssertionError: expected 0\n        at org.junit.Assert.fail(Assert.java:89)\n3 tests completed, 1 failed\nBUILD FAILED');
    const r = exec("./gradlew testDebugUnitTest --tests '*BudgetTest'").stdout;
    assert.match(r, /3 tests completed, 1 failed/);
    assert.match(r, /BudgetTest > rejectsNegative FAILED/);
    assert.ok(!/at org\.junit/.test(r));
    fake('> Task :app:testDebugUnitTest\nBUILD SUCCESSFUL in 4s');
    assert.match(exec('./gradlew testDebugUnitTest').stdout, /gradle: ok/);
    assert.equal(exec('./gradlew installDebug').status, 2); // fuera de SAFE_EXEC
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
