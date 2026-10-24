import { fileURLToPath } from 'node:url';
import * as fixtures from '@fssrepository/myscoutee-e2e/shared/mongo-fixture.mjs';

const repoRoot = fileURLToPath(new URL('../../', import.meta.url));
export const queryE2eMongo = (ctx, expression) => fixtures.queryE2eMongo(ctx, expression, { repoRoot });
export const runE2eMongo = (ctx, script) => fixtures.runE2eMongo(ctx, script, { repoRoot });
