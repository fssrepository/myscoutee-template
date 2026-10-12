import { readFileSync } from 'node:fs';
import { assert } from '../../../shared/assertions.mjs';
const expectedVersion = JSON.parse(readFileSync(new URL('../../../../frontend/package.json', import.meta.url), 'utf8')).version;

export async function assertUi({ page, ctx, local = ctx?.mode !== 'full', memberReady, profileSelected, role = 'member', actorId = '100000000000000000000001', actorName = 'Alex Morgan', consentExpected = true, endsSession = false }) {
  const networkStart = page.network.length;
  const workspacePath = role === 'member' ? '/home' : '/' + role;
  const basePath = new URL(page.browser.baseUrl).pathname.replace(/\/$/, '');
  await page.navigate('/privacy-policy');
  await page.waitForSelector('.document-viewer-section-body');
  await page.waitForText('Privacy information');
  await page.navigate('/terms');
  await page.waitForSelector('.document-viewer-section-body');
  await page.navigate('/entry');
  await page.waitForText('Templates');
  await page.waitForText('v' + expectedVersion);
  await page.waitFor('bundled landing logo loaded', () => page.evaluate(() => {
    const logo = document.querySelector('.brand img');
    return Boolean(logo?.currentSrc && logo.complete && logo.naturalWidth > 0);
  }));
  if (local) await page.clickVisible('.entry-actions .primary');
  else await page.clickByText('.entry-actions button', 'Explore');
  if (consentExpected) {
    await page.waitForSelector('app-document-viewer [role="dialog"]');
    await page.clickByText('app-document-viewer button', 'Accept and continue');
  }
  if (role !== 'member') {
    await page.clickVisible('[data-guide-control="selector-role"] .app-menu__trigger');
    await page.clickVisible(`[data-guide-item="${role}"]`);
  }
  await page.waitForSelector(`.demo-user-item[data-user-id="${actorId}"]`);
  assert(!await page.isSelectorVisible('app-document-viewer [role="dialog"]'), 'Accepted browser consent must allow profile selection');
  await page.clickVisible(`.demo-user-item[data-user-id="${actorId}"]`);
  if (profileSelected) await profileSelected({ page, local });
  await page.waitForUrlPath(workspacePath, 30000);
  await page.waitForText(actorName);
  await page.navigate(workspacePath);
  await page.waitForText(actorName);
  assert(new URL(await page.evaluate(() => location.href)).pathname === basePath + workspacePath, 'Session and consent must survive reload');
  const snapshot = await page.evaluate(async () => {
    const databases = await indexedDB.databases();
    const name = databases.find(db => db.name === 'myscoutee-template_demo_db')?.name;
    if (!name) return null;
    const database = await new Promise((resolve, reject) => { const request = indexedDB.open(name); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); });
    try {
      const [users, content] = await Promise.all(['users', 'helpCenter'].map(key => new Promise((resolve, reject) => {
        const request = database.transaction('tables').objectStore('tables').get(key);
        request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
      })));
      return { users: users?.ids?.length, consentUserIds: Object.values(content?.privacyConsentsById ?? {}).map(value => value.userId) };
    } finally { database.close(); }
  });
  if (local) {
    assert(snapshot?.users === 4, 'Local seed contains the four template actors');
    assert(snapshot.consentUserIds.includes(actorId), 'Consent must be persisted in IndexedDB');
    assert(!page.network.some(request => new URL(request.url).pathname.startsWith('/api/')), 'Local mode must not call the Java API');
  } else {
    assert(!snapshot?.users, 'HTTP entry must not seed browser-local demo users');
    for (const route of ['/auth/session/login', '/auth/me', '/privacy/consents']) {
      assert(page.network.some(request => new URL(request.url).pathname === '/api' + route && request.status < 400), 'Expected real backend request: ' + route);
    }
  }
  if (memberReady) await memberReady({ page, local });
  if (!endsSession) {
    await page.clickVisible('.avatar-menu [data-guide-item="navigator-avatar"]');
    await page.clickVisible('.settings-menu .app-menu__trigger');
    await page.clickVisible('[data-guide-item="logout"]');
  }
  await page.waitForUrlPath('/entry');
  await page.navigate(workspacePath);
  await page.waitForUrlPath('/entry');
  assert(page.pageErrors.length === 0, 'No browser runtime exceptions', { errors: page.pageErrors });
  assert(!page.consoleMessages.some(message => message.type === 'error'), 'No handled Angular errors', { errors: page.consoleMessages.filter(message => message.type === 'error') });
  assertNoResourceFailures(page, local, [workspacePath], networkStart);
}

export function assertNoResourceFailures(page, local, workspacePaths = ['/home'], fromIndex = 0) {
  const basePath = new URL(page.browser.baseUrl).pathname.replace(/\/$/, '');
  const expectedPagesRedirect = request => process.env.E2E_PAGES_REDIRECT === 'true'
    && local && request.status === 404 && request.type === 'Document'
    && ['/entry','/privacy-policy','/terms',...workspacePaths].some(route =>
      new URL(request.url).origin === new URL(page.browser.baseUrl).origin
      && new URL(request.url).pathname === basePath + route);
  const failures = page.network.slice(fromIndex).filter(request => request.status >= 400 && !expectedPagesRedirect(request));
  assert(!failures.length, 'No failed HTTP resources beyond the explicit Pages document redirect', { failures });
}
