import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
export async function assertResponse({ ctx }) {
  const headers = {
    'X-App-Session-Kind': 'demo', 'X-Demo-User-Id': ctx.vars.userId,
    'X-App-Session-Id': `session:${randomUUID()}`
  };
  const login = await ctx.http.post(`${ctx.vars.apiPrefix}/auth/session/login`, {
    headers, json: { provider: 'demo', attemptId: `attempt:${randomUUID()}` }
  });
  assert.equal(login.data.accepted, true);
  try {
    const me = await ctx.http.get(`${ctx.vars.apiPrefix}/auth/me`, { headers });
    assert.equal(me.data.id, ctx.vars.userId);
  } finally {
    const logout = await ctx.http.post(`${ctx.vars.apiPrefix}/auth/me/logout`, {
      headers, json: { userId: ctx.vars.userId }
    });
    assert.equal(logout.data.submitted, true);
  }
  await ctx.http.get(`${ctx.vars.apiPrefix}/auth/me`, { headers, expectedStatus: 401 });
}
