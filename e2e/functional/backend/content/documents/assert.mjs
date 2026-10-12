import assert from 'node:assert/strict';
export async function assertResponse({ ctx, result, request }) {
  const results = [['help', result]];
  for (const kind of ['privacy', 'terms', 'explanation']) {
    results.push([kind, await ctx.http.get(`${ctx.vars.apiPrefix}/${kind}/active`, {
      headers: request.headers,
      params: { lang: 'en', ...(kind === 'explanation' ? { contextKey: 'home' } : {}) }
    })]);
  }
  for (const [kind, response] of results) {
    const revision = response.data.activeRevision;
    assert.equal(revision.documentKind, kind);
    assert.equal(revision.lang, 'en');
    assert.equal(revision.active, true);
    assert.ok(revision.sections.length > 0, `${kind} must contain seeded sections`);
    assert.deepEqual(response.data.revisions, [revision]);
  }
}
