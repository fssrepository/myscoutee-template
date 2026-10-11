import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

export async function queryE2eMongo(ctx, expression) {
  const { stdout } = await runE2eMongo(
    ctx,
    `print(EJSON.stringify((${expression}), { relaxed: true }));`
  );
  return JSON.parse(stdout.trim());
}

export async function runE2eMongo(ctx, evalScript) {
  const dbName = ctx.vars.e2eDatabase
    ?? process.env.E2E_DATABASE
    ?? process.env.MONGO_E2E_DATABASE
    ?? 'e2e_db';
  return execFileAsync('docker', [
    'exec',
    process.env.E2E_MONGO_CONTAINER ?? 'mongodb',
    'mongosh',
    '--username',
    process.env.E2E_MONGO_USERNAME ?? process.env.MONGO_APP_USERNAME ?? 'guest',
    '--password',
    process.env.E2E_MONGO_PASSWORD ?? process.env.MONGO_APP_PASSWORD ?? 'guest',
    '--authenticationDatabase',
    dbName,
    '--quiet',
    dbName,
    '--eval',
    evalScript
  ], {
    timeout: Number(process.env.E2E_FIXTURE_TIMEOUT_MS ?? 30000),
    maxBuffer: 1024 * 1024
  });
}

