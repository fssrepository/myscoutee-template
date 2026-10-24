import assert from 'node:assert/strict';
export async function assertResponse({ ctx, result, request }) {
  assert.equal(result.data.id, ctx.vars.userId);
  assert.equal(result.data.admin, false);
  assert.equal(result.data.operator, false);
  assert.ok(result.data.name);
  assert.equal(result.data.partitionKey, 'country:de');
  assert.deepEqual(result.data.images, ['/media/public?key=images/demo-profiles/b29df68956f3fe15e04558de/photo-01/large.webp']);
  assert.equal(Object.hasOwn(result.data, 'affiliateRegistration'), false);
  const extended = await ctx.http.get(`${ctx.vars.apiPrefix}/auth/me/profile-ext`, {
    headers: request.headers, params: request.params
  });
  assert.equal(extended.data.profileExt.profile.id, ctx.vars.userId);
}
