import assert from 'node:assert/strict';
export async function assertResponse({ctx, result, request}) {
  assert.equal(result.data.total, 4);
  assert.deepEqual(result.data.items.map(item => item.title), ['Coastal light', 'Quiet peaks']);
  assert.deepEqual(Object.keys(result.data.items[0]).sort(), ['dateIso','description','id','imageUrl','palette','title']);
  for (const item of result.data.items) assert.equal(new Date(item.dateIso).toISOString(), item.dateIso, 'Calendar cards have a valid canonical date');
  const second = await ctx.http.get(`${ctx.vars.apiPrefix}/showcase/cards`, {headers:request.headers, params:{page:1,pageSize:2}});
  assert.deepEqual(second.data.items.map(item => item.title), ['Desert rhythm','Forest canopy']);
  const end = await ctx.http.get(`${ctx.vars.apiPrefix}/showcase/cards`, {headers:request.headers, params:{page:2,pageSize:2}});
  assert.deepEqual(end.data.items, []);
  assert.equal(end.data.total,4);
}
