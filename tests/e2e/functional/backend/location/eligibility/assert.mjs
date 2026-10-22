import assert from 'node:assert/strict';
export async function assertResponse({ ctx, result }) {
  assert.equal(result.data.eligible, true);
  assert.equal(result.data.partitionKey, 'country:de');
  for (const params of [ { latitude: 91, longitude: 13 }, { latitude: 52.52 } ]) {
    await ctx.http.get(`${ctx.vars.apiPrefix}/auth/eligibility`, { params, expectedStatus: 400 });
  }
}
