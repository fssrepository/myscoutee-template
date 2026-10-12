import assert from 'node:assert/strict';
export function assertResponse({ ctx, result }) {
  const { records, total, unreadCount, nextCursor } = result.data;
  assert.ok(Array.isArray(records));
  assert.ok(Number.isInteger(total) && total >= records.length);
  assert.ok(Number.isInteger(unreadCount) && unreadCount >= 0 && unreadCount <= total);
  assert.equal(new Set(records.map(record => record.id)).size, records.length);
  for (const record of records) {
    assert.equal(record.recipientUserId, ctx.vars.userId);
    assert.ok(record.id && record.title && record.createdAtIso);
  }
  if (nextCursor === null) {
    assert.equal(records.length, total);
    assert.equal(records.filter(record => !record.readAtIso).length, unreadCount);
  } else {
    assert.ok(typeof nextCursor === 'string' && nextCursor.length > 0);
  }
}
