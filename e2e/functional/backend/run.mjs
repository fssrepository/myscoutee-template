#!/usr/bin/env node
// Template composition of the unchanged upstream collection and session runners.
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { parseCommonArgs, runCollection } from '../../shared/collection-runner.mjs';
import { createHttpClient } from '../../shared/http.mjs';
import { nativeDemoSessions } from '../../shared/native-demo-sessions.mjs';

const rootDir = path.dirname(fileURLToPath(import.meta.url));
const args = parseCommonArgs(process.argv.slice(2));
const fixtures = JSON.parse(await readFile(path.join(rootDir, 'fixtures.json'), 'utf8'));
const baseUrl = process.env.E2E_SERVER_BASE_URL ?? 'https://127.0.0.1';
const apiPrefix = process.env.E2E_API_PREFIX ?? '/api';
const artifactsDir = path.resolve(rootDir, '../../../.e2e-artifacts/functional/backend');
const sessions = nativeDemoSessions(baseUrl, { apiPrefix });
const ctx = {
  http: createHttpClient({ baseUrl, headersForRequest: sessions.headersForRequest }),
  mode: process.env.E2E_SERVER_MODE ?? 'contract',
  vars: { ...fixtures, apiPrefix },
  defaultMaxMs: Number(process.env.E2E_DEFAULT_MAX_MS ?? 5000)
};
let summary;
let cleanupError;
try {
  if (!args.listOnly) await sessions.prepare(fixtures.adminUserId, fixtures.userId);
  summary = await runCollection({ rootDir, ctx, ...args, artifactsDir });
} finally {
  if (!args.listOnly) {
    try { await sessions.close(); } catch (error) { cleanupError = error.message; }
  }
}
if (!args.listOnly) {
  await mkdir(artifactsDir, { recursive: true });
  await writeFile(path.join(artifactsDir, 'summary.json'), JSON.stringify({
    recordedAt: new Date().toISOString(), mode: ctx.mode, baseUrl, apiPrefix,
    selection: { filter: args.filter, group: args.group, qaCase: args.qaCase },
    ...summary, ownedSessionsRevoked: !cleanupError, cleanupError,
    scope: 'Backend contracts and owned session lifecycle in the recorded deployment mode; not UI, load or installed-release proof.'
  }, null, 2) + '\n');
}
console.log(`\ntemplate backend: ${summary.passed} passed, ${summary.failed} failed, ${summary.skipped} skipped, ${summary.total} selected`);
if (cleanupError) console.error(`Owned session cleanup failed: ${cleanupError}`);
process.exitCode = summary.failed || cleanupError ? 1 : 0;
