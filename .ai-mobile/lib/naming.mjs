/**
 * Estándar de estructura para archivos NUEVOS de una app Android con Clean Architecture + MVVM por
 * feature (guía de programación del proyecto, §4 y §5.1):
 *
 *   <paquete>/feature/<f>/ui/        XxxScreen (Screen + Content), XxxViewModel, XxxUiState, XxxUiAction; components/
 *   <paquete>/feature/<f>/domain/    model/, usecase/ (XxxUseCase), rules/, repository/ (XxxRepository), event/
 *   <paquete>/feature/<f>/data/      XxxRepositoryImpl o RoomXxxRepository, XxxDao, XxxEntity, XxxProjector, XxxMapper
 *   <paquete>/feature/<f>/di/        XxxModule (Hilt)
 *   <paquete>/core/<x>/              money, time, security, di, designsystem…
 *   <paquete>/data/<x>/              piezas de datos transversales (eventstore, database)
 *
 * Solo se revisan rutas nuevas (un `git mv` es una ruta nueva). Lo heredado nunca se bloquea. Dos
 * llamadores, una regla: el hook de escritura y `am.mjs diff`.
 */
import { SRC_ROOT } from './android.mjs';
import { PROFILE_FEATURES } from './profile.mjs';

const GENERIC = /(Util|Utils|Helper|Helpers|Manager|Misc|Common|Stuff|Base)\.kt$/;
const GENERIC_DIR = /(^|\/)(utils?|helpers?|misc|common)\//;
const PASCAL = /^[A-Z][A-Za-z0-9]*\.kt$/;

/** Carpeta dentro de una feature → patrón del archivo y para qué es. */
const FEATURE_RULES = [
  [/^ui\/components\/$/, PASCAL, 'componente: PascalCase.kt con su @Preview'],
  [/^ui\/$/, /(Screen|ViewModel|UiState|UiAction|Route|Routes|Navigation)\.kt$/, 'XxxScreen.kt (Screen + Content), XxxViewModel.kt, XxxUiState.kt, XxxUiAction.kt'],
  [/^domain\/model\/$/, PASCAL, 'modelo del dominio (Kotlin puro), con el nombre del glosario'],
  [/^domain\/usecase\/$/, /^[A-Z]\w*UseCase\.kt$/, 'VerboSustantivoUseCase.kt con un operator fun invoke'],
  [/^domain\/rules\/$/, PASCAL, 'regla de negocio con el nombre de lo que decide o calcula'],
  [/^domain\/repository\/$/, /Repository\.kt$/, 'interfaz XxxRepository.kt'],
  [/^domain\/event\/$/, PASCAL, 'evento de dominio: sustantivo + verbo en pasado (ExpenseRegistered)'],
  [/^data\/(.+\/)?$/, /(RepositoryImpl|Repository|Dao|Entity|Projector|Mapper|Converters|Upcaster|Database|Migration\w*)\.kt$/, 'XxxRepositoryImpl / RoomXxxRepository, XxxDao, XxxEntity, XxxProjector, XxxMapper, XxxUpcaster'],
  [/^di\/$/, /Module\.kt$/, 'módulo Hilt: XxxModule.kt'],
];

/** `null` si cumple, o `{ rule, msg }`. `listDir(dir)` → archivos trackeados bajo `dir`. */
export function checkNewPath(file, listDir = () => []) {
  if (/\/src\/(test|androidTest)\//.test(file) || !file.endsWith('.kt')) return null;
  const src = SRC_ROOT();
  if (!file.startsWith(src)) {
    return /\/src\/main\/java\//.test(file) ? fail(`fuera del paquete del proyecto (${src}): todo el código vive bajo el namespace de la app`) : null;
  }
  const rel = file.slice(src.length);
  const name = rel.split('/').pop();
  if (GENERIC.test(name) || GENERIC_DIR.test(rel)) return fail(`"${name}" no dice para qué es: una regla va en domain/rules con el nombre de lo que decide; un formateo, en core/ (MoneyFormatter); un mapeo, en un XxxMapper`);
  if (!PASCAL.test(name)) return fail('archivo Kotlin en PascalCase, con el nombre de su clase principal');
  const parts = rel.split('/');
  if (parts.length === 1) return /^(MainActivity|\w+Application)\.kt$/.test(name) ? null : fail('en la raíz del paquete solo MainActivity y la Application; lo demás va en feature/, core/ o data/');
  const [top, second] = parts;
  if (top === 'navigation') return null;
  if (top === 'core' || top === 'data') {
    if (parts.length < 3) return fail(`en ${top}/ cada pieza tiene su carpeta (${top === 'core' ? 'money, time, security, di, designsystem' : 'eventstore, database'})`);
    if (second === 'di' && !/Module\.kt$|Qualifiers?\.kt$|Dispatchers?\.kt$/.test(name)) return fail('core/di: módulos Hilt (XxxModule.kt) y calificadores');
    return null;
  }
  if (top !== 'feature') return fail(`carpeta "${top}/" fuera de la estructura: feature/<f>/{ui,domain,data,di}, core/<x>, data/<x> (guía §4)`);
  if (!second || parts.length < 4) return fail('dentro de feature/<f>/ todo va en una capa: ui/, domain/, data/ o di/');
  if (PROFILE_FEATURES.length && !PROFILE_FEATURES.includes(second) && !listDir(`${src}feature/${second}/`).length) {
    return fail(`feature nueva "${second}": agrégala a la guía (§4) y a FEATURES del perfil (.ai-mobile/profile/profile.mjs) antes de crearla`);
  }
  const inFeature = parts.slice(2).join('/');
  const dir = inFeature.slice(0, inFeature.length - name.length);
  const rule = FEATURE_RULES.find(([d]) => d.test(dir));
  if (!rule) return fail(`carpeta "${dir}" fuera de la estructura de la feature: ui/ (components/), domain/{model,usecase,rules,repository,event}, data/…, di/`);
  return rule[1].test(name) ? null : fail(`en ${dir} va ${rule[2]}`);
}

const fail = (msg) => ({ rule: 'naming-standard', msg });
