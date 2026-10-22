import { spawn } from 'node:child_process';
import { stageChatFixture } from '../../functional/ui/chat/fixture.mjs';
const fixture = await stageChatFixture();
try {
  for (const [duration, record] of [[3000, false], [15000, true]]) {
    const args = ['tests/e2e/stress/backend/run.mjs', '--scenario', 'template-websocket', '--duration-ms', String(duration)];
    if (record) args.push('--record', '--artifacts-dir', '/tmp/msc122-websocket-stress');
    await new Promise((resolve, reject) => {
      const child = spawn(process.execPath, args, { stdio: 'inherit', env: { ...process.env,
        E2E_NATIVE_DEMO_SESSIONS: 'true', E2E_SKIP_E2E_RESET: 'true',
        E2E_ADMIN_USER_ID: fixture.ids[0], E2E_DEMO_USER_ID: fixture.ids[1], E2E_CHAT_ID: fixture.chatId } });
      child.on('error', reject);
      child.on('exit', code => code === 0 ? resolve() : reject(new Error('WebSocket workload failed: ' + code)));
    });
  }
} finally { console.log('Owned socket fixture cleanup', await fixture.close()); }
