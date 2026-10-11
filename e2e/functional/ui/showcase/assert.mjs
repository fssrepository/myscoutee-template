import { assert } from '../../../shared/assertions.mjs';

export async function assertShowcase({ page, local }) {
  await page.waitForText('Coastal light');
  const cards = await page.evaluate(() => [...document.querySelectorAll('app-showcase app-image-card')].map(card => card.innerText));
  assert(cards.length === 4, 'The seeded SmartList must show four cards', { cards });
  for (const title of ['Coastal light', 'Quiet peaks', 'Desert rhythm', 'Forest canopy']) {
    assert(cards.some(card => card.includes(title)), 'Missing sample card: ' + title);
  }
  await page.waitForSelector('app-showcase .lazy-bg-loaded');
  assert(page.network.some(request => new URL(request.url).pathname.endsWith('/showcase/coast.svg') && request.status === 200), 'The visible landscape image must load');
  await page.clickVisible('.showcase-view-menu [data-guide-item="rows"]');
  await page.waitForSelector('app-showcase single-row');
  assert(await page.evaluate(() => document.querySelectorAll('app-showcase single-row').length) === 4, 'Rows must contain the same four cards');
  await page.navigate('/home?view=rows');
  await page.waitForSelector('app-showcase single-row');
  assert(await page.evaluate(() => document.querySelectorAll('app-showcase single-row').length) === 4, 'The selected view must survive reload');
  await page.clickVisible('.showcase-view-menu [data-guide-item="gallery"]');
  await page.waitForSelector('.smart-list__surface--fullscreen');
  await page.waitForText('Coastal light');
  await page.clickVisible('.showcase-view-menu [data-guide-item="cards"]');
  await page.waitForText('Forest canopy');
  assert(await page.evaluate(() => document.querySelectorAll('app-showcase app-image-card').length) === 4, 'Returning from gallery must restore the cards');
  if (local) {
    assert(!page.network.some(request => new URL(request.url).pathname.startsWith('/api/')), 'Local SmartList must not call the backend');
  } else {
    assert(page.network.some(request => new URL(request.url).pathname === '/api/showcase/cards' && request.status === 200), 'HTTP SmartList must load from the backend');
  }
}
