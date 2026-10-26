#!/usr/bin/env node
/**
 * PreToolUse (Bash): si Claude corre una tarea de Gradle de test, compilación o lint directo
 * (`./gradlew testDebugUnitTest`, `compileDebugKotlin`, `lintDebug`, `ktlintCheck`…), reescribe el
 * comando para que pase por `am.mjs exec`, que devuelve solo conteos y fallos. Una corrida de Gradle
 * son cientos o miles de líneas que quedan en el contexto y se reenvían en cada turno; el resumen,
 * decenas. Para ver la salida completa a propósito, agregar `| cat`: el hook no lo toca.
 *
 * Devuelve solo `updatedInput`: no aprueba nada; los permisos se evalúan sobre el comando reescrito.
 * Cualquier error aquí deja el comando original intacto.
 */
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { SAFE_EXEC, PROFILE } from '../lib/android.mjs';

try {
  const input = JSON.parse(readFileSync(0, 'utf8'));
  const command = input.tool_input?.command ?? '';
  if (typeof command !== 'string' || !SAFE_EXEC.test(command) || /--continuous|-t\b/.test(command) || input.tool_input?.run_in_background) process.exit(0);

  const project = process.env.CLAUDE_PROJECT_DIR
    || execFileSync('git', ['rev-parse', '--show-toplevel'], { cwd: input.cwd || process.cwd(), encoding: 'utf8' }).trim();
  const script = `${project.replace(/\\/g, '/')}/${PROFILE.tool ?? '.ai-mobile'}/am.mjs`;
  const b64 = Buffer.from(command, 'utf8').toString('base64');
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      updatedInput: { ...input.tool_input, command: `node "${script}" exec --b64 ${b64}` },
    },
  }));
} catch {
  process.exit(0);
}
