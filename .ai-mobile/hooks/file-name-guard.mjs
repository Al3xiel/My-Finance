#!/usr/bin/env node
/**
 * PreToolUse (Write): rechaza un archivo NUEVO que no cumple el estándar de estructura
 * (lib/naming.mjs) en el momento de crearlo, que es cuando corregirlo es más barato. Un archivo que
 * ya existe nunca se bloquea: lo heredado se tolera. También exige los prefijos `plan-` y `prop-`
 * en `.ai-mobile/work/` y `.ai-mobile/proposals/`.
 *
 * Excepción deliberada: la ruta en `.ai-mobile/naming-exceptions.txt` (una por línea), con el motivo
 * en un comentario `#` — queda en el diff y se discute en el PR.
 * Cualquier error aquí deja pasar la escritura: el hook nunca traba el trabajo por un fallo propio.
 */
import { readFileSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, relative, isAbsolute } from 'node:path';

try {
  const input = JSON.parse(readFileSync(0, 'utf8'));
  const cwd = input.cwd || process.cwd();
  const project = process.env.CLAUDE_PROJECT_DIR || execFileSync('git', ['rev-parse', '--show-toplevel'], { cwd, encoding: 'utf8' }).trim();
  // El paquete se lee del build.gradle.kts del proyecto: android.mjs lo toma de AM_ROOT al cargar.
  process.env.AM_ROOT = project;
  const { checkNewPath } = await import('../lib/naming.mjs');
  const { PROFILE } = await import('../lib/android.mjs');
  const tool = PROFILE.tool ?? '.ai-mobile';

  const target = input.tool_input?.file_path;
  if (!target) process.exit(0);
  const abs = isAbsolute(target) ? target : join(cwd, target);
  if (existsSync(abs)) process.exit(0);
  const rel = relative(project, abs).replace(/\\/g, '/');
  const exceptionsFile = join(project, tool, 'naming-exceptions.txt');
  const exceptions = existsSync(exceptionsFile) ? readFileSync(exceptionsFile, 'utf8').split('\n').map((l) => l.replace(/#.*/, '').trim()) : [];
  if (rel.startsWith('..') || exceptions.includes(rel)) process.exit(0);

  const listDir = (dir) => execFileSync('git', ['ls-files', '--', dir], { cwd: project, encoding: 'utf8' }).split('\n').filter(Boolean);
  const doc = rel.match(new RegExp(`^${tool.replace('.', '\\.')}/(work|proposals)/([^/]+)\\.md$`));
  const prefix = doc && !doc[2].startsWith('backtest-') ? (doc[1] === 'work' ? 'plan-' : 'prop-') : null;
  const problem = prefix && !doc[2].startsWith(prefix)
    ? { msg: `en ${doc[1]}/ el nombre empieza con "${prefix}" (${prefix}${doc[2].replace(/^(plan|prop)-/, '')}.md)` }
    : checkNewPath(rel, listDir);
  if (!problem) process.exit(0);

  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      permissionDecision: 'deny',
      permissionDecisionReason: `Estándar de estructura (${rel}): ${problem.msg}. Elige otra ruta; si es una excepción justificada, agrégala a ${tool}/naming-exceptions.txt con el motivo.`,
    },
  }));
} catch {
  process.exit(0);
}
