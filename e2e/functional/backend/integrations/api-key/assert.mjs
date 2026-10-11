import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { saveAccess, restoreAccess } from '../access-fixture.mjs';

export async function assertResponse({ ctx, result, request }) {
  const previous = result.data;
  assert.equal(Object.hasOwn(previous, 'affiliate'), false);
  assert.equal(Object.hasOwn(previous, 'participants'), false);
  const path = `${ctx.vars.apiPrefix}/integrations`;
  const headers = request.headers;
  const created = (await ctx.http.post(`${path}/tokens`, { headers,
    json: { name: `Template QA ${randomUUID()}`, expiresInDays: 1 } })).data;
  const apiHeaders = { 'X-App-Session-Kind': 'demo',
    Authorization: `Bearer ${created.value}`, 'X-MyScoutee-Client-Id': randomUUID() };
  try {
    assert.match(created.value, /^msc_/);
    const current = (await ctx.http.get(`${path}/settings`, { headers })).data;
    assert.equal(JSON.stringify(current).includes(created.value), false);
    await ctx.http.get(`${path}/v1/chats`, { headers: apiHeaders, expectedStatus: 403 });
    await saveAccess(ctx, headers, current, { clients: [{ id: created.token.id, accessMode: 'read' }] });
    const connected = (await ctx.http.post(`${path}/v1/connect`, { headers: apiHeaders })).data;
    assert.equal(connected.profileId, ctx.vars.userId);
    assert.deepEqual(connected.operations, ['listChats', 'chatDetails', 'chatMessages', 'listNotifications']);
    const chats = (await ctx.http.get(`${path}/v1/chats`, { headers: apiHeaders })).data;
    assert.ok(chats.items.some(chat => chat.channelType === 'contact'));
    await ctx.http.get(`${path}/v1/notifications`, { headers: apiHeaders });
    await ctx.http.post(`${path}/v1/connect`, { headers: {
      ...apiHeaders, 'X-MyScoutee-Client-Id': randomUUID()
    }, expectedStatus: 401 });
  } finally {
    try { await ctx.http.delete(`${path}/tokens/${created.token.id}`, { headers }); }
    finally { await restoreAccess(ctx, headers, previous); }
  }
  await ctx.http.post(`${path}/v1/connect`, { headers: apiHeaders, expectedStatus: 401 });
}
