import assert from 'node:assert/strict';
export async function assertResponse({ ctx, result, request }) {
  assert.equal(result.data.length, 1);
  const chat = result.data[0];
  assert.equal(chat.channelType, 'contact');
  assert.deepEqual(chat.memberIds.sort(), [ctx.vars.userId, ctx.vars.secondUserId].sort());
  const messages = await ctx.http.get(`${ctx.vars.apiPrefix}/activities/chats/${chat.id}/messages`, {
    headers: request.headers, params: request.params
  });
  assert.equal(messages.data.items.length, 2);
  assert.deepEqual(new Set(messages.data.items.map(message => message.senderId)),
    new Set([ctx.vars.userId, ctx.vars.secondUserId]));
  assert.ok(messages.data.items.every(message => message.text && !message.deletedAtIso));
  const peer = await ctx.http.get(`${ctx.vars.apiPrefix}/activities/chats`, {
    headers: { 'X-App-Session-Kind': 'demo', 'X-Demo-User-Id': ctx.vars.secondUserId },
    params: { userId: ctx.vars.secondUserId }
  });
  assert.equal(peer.data.length, 1);
  assert.equal(peer.data[0].id, chat.id);
}
