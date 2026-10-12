import { randomUUID } from 'node:crypto';
import { createHttpClient } from './http.mjs';

// Opt-in runner authentication for a production-style DEMO deployment. The
// collection requests/assertions remain unchanged; only owned sessions are used.
export function nativeDemoSessions(baseUrl, { apiPrefix = '/api' } = {}) {
  const raw = createHttpClient({ baseUrl });
  const sessions = new Map();
  async function login(userId) {
    if (!sessions.has(userId)) {
      const pending = (async () => {
        const headers = {
          'X-App-Session-Kind': 'demo', 'X-Demo-User-Id': userId,
          'X-App-Session-Id': `session:${randomUUID()}`
        };
        const result = await raw.post(`${apiPrefix}/auth/session/login`, {
          headers, json: { provider: 'demo', attemptId: `attempt:${randomUUID()}` }
        });
        if (result.data?.accepted !== true) throw new Error(`Native DEMO login rejected: ${result.data?.outcome}`);
        return headers;
      })();
      sessions.set(userId, pending);
    }
    return sessions.get(userId);
  }
  return {
    async prepare(adminUserId, preferredUserId) {
      const result = await raw.get(`${apiPrefix}/auth/demo-users`);
      const actor = preferredUserId ?? result.data.find(user => !user.deletedAtIso && user.profileStatus !== 'deleted')?.id;
      if (!actor) throw new Error('No native DEMO actor available');
      await login(actor);
      await login(adminUserId);
    },
    async headersForRequest(method, url, options) {
      if (new URL(url).origin !== new URL(baseUrl).origin) return {};
      const headers = new Headers(options.headers);
      // Explicit session and negative-auth requests retain their own semantics.
      if (headers.has('X-App-Session-Id') || headers.get('X-App-Session-Kind') !== 'demo') return {};
      const params = new URL(url).searchParams;
      const actor = headers.get('X-Demo-User-Id') || params.get('adminUserId') || params.get('userId')
        || options.json?.adminUserId || options.json?.userId;
      return actor ? login(actor) : {};
    },
    async close() {
      const errors = [];
      for (const [userId, pending] of sessions) {
        let headers;
        try { headers = await pending; } catch { continue; }
        try {
          const result = await raw.post(`${apiPrefix}/auth/me/logout`, { headers, json: { userId } });
          if (result.data?.submitted !== true) throw new Error('Owned session logout not submitted');
        } catch (error) { errors.push(error); }
      }
      if (errors.length) throw new AggregateError(errors, 'Native DEMO session cleanup failed');
    }
  };
}
