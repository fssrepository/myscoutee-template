#!/usr/bin/env node
// Serial composition of the qualified, fixture-owning flow scripts. Each flow
// keeps its existing Chrome/fixture/cleanup owner; no second browser harness.
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { parseCommonArgs } from '../../shared/collection-runner.mjs';

const exec = promisify(execFile);
const root = fileURLToPath(new URL('./', import.meta.url));
const local = process.argv.includes('--local');
const args = parseCommonArgs(process.argv.slice(2).filter(arg => arg !== '--local'));
const cases = JSON.parse(await readFile(new URL('./suite.json', import.meta.url), 'utf8'));
const selected = cases.filter(item => (!local || item.local !== false) && (!args.filter || item.id.includes(args.filter))
  && (!args.group || item.id.startsWith(args.group + '/'))
  && (!args.qaCase || item.qaBranches.includes(args.qaCase)));
if (args.listOnly) {
  for (const item of selected) console.log(item.id);
} else {
  if (!selected.length) throw new Error('No UI flow matched the requested selection');
  const artifacts = path.resolve(process.env.E2E_UI_ARTIFACTS_DIR
    ?? path.join(root, '../../../.e2e-artifacts/functional/ui', local ? 'local' : 'http'));
  await mkdir(artifacts, { recursive: true });
  const summary = { mode: local ? 'local' : 'http', startedAt: new Date().toISOString(),
    baseUrl: local ? process.env.E2E_LOCAL_BASE_URL ?? 'http://127.0.0.1:4200'
      : process.env.E2E_SERVER_BASE_URL ?? 'https://127.0.0.1',
    passed: 0, failed: 0, results: [] };
  for (const item of selected) {
    const destination = path.join(artifacts, item.id);
    await mkdir(destination, { recursive: true });
    const started = performance.now();
    let status = 'PASS', stdout = '', stderr = '';
    try {
      ({ stdout, stderr } = await exec(process.execPath,
        [path.join(root, local && item.localScript ? item.localScript : item.script),
          ...(local && !item.localScript ? ['--local'] : [])], {
          cwd: path.resolve(root, '../../..'), maxBuffer: 16 * 1024 * 1024,
          env: { ...process.env, E2E_SERVER_BASE_URL: summary.baseUrl,
            E2E_SCREENSHOT_DIR: destination }
        }));
    } catch (error) {
      status = 'FAIL'; stdout = error.stdout ?? ''; stderr = error.stderr ?? error.message;
    }
    await writeFile(path.join(destination, 'run.log'), stdout + stderr);
    summary[status === 'PASS' ? 'passed' : 'failed']++;
    summary.results.push({ id: item.id, qaBranches: item.qaBranches, status,
      durationMs: Math.round(performance.now() - started), log: path.relative(artifacts, path.join(destination, 'run.log')) });
    await writeFile(path.join(artifacts, 'summary.json'), JSON.stringify(summary, null, 2) + '\n');
    await writeFile(path.join(artifacts, 'summary.csv'), 'flow,status,duration_ms,qa_branches,log\n'
      + summary.results.map(result => [result.id, result.status, result.durationMs,
        result.qaBranches.join(';'), result.log].join(',')).join('\n') + '\n');
    console.log(`${status} ${item.id} ${summary.results.at(-1).durationMs}ms (${destination}/run.log)`);
    if (status === 'FAIL' && args.failFast) break;
  }
  console.log(`UI ${summary.mode}: ${summary.passed} passed, ${summary.failed} failed; ${artifacts}/summary.json`);
  process.exitCode = summary.failed ? 1 : 0;
}
