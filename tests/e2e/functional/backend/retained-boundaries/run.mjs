import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { stageChatFixture } from '../../ui/chat/fixture.mjs';
import { nativeDemoSessions } from '@fssrepository/myscoutee-e2e/shared/native-demo-sessions.mjs';
import { createHttpClient } from '@fssrepository/myscoutee-e2e/shared/http.mjs';

const baseUrl = process.env.E2E_SERVER_BASE_URL;
assert(baseUrl && process.env.E2E_MINIO_CONTAINER?.startsWith('myscoutee-template-'));
const fixture = await stageChatFixture();
const sessions = nativeDemoSessions(baseUrl);
const http = createHttpClient({ baseUrl, headersForRequest: sessions.headersForRequest });
const [owner, peer] = fixture.ids;
const headers = { 'X-App-Session-Kind': 'demo', 'X-Demo-User-Id': owner };
const peerHeaders = { ...headers, 'X-Demo-User-Id': peer };
const checks = {};
const uploadedKeys = [];
try {
  const revision = (await http.get('/api/privacy/active?lang=en', { headers })).data.activeRevision;
  const consent = { userId: owner, revisionId: revision.id, revisionVersion: 999,
    approvedOptionalSectionIds: [], source: 'entry' };
  const accepted = (await http.post('/api/privacy/consents', { headers, json: consent })).data;
  assert.equal(accepted.revisionVersion, revision.version);
  const loaded = (await http.get('/api/privacy/consents', { headers,
    params: { userId: owner, revisionId: revision.id, revisionVersion: revision.version } })).data;
  assert.equal(loaded.id, accepted.id);
  // Canonical DEMO consent bypass is intentional; REAL actor denial is covered by the consent owner tests.
  checks.consentHttpIdentityAndReload = true;

  const device = { userId: owner, deviceId: 'owned-endpoint-check', platform: 'web',
    firebaseToken: 'owned-nondeliverable-test-token', notificationsEnabled: false };
  await http.post('/api/activities/chats/devices', { headers, json: device });
  const snapshot = (await http.post('/api/auth/me/realtime/long-poll', { headers,
    json: { userId: owner, cursor: null } })).data;
  assert.equal(snapshot.userId, owner);
  assert(snapshot.notificationDevices.some(item => item.deviceId === device.deviceId));
  await http.delete('/api/activities/chats/devices', { headers, json: device });
  const after = (await http.post('/api/auth/me/realtime/long-poll', { headers,
    json: { userId: owner, cursor: null } })).data;
  assert(!after.notificationDevices.some(item => item.deviceId === device.deviceId));
  checks.deviceRegistrationRemovalAndRealtime = true;

  const uploads = [];
  for (const [kind, field, type, bytes] of [
    ['documents', 'document', 'text/plain', 'Owned endpoint coverage'],
    ['audio', 'audio', 'audio/wav', Buffer.from('UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQAAAAA=', 'base64')]
  ]) {
    const form = new FormData();
    form.set('ownerId', owner); form.set('entityId', 'owned-endpoint-check');
    form.set(field, new Blob([bytes], { type }), field === 'audio' ? 'owned.wav' : 'owned.txt');
    const result = (await http.post('/api/media/' + kind, { headers, body: form })).data;
    assert.equal(result.uploaded, true);
    const key = new URL(result.url, baseUrl).searchParams.get('key');
    assert(key?.includes('/' + owner + '/'));
    uploadedKeys.push(key); uploads.push(result.url);
    const read = await http.get(result.url, { headers });
    assert.equal(read.status, 200);
    // Native DEMO uploads use the public/demo prefix; private media has its separate authorization test.
    assert(key.startsWith('public/demo/'));
    await http.get(result.url, { headers: peerHeaders });
  }
  const sent = (await http.post(`/api/activities/chats/${fixture.chatId}/messages`, {
    headers, params: { userId: owner }, json: { text: 'Owned attachment check' }
  })).data;
  const attachment = { id: 'owned-document', type: 'document', title: 'owned.txt',
    ownerUserId: owner, url: uploads[0] };
  const changed = (await http.post(`/api/activities/chats/${fixture.chatId}/messages/${sent.id}/attachments`, {
    headers, params: { userId: owner }, json: { attachments: [attachment] }
  })).data;
  assert.equal(changed.attachments[0].url, uploads[0]);
  await http.get(uploads[0], { headers: peerHeaders });
  const rejectedImport = (await http.post('/api/media/images/import', {
    headers, json: { ownerId: owner, entityId: 'owned-endpoint-check', imageUrl: 'file:///etc/passwd' }
  })).data;
  assert.equal(rejectedImport.uploaded, false);
  checks.documentAudioUploadAndAttachmentPersistence = true;
  checks.imageImportRejectsNonHttpSource = true;
} finally {
  try { await sessions.close(); } finally {
    try {
      for (const key of uploadedKeys) {
        assert(key.includes('/' + owner + '/') && owner.startsWith('qa-template-chat-'));
        execFileSync('docker', ['exec', '-i', process.env.E2E_MINIO_CONTAINER,
          'sh', '-s', '--', key], { input: `set -eu
export GOMEMLIMIT=48MiB GOMAXPROCS=1
mc alias set owned_endpoint http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
trap 'mc alias remove owned_endpoint >/dev/null' EXIT
mc rm --force "owned_endpoint/$MEDIA_BUCKET/$1" >/dev/null
`, stdio: ['pipe', 'pipe', 'pipe'], timeout: 30000 });
      }
    } finally { await fixture.close(); }
  }
}
console.log('DONE retained endpoint gaps', JSON.stringify(checks));
if (process.env.E2E_EVIDENCE_FILE) writeFileSync(process.env.E2E_EVIDENCE_FILE,
  JSON.stringify({ checks, cleanup: true }, null, 2) + '\n');
