import assert from 'node:assert/strict';

// Actual MCP HTTP boundary. Metadata/headers follow the pinned native Go SDK.
export async function mcpRequest(ctx, token, method, params = {}, expectedStatus = 200) {
  const modern = '2026-07-28';
  const headers = {
    Accept: 'application/json, text/event-stream',
    'MCP-Protocol-Version': modern, 'Mcp-Method': method,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(method === 'tools/call' ? { 'Mcp-Name': params.name } : {}),
    ...(method === 'resources/read' ? { 'Mcp-Name': params.uri } : {})
  };
  return ctx.http.post(token === null ? '/mcp' : '/mcp/private', {
    headers, expectedStatus,
    json: { jsonrpc: '2.0', id: 1, method, params: { ...params, _meta: {
      'io.modelcontextprotocol/protocolVersion': modern,
      'io.modelcontextprotocol/clientInfo': { name: 'template-e2e', version: '1.0.0' },
      'io.modelcontextprotocol/clientCapabilities': {}
    } } }
  });
}

function result(response) {
  assert.equal(response.data.error, undefined, 'MCP request must succeed');
  assert.notEqual(response.data.result?.isError, true, 'MCP tool must succeed');
  return response.data.result;
}

export async function assertMcpTransport(ctx, token, role = 'member') {
  const publicDiscovery = result(await mcpRequest(ctx, null, 'server/discover'));
  assert.equal(publicDiscovery._meta['io.modelcontextprotocol/serverInfo'].name, 'myscoutee-template');
  assert.equal(publicDiscovery._meta['io.modelcontextprotocol/serverInfo'].version, '1.0.0');
  assert(!('events' in publicDiscovery.capabilities));
  assert.deepEqual(result(await mcpRequest(ctx, null, 'tools/list')).tools.map(x => x.name).sort(),
    ['list_help_topics', 'read_help_topic', 'search_help']);
  const resources = result(await mcpRequest(ctx, null, 'resources/list')).resources;
  assert.deepEqual(resources.map(x => x.uri).sort(), ['template://help/en', 'template://help/hu']);
  for (const language of ['en', 'hu']) {
    const help = result(await mcpRequest(ctx, null, 'tools/call', { name: 'list_help_topics', arguments: { language } })).structuredContent;
    assert(help.revisionId && help.sections.length > 0);
    const topic = result(await mcpRequest(ctx, null, 'tools/call', {
      name: 'read_help_topic', arguments: { language, topic: help.sections[0].id }
    })).structuredContent;
    assert.equal(topic.revisionId, help.revisionId);
    assert(topic.sections[0].text.length > 0);
    const resource = result(await mcpRequest(ctx, null, 'resources/read', { uri: `template://help/${language}` }));
    assert.equal(JSON.parse(resource.contents[0].text).revisionId, help.revisionId);
  }
  const challenge = await mcpRequest(ctx, '', 'tools/list', {}, 401);
  assert.match(challenge.headers['www-authenticate'], /oauth-protected-resource\/mcp\/private/);
  await mcpRequest(ctx, 'msc_wrong-scope', 'tools/list', {}, 401);
  const privateDiscovery = result(await mcpRequest(ctx, token, 'server/discover'));
  assert.deepEqual(privateDiscovery.capabilities.events, {});
  const listedResponse = await mcpRequest(ctx, token, 'tools/list');
  assert.match(listedResponse.headers['cache-control'], /private.*no-store/);
  const listed = result(listedResponse);
  const expected = role === 'admin' ? ['adminOverview'] : ['listChats', 'chatDetails', 'chatMessages', 'listNotifications'];
  assert.deepEqual(listed.tools.map(x => x.name).sort(), [...expected, 'list_help_topics', 'read_help_topic', 'search_help'].sort());
  assert.equal(listed.cacheScope, 'private');
  assert.equal(listed.ttlMs, 0);
  for (const tool of listed.tools.filter(x => expected.includes(x.name))) {
    assert.equal(tool.annotations.readOnlyHint, true);
    assert(tool.description.includes(ctx.vars.userId), 'Discovery must identify the authenticated owner');
  }
  if (role === 'admin') {
    const overview = result(await mcpRequest(ctx, token, 'tools/call', { name: 'adminOverview', arguments: {} })).structuredContent;
    assert.equal(typeof overview.attention.unreadMessages, 'number');
    for (const field of ['unresolvedReports', 'unresolvedFeedback', 'supportCases']) {
      assert.equal(Object.hasOwn(overview.attention, field), false);
    }
    assert(!('statistics' in overview));
  } else {
    result(await mcpRequest(ctx, token, 'tools/call', { name: 'listChats', arguments: { limit: 2 } }));
    result(await mcpRequest(ctx, token, 'tools/call', { name: 'listNotifications', arguments: { limit: 2 } }));
    const forged = await mcpRequest(ctx, token, 'tools/call', { name: 'listChats', arguments: { userId: ctx.vars.secondUserId } });
    assert(forged.data.error || forged.data.result?.isError, 'Caller cannot override the owner');
    const missing = await mcpRequest(ctx, token, 'tools/call', { name: 'chatDetails', arguments: { id: 'not-owned-by-this-fixture' } });
    assert(missing.data.result?.isError, 'Inaccessible chat is denied');
  }
  const omitted = await mcpRequest(ctx, token, 'tools/call', { name: 'createEvents', arguments: {} }, 400);
  assert(omitted.data.error, 'Unselected product operations are not registered');
  assert.deepEqual(result(await mcpRequest(ctx, token, 'events/list')).events, [], 'Read access does not enable Events');
}
