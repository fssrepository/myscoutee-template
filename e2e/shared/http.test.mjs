import assert from 'node:assert/strict';
import test from 'node:test';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { createHttpClient } from './http.mjs';

test('HTTP sends credentials but failure diagnostics redact headers, query and body', async () => {
  const received = [];
  const server = createServer(async (req, res) => {
    let body = '';
    for await (const chunk of req) body += chunk;
    received.push({ headers: req.headers, body, url: req.url });
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end('{}');
  }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  try {
    const client = createHttpClient({ baseUrl: `http://127.0.0.1:${server.address().port}` });
    for (const options of [
      { json: { keep: 'visible', nested: { client_secret: 'secret-body', token: 'private-token' } } },
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'keep=visible&refresh_token=secret-body&code_verifier=private-token' }
    ]) {
      await assert.rejects(client.post('/token?code=secret-query', {
        ...options, headers: { ...options.headers, Authorization: 'Bearer secret-header', 'X-App-Session-Id': 'session-secret' }
      }), error => {
        assert.equal(error.result.status, 400);
        assert(!/secret-header|session-secret|secret-query|secret-body|private-token/.test(error.result.curl));
        assert(error.result.curl.includes('visible'));
        return true;
      });
    }
    assert(received.every(request => request.headers.authorization === 'Bearer secret-header'
      && request.body.includes('secret-body') && request.url.includes('secret-query')));
  } finally {
    server.close();
    await once(server, 'close');
  }
});
