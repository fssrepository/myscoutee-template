import assert from 'node:assert/strict';
export async function assertResponse({ctx,result}) {
  assert.match(result.headers['content-type'], /text\/html/);
  assert(result.text.includes('&lt;script&gt;qa&lt;/script&gt;'));
  assert(!result.text.includes('<script>qa</script>'));
  const route = `${ctx.vars.apiPrefix}/auth/facebook/data-deletion`;
  const status = await ctx.http.get(route + '/status');
  assert(status.text.includes('No confirmation code was provided'));
  // This local deployment intentionally has no live provider credentials.
  await ctx.http.post(route, {
    headers: {'Content-Type':'application/x-www-form-urlencoded'},
    body: new URLSearchParams({signed_request:'qa.invalid'}).toString(),
    expectedStatus:503
  });
}
