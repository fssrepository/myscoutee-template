import assert from 'node:assert/strict';
export function assertResponse({ ctx, result }) {
  assert.equal(result.data.productName, ctx.vars.productName);
  assert.equal(result.data.firebaseMessagingConfigured, false);
  assert.deepEqual(Object.keys(result.data).sort(), [
    'productName', 'logoCharacterIndex', 'homeLabel', 'logoUrl', 'revision',
    'firebaseMessagingConfigured', 'socialLinks', 'privacyContact'
  ].sort());
}
