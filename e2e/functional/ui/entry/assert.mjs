import { assert } from '../../../shared/assertions.mjs';

export async function assertUi({ page, ctx, local = ctx?.mode !== 'full', memberReady, actorId = '100000000000000000000001', actorName = 'Alex Morgan', consentExpected = true }) {
  await page.navigate('/privacy-policy');
  await page.waitForSelector('.document-viewer-section-body');
  await page.waitForText('Privacy information');
  await page.navigate('/terms');
  await page.waitForSelector('.document-viewer-section-body');
  await page.navigate('/entry');
  await page.waitForText('Templates');
  await page.waitForText('v1.0.0');
  if (local) await page.clickVisible('.entry-actions .primary');
  else await page.clickByText('.entry-actions button', 'Explore');
  if (consentExpected) {
    await page.waitForSelector('app-document-viewer [role="dialog"]');
    await page.clickByText('app-document-viewer button', 'Accept and continue');
  }
  await page.waitForSelector(`.demo-user-item[data-user-id="${actorId}"]`);
  assert(!await page.isSelectorVisible('app-document-viewer [role="dialog"]'), 'Accepted browser consent must allow profile selection');
  await page.clickVisible(`.demo-user-item[data-user-id="${actorId}"]`);
  await page.waitForUrlPath('/home', 30000);
  await page.waitForText(actorName);
  await page.navigate('/home');
  await page.waitForText(actorName);
  assert(new URL(await page.evaluate(() => location.href)).pathname === '/home', 'Session and consent must survive reload');
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
  await page.clickByText('app-member-page button', 'Logout');
  await page.waitForUrlPath('/entry');
  await page.navigate('/home');
  await page.waitForUrlPath('/entry');
  assert(page.pageErrors.length === 0, 'No browser runtime exceptions', { errors: page.pageErrors });
  assert(!page.consoleMessages.some(message => message.type === 'error'), 'No handled Angular errors', { errors: page.consoleMessages.filter(message => message.type === 'error') });
  assert(!page.network.some(request => request.status >= 400), 'No failed HTTP resources', { failures: page.network.filter(request => request.status >= 400) });
}
