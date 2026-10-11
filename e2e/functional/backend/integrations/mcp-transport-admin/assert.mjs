import { assertMcpFlow } from '../mcp-flow.mjs';
export const assertResponse = args => assertMcpFlow({ ...args, ctx: { ...args.ctx, vars: { ...args.ctx.vars, userId: args.ctx.vars.adminUserId } } }, true, 'admin');
