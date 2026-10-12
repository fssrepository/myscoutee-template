import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

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
  const transport = process.env.E2E_MONGO_TRANSPORT ?? 'docker';
  if (transport === 'kvm') {
    // The owned lab helper validates the namespace and permits its DEMO DB only.
    // Send fixture code through stdin, never credentials or a second DB server.
    const { spawn } = await import('node:child_process');
    const helper = fileURLToPath(new URL('../../microservices/tools/e2e-mongo.py', import.meta.url));
    return new Promise((resolve, reject) => {
      const child = spawn('python3', [helper, '--database', dbName], {
        timeout: Number(process.env.E2E_FIXTURE_TIMEOUT_MS ?? 30000),
        stdio: ['pipe', 'pipe', 'pipe']
      });
      let stdout = '', stderr = '';
      child.stdout.on('data', data => { stdout += data; if (stdout.length > 1024 * 1024) child.kill(); });
      child.stderr.on('data', data => { stderr += data; if (stderr.length > 1024 * 1024) child.kill(); });
      child.on('error', reject);
      child.on('close', (code, signal) => code === 0
        ? resolve({ stdout, stderr })
        : reject(new Error(`Owned KVM Mongo fixture failed (${code ?? signal}): ${stderr}`)));
      child.stdin.on('error', error => { if (error.code !== 'EPIPE') reject(error); });
      child.stdin.end(evalScript);
    });
  }
  if (transport !== 'docker') throw new Error('E2E_MONGO_TRANSPORT must be docker or kvm.');
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
