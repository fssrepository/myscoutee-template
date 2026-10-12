export async function prepare({ ctx }) {
  if (!ctx.nativeSessions) throw new Error('Owned native sessions are required for WebSocket stress.');
  const headers = await ctx.nativeSessions.headersForRequest('GET', ctx.http.baseUrl + '/api/auth/me', {
    headers: { 'X-App-Session-Kind': 'demo', 'X-Demo-User-Id': ctx.vars.userId }
  });
  ctx.vars.socketSessionId = headers['X-App-Session-Id'];
  return {};
}
