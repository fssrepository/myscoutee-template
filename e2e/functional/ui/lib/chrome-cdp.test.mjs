import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import { launchChromeBrowser } from './chrome-cdp.mjs';

test('visible launcher attachment preserves ownership and releases its allocation', async () => {
  const server = http.createServer((_request, response) => response.end('{}'));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const directory = await mkdtemp(path.join(os.tmpdir(), 'managed-chrome-test-'));
  const manifest = path.join(directory, 'session.json');
  const previous = process.env.E2E_MANAGED_BROWSER_MANIFEST;
  process.env.E2E_MANAGED_BROWSER_MANIFEST = manifest;
  try {
    await writeFile(manifest, JSON.stringify({ headless: false,
      sessions: [{ devToolsPort: port, fakeMediaStream: true }] }));
    const first = await launchChromeBrowser({ fakeMediaStream: true });
    await assert.rejects(launchChromeBrowser(), /enough managed browser sessions/);
    await first.close();
    assert.equal((await fetch(`http://127.0.0.1:${port}/json/version`)).status, 200);
    const second = await launchChromeBrowser();
    await second.close();
    await writeFile(manifest, JSON.stringify({ headless: true, sessions: [{ devToolsPort: port }] }));
    await assert.rejects(launchChromeBrowser(), /visible launcher sessions/);
  } finally {
    if (previous === undefined) delete process.env.E2E_MANAGED_BROWSER_MANIFEST;
    else process.env.E2E_MANAGED_BROWSER_MANIFEST = previous;
    await new Promise(resolve => server.close(resolve));
    await rm(directory, { recursive: true, force: true });
  }
});
