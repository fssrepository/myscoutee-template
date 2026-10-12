import { assert } from '../../../shared/assertions.mjs';

export async function assertShowcase({ page, local }) {
  await page.clickVisible('.user-selector-menu [data-guide-item="navigator-avatar"]');
  await page.clickVisible('.user-menu-content [data-guide-item="showcase"]');
  await page.waitForText('Coastal light');
  const cards = await page.evaluate(() => [...document.querySelectorAll('app-showcase app-info-card')].map(card => card.innerText));
  assert(cards.length === 4, 'The seeded SmartList must show four cards', { cards });
  for (const title of ['Coastal light', 'Quiet peaks', 'Desert rhythm', 'Forest canopy']) {
    assert(cards.some(card => card.includes(title)), 'Missing sample card: ' + title);
  }
  await page.waitForSelector('app-showcase .lazy-bg-loaded');
  assert(page.network.some(request => new URL(request.url).pathname.endsWith('/showcase/coast.svg') && request.status === 200), 'The visible landscape image must load');
  const selectView = async view => {
    await page.clickVisible('[data-guide-control="view"] .app-menu__trigger');
    await page.clickVisible(`[data-guide-item="${view}"]`);
  };
  await selectView('week');
  await page.waitForSelector('app-showcase .smart-list--week');
  await selectView('month');
  await page.waitForSelector('app-showcase .smart-list--month');
  await page.navigate('/home?page=showcase&view=month');
  await page.waitForSelector('app-showcase .smart-list--month');
  await selectView('day');
  await page.waitForText('Forest canopy');
  assert(await page.evaluate(() => document.querySelectorAll('app-showcase app-info-card').length) === 4, 'Day view restores all four InfoCards');
  assert(!await page.isSelectorVisible('app-showcase .smart-list__page-next'), 'Discover uses the native scrolling list without gallery arrows');
  await page.clickVisible('app-popup:has(app-showcase) [data-guide-control="close"]');
  if (local) {
    assert(!page.network.some(request => new URL(request.url).pathname.startsWith('/api/')), 'Local SmartList must not call the backend');
  } else {
    assert(page.network.some(request => new URL(request.url).pathname === '/api/showcase/cards' && request.status === 200), 'HTTP SmartList must load from the backend');
  }
}
