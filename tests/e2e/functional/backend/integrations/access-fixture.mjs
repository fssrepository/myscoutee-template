// Restore only the access preferences changed by these owned-client tests.
export async function saveAccess(ctx, headers, settings, changes = {}) {
  return (await ctx.http.request('PUT', `${ctx.vars.apiPrefix}/integrations/settings`, {
    headers, json: {
      accessRevision: settings.accessRevision,
      mcpAccess: changes.mcpAccess ?? settings.mcp.accessMode,
      clients: changes.clients ?? []
    }
  })).data;
}

export async function restoreAccess(ctx, headers, previous) {
  const current = (await ctx.http.get(`${ctx.vars.apiPrefix}/integrations/settings`, { headers })).data;
  const active = new Set([...current.tokens, ...current.mcp.clients.map(client => client.token)].map(token => token.id));
  const clients = [...previous.tokens, ...previous.mcp.clients.map(client => client.token)]
    .filter(token => active.has(token.id)).map(({ id, accessMode }) => ({ id, accessMode }));
  await saveAccess(ctx, headers, current, { mcpAccess: previous.mcp.accessMode, clients });
}
