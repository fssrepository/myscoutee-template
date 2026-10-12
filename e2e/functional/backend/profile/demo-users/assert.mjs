import assert from 'node:assert/strict';
export async function assertResponse({ ctx, result }) {
  assert.deepEqual(result.data.map(user => user.id).sort(),
    [ctx.vars.userId, ctx.vars.secondUserId].sort());
  assert.ok(result.data.every(user => user.name && !user.deletedAtIso));
  assert.ok(result.data.every(user => user.id !== ctx.vars.operatorUserId));
  const admins = await ctx.http.get(`${ctx.vars.apiPrefix}/auth/demo-users?role=admin`, { headers: {} });
  assert.deepEqual(admins.data.map(user => user.id), [ctx.vars.adminUserId]);
}
