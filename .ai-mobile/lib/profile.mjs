/**
 * Carga el perfil del proyecto: lo que no es Android genérico (paquete, features, docs, reglas propias).
 *
 * Instalado, el perfil está en `.ai-mobile/profile/` (lo copia install.mjs desde
 * `profiles/<nombre>/`). En este repositorio, sin instalar, se usa `AM_PROFILE` o el primero.
 */
import { existsSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const installed = join(HERE, '..', 'profile');
const repoProfiles = join(HERE, '..', '..', 'profiles');
const dir = existsSync(join(installed, 'profile.mjs')) ? installed
  : join(repoProfiles, process.env.AM_PROFILE || readdirSync(repoProfiles).sort()[0]);

const P = await import(pathToFileURL(join(dir, 'profile.mjs')).href);
const R = existsSync(join(dir, 'rules.mjs')) ? await import(pathToFileURL(join(dir, 'rules.mjs')).href) : {};

export const PROFILE = P.PROFILE;
export const PROFILE_SENSITIVE = P.SENSITIVE ?? [];
export const PROFILE_SECURITY_SURFACE = P.SECURITY_SURFACE ?? [];
export const PROFILE_FEATURES = P.FEATURES ?? [];
export const PROFILE_DOCS = P.DOCS ?? {};
/** (ruta) → claves de DOCS que la describen. */
export const PROFILE_DOCS_FOR = P.docsFor ?? (() => []);
export const projectFileAlerts = R.fileAlerts ?? (() => []);
export const projectChangeAlerts = R.changeAlerts ?? (() => []);
export const PROJECT_SECURITY_RULES = R.SECURITY_RULES ?? new Set();
export const PROFILE_DIR = dir;
