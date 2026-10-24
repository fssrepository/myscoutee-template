import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { launchChromeBrowser } from '@fssrepository/myscoutee-e2e/functional/ui/lib/chrome-cdp.mjs';
import { assertUi } from '../entry/assert.mjs';
import { assertShowcase } from './assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';

const local = process.argv.includes('--local');
const fixture = local ? null : await stageChatFixture();
const browser = await launchChromeBrowser({ baseUrl: local
  ? process.env.E2E_LOCAL_BASE_URL ?? 'http://127.0.0.1:4200'
  : process.env.E2E_SERVER_BASE_URL ?? 'https://127.0.0.1' });
const page = await browser.newPage();
try {
  await assertUi({ page, local, actorId: fixture?.ids[0], actorName: fixture?.names[0],
    memberReady: async () => {
      await assertShowcase({ page, local });
      if (process.env.E2E_SCREENSHOT_DIR) {
        await mkdir(process.env.E2E_SCREENSHOT_DIR, { recursive: true });
        for (const [name, width, height] of [['desktop', 1440, 950], ['mobile', 390, 844]]) {
          await page.setViewport({ width, height });
          assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
          await writeFile(path.join(process.env.E2E_SCREENSHOT_DIR, `showcase-${name}.png`), await page.screenshot());
        }
      }
    } });
  console.log('PASS entry, consent, member login, SmartList day/week/month views and reload');
} finally {
  await page.close(); await browser.close();
  if (fixture) console.log('Owned fixture cleanup', await fixture.close());
}
