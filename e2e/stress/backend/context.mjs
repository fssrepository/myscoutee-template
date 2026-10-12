// The same fixed, versioned actors as the template functional collection.
// Load measurement, resource sampling and comparison remain shared upstream.
import { readFile } from 'node:fs/promises';
import { createHttpClient } from '../../shared/http.mjs';

const fixtures = JSON.parse(await readFile(new URL('../../functional/backend/fixtures.json', import.meta.url), 'utf8'));

export function sessionActors() {
  return [process.env.E2E_ADMIN_USER_ID ?? fixtures.adminUserId,
    process.env.E2E_DEMO_USER_ID ?? fixtures.userId];
}

export function createContext({ baseUrl, mode, runId, vu, nativeSessions }) {
  if (!nativeSessions) throw new Error('Template load tests require E2E_NATIVE_DEMO_SESSIONS=true.');
  return {
    mode,
    http: createHttpClient({ baseUrl, headersForRequest: nativeSessions.headersForRequest,
      timeoutMs: Number(process.env.E2E_STRESS_HTTP_TIMEOUT_MS ?? 15000) }),
    vars: { ...fixtures, userId: sessionActors()[1], adminUserId: sessionActors()[0],
      apiPrefix: process.env.E2E_API_PREFIX ?? '/api', runId, vu: String(vu) },
    defaultMaxMs: Number(process.env.E2E_STRESS_DEFAULT_MAX_MS ?? 5000)
  };
}
