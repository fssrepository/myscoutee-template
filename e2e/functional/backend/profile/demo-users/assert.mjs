import assert from 'node:assert/strict';
export function assertResponse({ ctx, result }) {
  assert.deepEqual(result.data.map(user => user.id).sort(),
    [ctx.vars.userId, ctx.vars.secondUserId, ctx.vars.adminUserId].sort());
  assert.ok(result.data.every(user => user.name && !user.deletedAtIso));
  assert.ok(result.data.every(user => user.id !== ctx.vars.operatorUserId));
}
