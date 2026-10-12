import assert from 'node:assert/strict';
import { readFile, stat, mkdir, writeFile } from 'node:fs/promises';
import { launchChromeBrowser } from '../lib/chrome-cdp.mjs';

assert(process.env.E2E_MANAGED_BROWSER_MANIFEST, 'Use the versioned installed checkpoint launcher');
const credentialLog = process.env.E2E_INSTALL_LOG;
assert(credentialLog, 'Provide the private installer log containing the generated bootstrap credential');
assert.equal((await stat(credentialLog)).mode & 0o077, 0, 'Installer credentials must stay private');
const log = await readFile(credentialLog, 'utf8');
const email = log.match(/^\s*Email:\s*(\S+)/m)?.[1];
const password = log.match(/^\s*Password:\s*(\S+)/m)?.[1];
assert(email && password, 'The fresh installation must issue its own operator credential');
const baseUrl = process.env.E2E_SERVER_BASE_URL;
assert(baseUrl, 'Specify the installed deployment explicitly');
const browser = await launchChromeBrowser({ baseUrl }), page = await browser.newPage();
const evidence = { bootstrapLogin: false, configuration: false, installedHostControls: false, updateVersion: false, mobile: false, logoutConfirmation: false };
try {
  await page.navigate('/entry');
  await page.waitForSelector('[data-guide-item="operator-access"]');
  if (await page.isSelectorVisible('.explanation-guide-overlay__next button')) {
    await page.clickVisible('.explanation-guide-overlay__next button');
    await page.waitForSelectorHidden('.explanation-guide-overlay');
  }
  await page.clickVisible('[data-guide-item="operator-access"]');
  await page.waitForSelector('app-document-viewer [role="dialog"], .firebase-auth-provider-btn.email');
  if (await page.isSelectorVisible('app-document-viewer [role="dialog"]')) {
    await page.clickByText('app-document-viewer button', 'Accept and continue');
  }
  await page.waitForSelector('.firebase-auth-provider-btn.email');
  await page.clickVisible('.firebase-auth-provider-btn.email');
  await page.fill('.firebase-auth-email-form input[name="email"]', email);
  await page.fill('.firebase-auth-email-form input[name="password"]', password);
  await page.clickVisible('.firebase-auth-email-form button[type="submit"]');
  await page.waitForUrlPath('/operator', 45000);
  assert(page.network.some(r => new URL(r.url).pathname === '/api/auth/operator-bootstrap' && r.status === 200));
  evidence.bootstrapLogin = true;
  await page.clickVisible('.operator-actions [data-guide-item="configuration"]');
  const popup = 'app-operator-configuration-popup';
  await page.waitForSelector(popup + ' .operator-action-popup__tls-header');
  await page.waitFor('installed configuration ready', () => page.evaluate(() =>
    document.querySelector('app-operator-configuration-popup [data-palette="violet"] input')?.disabled === false));
  assert.equal(await page.evaluate(() => document.querySelectorAll('app-operator-configuration-popup .operator-action-popup__configuration-group').length), 8);
  evidence.configuration = true;
  assert.equal(await page.evaluate(() => document.querySelector('.operator-action-popup__tls-header .operator-action-popup__on-off-toggle')?.disabled), false,
    'Installed REAL operator must have the package-owned TLS controls');
  evidence.installedHostControls = true;
  if (process.env.E2E_SCREENSHOT_DIR) {
    await mkdir(process.env.E2E_SCREENSHOT_DIR, { recursive: true });
    await writeFile(process.env.E2E_SCREENSHOT_DIR + '/operator-installed-desktop.png', await page.screenshot());
  }
  await page.setViewport({ width: 390, height: 844 });
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  if (process.env.E2E_SCREENSHOT_DIR) await writeFile(process.env.E2E_SCREENSHOT_DIR + '/operator-installed-mobile.png', await page.screenshot());
  evidence.mobile = true;
  await page.clickVisible(popup + ' > app-popup .ui-popup__header .ui-popup__close');
  await page.setViewport({ width: 1440, height: 950 });
  await page.clickVisible('.operator-actions [data-guide-item="updates"]');
  await page.waitFor('installed update ready', () => page.evaluate(() => document.querySelector('app-operator-update-popup .operator-action-popup')?.getAttribute('aria-busy') === 'false'), 45000);
  assert.equal(await page.evaluate(() => document.querySelector('.operator-action-popup__version--current')?.textContent.trim()), '1.4.0');
  evidence.updateVersion = true;
  await page.clickVisible('app-operator-update-popup > app-popup .ui-popup__header .ui-popup__close');
  await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');
  await page.clickVisible('[data-guide-item="settings"]');
  await page.clickVisible('[data-guide-item="logout"]');
  await page.waitForSelector('.dialog-panel');
  await page.clickVisible('.dialog-panel [data-guide-item="confirm"]');
  await page.waitForUrlPath('/entry');
  evidence.logoutConfirmation = true;
  assert.equal(page.pageErrors.length, 0);
  console.log(JSON.stringify(evidence));
} catch (error) {
  console.error(error.message);
  console.error(JSON.stringify({ evidence, path: await page.evaluate(() => location.pathname), failedRoutes: page.network.filter(r => r.status >= 400).map(r => ({ path: new URL(r.url).pathname, status: r.status })) }));
  process.exitCode = 1;
} finally { await page.close(); await browser.close(); }
