/**
 * Reglas propias de finanzas-personales: lo que la app promete en sus docs y un error rompe en
 * silencio. Cada regla cita la regla del doc que protege (guía §5.3, reglas S*, E*, R*, glosario §3).
 */
import { DOCS, docsFor } from './profile.mjs';

export const SECURITY_RULES = new Set(['internet-permission', 'network-dependency', 'allow-backup', 'sensitive-permission',
  'events-mutation', 'sensitive-log', 'money-floating']);

const code = (line) => line.replace(/"(?:\\.|[^"\\])*"/g, '""').replace(/\/\/.*$/, '');
const MONEY_NAME = /\b\w*(amount|balance|total|money|price|fee|delta|remaining|estimate|allocated|spent|minor|limit|budget|cost|income|expense|receivable)\w*/i;

export function fileAlerts({ file, text, added }) {
  if (/\/src\/(test|androidTest)\//.test(file)) return [];
  const out = [];
  const push = (rule, line, msg) => out.push({ rule, line, msg });
  const isKt = file.endsWith('.kt');
  const domain = /\/domain\//.test(file);
  const projector = /Projector\.kt$/.test(file);
  const eventStore = /\/eventstore\//.test(file) || /EventDao\.kt$|EventEntity\.kt$/.test(file);

  for (const { n, line } of added) {
    const c = code(line);
    if (isKt) {
      // Guía §5.3: nunca Double/Float para dinero; Money(amountMinor: Long, currency).
      if ((/:\s*(Double|Float)\b/.test(c) && MONEY_NAME.test(c)) || /\bMoney\([^)]*\d+\.\d+/.test(c) || /(amount|balance)\w*\.toDouble\(\)/i.test(c)) {
        push('money-floating', n, 'dinero en Double/Float (guía §5.3): Money(amountMinor: Long, currency); S/ 12.50 = 1250');
      }
      if (/RoundingMode\.(?!HALF_EVEN\b)\w+/.test(c)) push('rounding-mode', n, 'redondeo distinto de HALF_EVEN (guía §5.3); el residuo va a la porción mayor para que cuadre al céntimo');
      if ((domain || projector) && /Instant\.now\(\)|LocalDate\.now\(\)|LocalDateTime\.now\(\)|System\.currentTimeMillis\(\)|ZoneId\.systemDefault\(\)/.test(c)) {
        push(projector ? 'projector-nondeterministic' : 'clock-direct', n, projector ? 'un proyector no lee el reloj (guía §6.5): el replay daría otro resultado; la fecha viene en el evento'
          : 'reloj o zona directa en el dominio (guía §5.3): Clock y ZoneId inyectados');
      }
      if (projector && /UUID\.randomUUID|\bRandom\b|kotlin\.random/.test(c)) push('projector-nondeterministic', n, 'aleatoriedad en un proyector (guía §6.5): los IDs se generan en el caso de uso y viajan en el evento');
      if (eventStore && /@(Update|Delete|Upsert)\b|OnConflictStrategy\.REPLACE|\b(UPDATE|DELETE\s+FROM|REPLACE\s+INTO)\s+events\b/i.test(line)) {
        push('events-mutation', n, 'la tabla events es append-only (E1): solo INSERT; un error se corrige con MovementCorrected/MovementVoided');
      }
      if (/\b(Log\.\w+|println|Timber\.\w+)\(/.test(c) && (MONEY_NAME.test(line) || /\b(note|alias|name|payload|person|account)\w*/i.test(line))) {
        push('sensitive-log', n, 'log con montos, nombres, notas o payloads (S6): prohibido incluso en debug');
      }
      if (/autoGenerate\s*=\s*true/.test(c) && !/EventEntity\.kt$/.test(file)) push('autoincrement-id', n, 'ID autoincremental (modelo §1): UUID generado en el caso de uso, para que el replay dé los mismos IDs');
      if (/UseCase\.kt$/.test(file) && /^import\s+[\w.]+\.data\.(projection|.*Dao\b)/.test(line)) push('usecase-writes-projection', n, 'un caso de uso no escribe proyecciones (guía §4): emite eventos; los proyectores actualizan las tablas');
      const term = c.match(/\b(class|interface|object|typealias|fun|val|var)\s+\w*(Transaction|Cluster|Concept|Debt)\w*/);
      if (term && !/@Transaction/.test(line)) push('forbidden-term', n, `"${term[2]}" está en "términos a evitar" (glosario §3): ${{ Transaction: 'Movement', Cluster: 'ReimbursementPool', Concept: 'Tag', Debt: 'Loan o ReimbursementPool' }[term[2]]}`);
      const es = c.match(/\b(class|interface|object|fun|val|var)\s+(\w*(Cuenta|Gasto|Ingreso|Saldo|Monto|Persona|Fondo|Categor[ií]a|Usuario|Datos|Sitio|Deuda|Prestamo|Reembolso|Movimiento)\w*)/i);
      if (es) push('spanish-identifier', n, `identificador en español "${es[2]}" (guía §5.1): el código usa la columna "Código (EN)" del glosario`);
    }
    if (file.endsWith('AndroidManifest.xml')) {
      if (/android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE)/.test(line)) push('internet-permission', n, 'la app no declara INTERNET (S4, P1): los datos no salen del teléfono');
      if (/android:allowBackup="true"/.test(line)) push('allow-backup', n, 'allowBackup="false" y dataExtractionRules excluyendo todo (S3)');
      if (/android\.permission\.(READ|WRITE|MANAGE)_EXTERNAL_STORAGE|READ_CONTACTS|ACCESS_(FINE|COARSE)_LOCATION|READ_MEDIA_\w+/.test(line)) push('sensitive-permission', n, 'permiso fuera del mínimo (guía §5.6, datos §2): solo USE_BIOMETRIC y POST_NOTIFICATIONS; archivos por Storage Access Framework');
    }
    if (/\.gradle(\.kts)?$|libs\.versions\.toml$/.test(file) && /retrofit|okhttp|ktor|volley|firebase|analytics|crashlytics|sentry|play-services-ads/i.test(line)) {
      push('network-dependency', n, 'dependencia de red o telemetría: la app es local y sin Internet (P1, S4)');
    }
  }
  return out;
}

/** Términos del glosario: todo lo que esté entre backticks en docs/03-glosario.md. */
const glossaryTerms = (text) => new Set([...(text ?? '').matchAll(/`([A-Za-z_][\w.]*)/g)].map((m) => m[1]));

export function changeAlerts({ files, added, removed = () => [], read }) {
  const out = [];
  const paths = [...files.keys()];
  const touched = (p) => files.has(p);
  const glossary = read(DOCS.glossary.file);
  const terms = glossaryTerms(glossary);

  for (const p of paths.filter((x) => x.endsWith('.kt') && /\/src\/main\//.test(x))) {
    const lines = added(p);
    // Glosario primero (guía §5.1): un tipo nuevo del dominio con nombre que el glosario no conoce.
    if (glossary && /\/domain\//.test(p)) {
      for (const { n, line } of lines) {
        const m = line.match(/^\s*(?:@JvmInline\s+)?(?:sealed\s+|data\s+|value\s+|enum\s+|abstract\s+)*(?:class|interface|object)\s+([A-Z]\w+)/);
        if (m && !terms.has(m[1]) && !/(UseCase|Repository|Error|Exception|Data|Rule|Rules|Id)$/.test(m[1])) {
          out.push({ rule: 'glossary-missing', file: p, line: n, msg: `${m[1]} no está en ${DOCS.glossary.file}: agrégalo (término ES, código EN, definición) antes de programarlo` });
        }
      }
    }
    // Eventos: catalogados y versionados (E4). Uno existente que cambia de forma necesita schemaVersion nueva y su upcaster.
    const text = read(p) ?? '';
    if (/:\s*DomainEvent\b/.test(text)) {
      const isNew = files.get(p).isNew;
      // Solo las clases que implementan DomainEvent, aunque el archivo tenga otras.
      const events = new Set([...text.matchAll(/(?:data\s+)?class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*DomainEvent\b/g)].map((m) => m[1]));
      for (const { n, line } of lines) {
        const ev = line.match(/class\s+(\w+)/);
        if (ev && events.has(ev[1]) && glossary && !new RegExp(`\`${ev[1]}\``).test(glossary)) out.push({ rule: 'event-not-cataloged', file: p, line: n, msg: `evento ${ev[1]} sin fila en el catálogo del glosario (§2) ni payload en ${DOCS.model.file} (§3)` });
      }
      const shape = (ls) => ls.filter((l) => /^\s*(override\s+)?val\s+\w+\s*:/.test(l));
      const changedShape = !isNew && (shape(lines.map((l) => l.line)).length || shape(removed(p)).length);
      if (changedShape && !paths.some((x) => /Upcaster\w*\.kt$/.test(x)) && !lines.some(({ line }) => /schemaVersion|SCHEMA_VERSION/.test(line))) {
        out.push({ rule: 'event-schema-changed', file: p, line: lines[0]?.n ?? 1, msg: 'cambia el payload de un evento ya existente sin schemaVersion nueva ni upcaster (E4): los eventos guardados no se reescriben' });
      }
    }
  }

  // Docs del proyecto: lo que el código cambió y el doc que lo describe no.
  const owed = new Map();
  for (const p of paths.filter((x) => /\/src\/main\//.test(x))) for (const d of docsFor(p)) if (!owed.has(d)) owed.set(d, p);
  for (const [key, p] of owed) {
    const doc = DOCS[key];
    if (doc && !touched(doc.file) && read(doc.file) !== null) out.push({ rule: 'docs-stale', file: doc.file, line: 0, msg: `cambió ${p.split('/').pop()} y no ${doc.file} (${doc.covers}): actualízalo o confirma que el cambio no lo altera` });
  }
  return out;
}
