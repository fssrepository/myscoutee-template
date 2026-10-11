import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { test } from 'node:test';
import { nativeDemoSessions } from './native-demo-sessions.mjs';

for (const apiPrefix of ['/api', '']) {
  test(`owned demo sessions use ${apiPrefix || 'direct backend'} paths and revoke only their logins`, async () => {
    const requests = [];
    const server = createServer(async (request, response) => {
      let body = '';
      for await (const chunk of request) body += chunk;
      requests.push({ path: request.url, headers: request.headers, body: body ? JSON.parse(body) : null });
      response.setHeader('content-type', 'application/json');
      const routes = {
        [`${apiPrefix}/auth/demo-users`]: [{ id: 'member' }],
        [`${apiPrefix}/auth/session/login`]: { accepted: true },
        [`${apiPrefix}/auth/me/logout`]: { submitted: true }
      };
      response.statusCode = request.url in routes ? 200 : 404;
      response.end(JSON.stringify(routes[request.url] ?? {}));
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const baseUrl = `http://127.0.0.1:${server.address().port}`;
    // Exercise the unchanged constructor used by existing source runners too.
    const sessions = apiPrefix ? nativeDemoSessions(baseUrl) : nativeDemoSessions(baseUrl, { apiPrefix });
    try {
      await sessions.prepare('admin', 'member');
      const options = { headers: { 'X-App-Session-Kind': 'demo' } };
      const headers = await sessions.headersForRequest('GET', `${baseUrl}/profile?userId=member`, options);
      assert.equal(headers['X-Demo-User-Id'], 'member');
      assert.match(headers['X-App-Session-Id'], /^session:/);
      assert.deepEqual(await sessions.headersForRequest('GET', 'https://other.invalid/profile?userId=member', options), {});
      assert.deepEqual(await sessions.headersForRequest('GET', `${baseUrl}/profile?userId=member`, {
        headers: { ...options.headers, 'X-App-Session-Id': 'explicit-negative-session' }
      }), {});
      await sessions.close();
      assert.deepEqual(requests.map(request => request.path), [
        'auth/demo-users', 'auth/session/login', 'auth/session/login', 'auth/me/logout', 'auth/me/logout'
      ].map(route => `${apiPrefix}/${route}`));
      assert.equal(requests[1].headers['x-app-session-id'], requests[3].headers['x-app-session-id']);
      assert.equal(requests[2].headers['x-app-session-id'], requests[4].headers['x-app-session-id']);
      assert.notEqual(requests[1].headers['x-app-session-id'], requests[2].headers['x-app-session-id']);
      assert.deepEqual(requests.slice(3).map(request => request.body.userId), ['member', 'admin']);
    } finally {
      await new Promise(resolve => server.close(resolve));
    }
  });
}
