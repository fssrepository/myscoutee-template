import assert from 'node:assert/strict';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { saveAccess, restoreAccess } from './access-fixture.mjs';

export async function assertMcpFlow({ ctx, result, request }, dynamic = false) {
  const previous = result.data;
  const headers = request.headers;
  const path = `${ctx.vars.apiPrefix}/integrations`;
  const redirectUri = 'https://template-qa.example.invalid/callback';
  assert.equal(previous.mcp.remoteEnabled, true, 'This collection requires the native dev DEMO MCP realm');
  const created = dynamic
    ? { client: { clientId: (await ctx.http.post(`${ctx.vars.apiPrefix}/mcp/oauth/register`, {
        headers: {}, expectedStatus: 201, json: { client_name: `Template public MCP QA ${randomUUID()}`,
          redirect_uris: [redirectUri], token_endpoint_auth_method: 'none',
          grant_types: ['authorization_code', 'refresh_token'], response_types: ['code'], scope: 'myscoutee:api' }
      })).data.client_id } }
    : (await ctx.http.post(`${path}/mcp/clients`, { headers,
        json: { name: `Template MCP QA ${randomUUID()}`, redirectUri } })).data;
  let tokenId = created.client.token?.id;
  try {
    const current = (await ctx.http.get(`${path}/settings`, { headers })).data;
    await saveAccess(ctx, headers, current, { mcpAccess: 'read',
      clients: tokenId ? [{ id: tokenId, accessMode: 'read' }] : [] });
    const metadata = (await ctx.http.get(`${ctx.vars.apiPrefix}/mcp/oauth/metadata`, { headers: {} })).data;
    assert.deepEqual(metadata.code_challenge_methods_supported, ['S256']);
    const verifier = randomBytes(32).toString('base64url');
    const authorization = {
      clientId: created.client.clientId, redirectUri, resource: current.mcp.resource,
      scope: 'myscoutee:api', responseType: 'code',
      codeChallenge: createHash('sha256').update(verifier).digest('base64url'),
      codeChallengeMethod: 'S256', state: randomUUID()
    };
    const context = (await ctx.http.post(`${path}/mcp/authorization`, { headers, json: authorization })).data;
    assert.equal(context.profileId, ctx.vars.userId);
    const consent = (await ctx.http.post(`${path}/mcp/consent`, { headers,
      json: { authorization, approve: true, profileId: ctx.vars.userId } })).data;
    if (dynamic) {
      const authorized = (await ctx.http.get(`${path}/settings`, { headers })).data;
      const connection = authorized.mcp.clients.find(client => client.clientId === created.client.clientId);
      assert.ok(connection, 'Public authorization must create the native account-owned connection');
      assert.equal(connection.manual, false);
      tokenId = connection.token.id;
      await saveAccess(ctx, headers, authorized, { clients: [{ id: tokenId, accessMode: 'read' }] });
    }
    const callback = new URL(consent.url);
    assert.equal(callback.origin, new URL(redirectUri).origin);
    assert.equal(callback.searchParams.get('state'), authorization.state);
    const exchange = (values, expectedStatus = 200) => ctx.http.post(`${ctx.vars.apiPrefix}/mcp/oauth/token`, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, expectedStatus,
      body: new URLSearchParams({ client_id: created.client.clientId, ...(dynamic ? {} : { client_secret: created.secret }),
        resource: current.mcp.resource, ...values }).toString()
    });
    const codeRequest = { grant_type: 'authorization_code', code: callback.searchParams.get('code'),
      code_verifier: verifier, redirect_uri: redirectUri };
    const grant = (await exchange(codeRequest)).data;
    assert.match(grant.access_token, /^mca_/);
    const apiHeaders = { Authorization: `Bearer ${grant.access_token}` };
    const connected = (await ctx.http.post(`${path}/v1/connect`, { headers: apiHeaders })).data;
    assert.equal(connected.profileId, ctx.vars.userId);
    await ctx.http.get(`${path}/v1/chats`, { headers: apiHeaders });
    await exchange(codeRequest, 400);
    const rotated = (await exchange({ grant_type: 'refresh_token', refresh_token: grant.refresh_token })).data;
    assert.notEqual(rotated.access_token, grant.access_token);
    await ctx.http.post(`${path}/v1/connect`, { headers: apiHeaders, expectedStatus: 401 });
    await ctx.http.get(`${path}/v1/notifications`, { headers: { Authorization: `Bearer ${rotated.access_token}` } });
    await ctx.http.delete(`${path}/mcp/clients/${tokenId}`, { headers });
    await ctx.http.post(`${path}/v1/connect`, {
      headers: { Authorization: `Bearer ${rotated.access_token}` }, expectedStatus: 401
    });
    await exchange({ grant_type: 'refresh_token', refresh_token: rotated.refresh_token }, 400);
  } finally {
    try {
      if (!tokenId && dynamic) {
        const current = (await ctx.http.get(`${path}/settings`, { headers })).data;
        tokenId = current.mcp.clients.find(client => client.clientId === created.client.clientId)?.token.id;
      }
      if (tokenId) await ctx.http.delete(`${path}/mcp/clients/${tokenId}`, { headers });
    }
    finally { await restoreAccess(ctx, headers, previous); }
  }
}
