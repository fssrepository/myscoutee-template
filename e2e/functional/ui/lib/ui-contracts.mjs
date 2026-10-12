import { assert } from '../../../shared/assertions.mjs';

const APP_BACKEND_UI_WINDOW_MS = 3500;
export const ENTRY_PRIMARY_ACTION_SELECTOR = '.entry-hero-cta-menu .app-menu__button-row-item:first-of-type';

export async function clickEntryPrimaryAction(page, timeoutMs = 15000) {
  await page.clickVisible(ENTRY_PRIMARY_ACTION_SELECTOR, { timeoutMs });
}

export async function applyCurrentDemoSessionHeaders(page) {
  const session = await page.evaluate(() => {
    try {
      return JSON.parse(window.localStorage.getItem('myscoutee.http.session.v1') || '{}');
    } catch {
      return {};
    }
  });
  assert(session?.kind === 'demo', 'Expected an active demo browser session before applying request headers');
  assert(`${session.userId ?? ''}`.trim(), 'Demo browser session should contain a user id');
  assert(`${session.sessionId ?? ''}`.trim(), 'Demo browser session should contain a registered session id');
  await page.setExtraHttpHeaders({
    'X-App-Session-Kind': 'demo',
    'X-Demo-User-Id': session.userId.trim(),
    'X-App-Session-Id': session.sessionId.trim()
  });
  return session;
}

export async function assertBrowserExpectations(page, expected = {}) {
  for (const selector of expected.selectors ?? []) {
    await page.waitForSelector(selector, { timeoutMs: expected.timeoutMs });
  }
  for (const selector of expected.absentSelectors ?? []) {
    await assertNoVisibleSelector(page, selector);
  }
  for (const selector of expected.optionalSelectors ?? []) {
    await page.waitForSelector(selector, { timeoutMs: expected.timeoutMs }).catch(() => undefined);
  }
  for (const item of expected.minVisible ?? []) {
    const selector = typeof item === 'string' ? item : item.selector;
    const min = typeof item === 'string' ? 1 : Number(item.min ?? 1);
    const label = typeof item === 'string' ? item : item.label ?? selector;
    await assertVisibleCount(page, selector, min, label);
  }
  for (const text of expected.text ?? []) {
    await page.waitForText(text, { timeoutMs: expected.timeoutMs });
  }
  for (const text of expected.notText ?? []) {
    await assertTextAbsent(page, text);
  }
  for (const networkPattern of expected.network ?? []) {
    await page.waitForNetwork(networkPattern, { timeoutMs: expected.timeoutMs });
  }
  if (expected.noPageErrors !== false) {
    assert(page.pageErrors.length === 0, 'Browser page should not throw runtime exceptions', {
      pageErrors: page.pageErrors
    });
  }
  if (expected.noFailedApi !== false) {
    const failedApiResponses = page.network.filter(entry => {
      return entry.url.includes('/api/') && entry.status >= 400;
    });
    assert(failedApiResponses.length === 0, 'Browser should not receive failed API responses', {
      failedApiResponses
    });
  }
  if (expected.noFailedNetwork === true) {
    assert(page.failedNetwork.length === 0, 'Browser should not have failed network loads', {
      failedNetwork: page.failedNetwork
    });
  }
  if (expected.noConsoleErrors === true) {
    const consoleErrors = page.consoleMessages.filter(entry => entry.type === 'error');
    assert(consoleErrors.length === 0, 'Browser console should not log errors', { consoleErrors });
  }
  if (expected.noCrashText === true) {
    await assertNoVisibleCrashFallback(page);
  }
  if (expected.noBrokenImages === true) {
    await assertNoBrokenImages(page);
  }
}

export async function openNavigatorMenu(page) {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await settleFirstRunOverlays(page, { timeoutMs: attempt === 0 ? 15000 : 5000 });
    if (await page.isSelectorVisible('.user-menu-panel.open')) {
      return;
    }
    await waitForNavigatorButtonReady(page);
    await page.clickVisible('.navigator-trigger-rail:not(.is-covered) .user-selector-menu button[aria-label="Open user menu"]');
    const opened = await waitForNavigatorMenuOrBlockingOverlay(page, 5000);
    if (opened && await page.isSelectorVisible('.user-menu-panel.open')) {
      return;
    }
    await settleFirstRunOverlays(page, { timeoutMs: 5000 });
  }
  const snapshot = await navigatorMenuSnapshot(page);
  page.recordFinding?.({
    severity: 'error',
    title: 'Navigator menu did not open after clicking the visible avatar button',
    details: snapshot
  });
  await page.waitForSelector('.user-menu-panel.open');
}

async function waitForNavigatorButtonReady(page) {
  await page.waitFor('visible global navigator button enabled', async () => page.evaluate(() => {
    const visible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none'
        && style.pointerEvents !== 'none';
    };
    return Array.from(document.querySelectorAll('.navigator-trigger-rail:not(.is-covered) .user-selector-menu button[aria-label="Open user menu"]'))
      .some(button => visible(button) && !button.disabled && !button.classList.contains('is-covered'));
  }));
}

async function waitForNavigatorMenuOrBlockingOverlay(page, timeoutMs = 5000) {
  return page.waitFor('navigator menu or blocking overlay after avatar click', async () => page.evaluate(() => {
    const visible = element => {
      if (!element) {
        return false;
      }
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    return visible(document.querySelector('.user-menu-panel.open'))
      || visible(document.querySelector('app-explanation-popup .ui-popup__panel'))
      || visible(document.querySelector('.app-install-prompt'))
      || visible(document.querySelector('app-navigator-privacy-popup'));
  }), timeoutMs).then(() => true).catch(() => false);
}

async function navigatorMenuSnapshot(page) {
  return page.evaluate(() => {
    const visible = element => {
      if (!element) {
        return false;
      }
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return {
        visible: rect.width > 0
          && rect.height > 0
          && style.visibility !== 'hidden'
          && style.display !== 'none',
        rect: {
          x: rect.x,
          y: rect.y,
          width: rect.width,
          height: rect.height
        },
        visibility: style.visibility,
        display: style.display,
        pointerEvents: style.pointerEvents,
        disabled: Boolean(element.disabled),
        classes: element.className,
        ariaLabel: element.getAttribute('aria-label'),
        title: element.getAttribute('title')
      };
    };
    return {
      url: window.location.href,
      bodyClasses: document.body.className,
      buttons: Array.from(document.querySelectorAll('.user-selector-menu button')).map(visible),
      menuPanel: visible(document.querySelector('.user-menu-panel')),
      openMenuPanel: visible(document.querySelector('.user-menu-panel.open')),
      explanationPopup: visible(document.querySelector('app-explanation-popup .ui-popup__panel')),
      installPrompt: visible(document.querySelector('.app-install-prompt')),
      privacyPopup: visible(document.querySelector('app-navigator-privacy-popup')),
      assetPopup: visible(document.querySelector('.asset-popup-content'))
    };
  });
}

export async function assertActiveBackendCalls(page, patterns) {
  for (const pattern of patterns) {
    assert(page.hasNetwork(pattern), `Expected backend call matching ${pattern}`, {
      network: page.network.map(entry => ({ url: entry.url, status: entry.status }))
    });
  }
}

export async function assertVisibleCount(page, selector, minCount, label = selector) {
  let count = await page.visibleCount(selector);
  if (count < minCount) {
    await waitForVisibleCount(page, selector, minCount, label, APP_BACKEND_UI_WINDOW_MS + 2500)
      .catch(() => undefined);
    count = await page.visibleCount(selector);
  }
  assert(count >= minCount, `${label} should have at least ${minCount} visible item(s)`, { count });
}

export async function waitForVisibleCount(page, selector, minCount, label = selector, timeoutMs = 30000) {
  await page.waitFor(`${label} visible count >= ${minCount}`, async () => {
    const count = await page.visibleCount(selector);
    return count >= minCount;
  }, timeoutMs);
}

export async function waitForSmartListSettled(page, rootSelector, timeoutMs = 45000) {
  await page.waitFor(`${rootSelector} smart-list settled`, async () => {
    const state = await backendUiState(page, rootSelector, { includeLazyImages: false });
    return !state.busy;
  }, timeoutMs);
}

export async function selectActivitiesEventScope(page, label, timeoutMs = 30000) {
  const eventScopeLabels = [
    'All',
    'Active Events',
    'Pending',
    'Invitations',
    'My Events',
    'Drafts',
    'Watchlist',
    'Trash'
  ];
  const triggerState = await page.evaluate(({ eventScopeLabels, label }) => {
    const visible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    const popupPanel = document.querySelector('.activities-popup')?.closest('.ui-popup__panel');
    const triggers = Array.from((popupPanel ?? document).querySelectorAll(
      '.ui-popup__toolbar .app-menu__trigger'
    )).filter(visible);
    const eventScopeTrigger = triggers.find(trigger => {
      const text = `${trigger.querySelector('.app-menu__trigger-label')?.textContent ?? ''}`.trim();
      return eventScopeLabels.includes(text);
    });
    const text = `${eventScopeTrigger?.querySelector('.app-menu__trigger-label')?.textContent ?? ''}`.trim();
    if (text === label) {
      return { found: true, selected: true, text };
    }
    eventScopeTrigger?.click();
    return { found: Boolean(eventScopeTrigger), selected: false, text };
  }, { eventScopeLabels, label });
  assert(triggerState.found, 'Activities popup should expose an event-scope selector', {
    label,
    triggerState
  });
  if (!triggerState.selected) {
    const optionSelector = '.ui-popup__panel:has(.activities-popup) .ui-popup__toolbar .app-menu__panel button';
    await page.waitForSelector(optionSelector, { timeoutMs });
    await page.clickByText(optionSelector, label, { timeoutMs });
  }
  await page.waitFor(`activities event scope ${label}`, async () => page.evaluate(label => {
    const visible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    const popupPanel = document.querySelector('.activities-popup')?.closest('.ui-popup__panel');
    return Array.from((popupPanel ?? document).querySelectorAll(
      '.ui-popup__toolbar .app-menu__trigger'
    )).filter(visible).some(trigger => {
      const text = `${trigger.querySelector('.app-menu__trigger-label')?.textContent ?? ''}`.trim();
      return text === label;
    });
  }, label), timeoutMs);
  await waitForSmartListSettled(page, '.activities-popup', timeoutMs);
}

export async function waitForBackendUiWindow(page, rootSelector = 'body', timeoutMs = APP_BACKEND_UI_WINDOW_MS + 2500) {
  const started = Date.now();
  let sawBusy = false;
  await page.waitFor(`${rootSelector} backend UI window settled`, async () => {
    const state = await backendUiState(page, rootSelector);
    sawBusy = sawBusy || state.busy;
    if (state.busy) {
      return false;
    }
    return sawBusy || Date.now() - started >= APP_BACKEND_UI_WINDOW_MS;
  }, timeoutMs, 150);
}

export async function waitForAppLazyLoadWindow(page, rootSelector = 'body', timeoutMs = APP_BACKEND_UI_WINDOW_MS + 2500) {
  await waitForBackendUiWindow(page, rootSelector, timeoutMs);
}

async function backendUiState(page, rootSelector = 'body', options = {}) {
  return page.evaluate(({ rootSelector, includeLazyImages }) => {
    const root = document.querySelector(rootSelector) ?? document;
    const visible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    const selectors = [
      '.header-progress-bar.is-state-loading',
      '.header-progress-bar.is-state-loading-overdue',
      '.popup-loading-overlay',
      '.smart-list__loading',
      '.is-pane-loading',
      '[aria-busy="true"]',
      '.document-viewer-status-loading',
      '.demo-user-progress',
      '.explanation-popup__loading-body',
      '.event-editor-save-btn-shell.is-loading',
      '.event-editor-save-btn-ring-progress.is-loading',
      '.dialog-actions-menu .app-menu__button-row-item--progress-loading',
      '.event-checkout-confirm-shell.is-loading',
      '.event-checkout-confirm-ring-progress.is-loading',
      '.profile-onboarding-save-shell.is-loading',
      '.profile-onboarding-save-ring-progress.is-loading',
      '.asset-popup-basket-save-btn-shell.is-loading',
      '.asset-popup-basket-save-btn-ring-progress.is-loading',
      '[class*="-save-btn-shell"].is-loading',
      '[class*="-delete-btn-shell"].is-loading',
      '[class*="-confirm-shell"].is-loading',
      '[class*="-ring-progress"].is-loading',
      '[class*="-upload-ring"].is-loading'
    ];
    if (includeLazyImages !== false) {
      selectors.push('.lazy-bg-loading');
    }
    const active = selectors.flatMap(selector => {
      return Array.from(root.querySelectorAll(selector))
        .filter(visible)
        .map(element => ({
          selector,
          className: element.className,
          text: (element.textContent ?? '').trim().replace(/\s+/g, ' ').slice(0, 120)
        }));
    });
    return {
      busy: active.length > 0,
      active
    };
  }, { rootSelector, includeLazyImages: options.includeLazyImages !== false });
}

export async function assertInfoCardMenuAnchored(page, options = {}) {
  const geometry = await inspectInfoCardMenuAnchor(page, options);
  const maxHorizontalDrift = Number(options.maxHorizontalDriftPx ?? 48);
  const maxVerticalGap = Number(options.maxVerticalGapPx ?? 24);
  assert(geometry.found, 'Info-card action menu should be open before checking anchoring', geometry);
  assert(geometry.menuOverlapsCardHorizontally, 'Info-card action menu should stay horizontally anchored to its card', geometry);
  assert(geometry.opensUp || geometry.opensDown, 'Info-card action menu should open above or below the three-dot trigger', geometry);
  assert(geometry.horizontalDrift <= maxHorizontalDrift, 'Info-card action menu should not drift away from its trigger', {
    ...geometry,
    maxHorizontalDrift
  });
  assert(geometry.verticalGap <= maxVerticalGap, 'Info-card action menu should stay close to its trigger vertically', {
    ...geometry,
    maxVerticalGap
  });
  return geometry;
}

export async function recordInfoCardMenuAnchorFinding(page, options = {}) {
  const geometry = await inspectInfoCardMenuAnchor(page, options);
  const maxHorizontalDrift = Number(options.maxHorizontalDriftPx ?? 48);
  const maxVerticalGap = Number(options.maxVerticalGapPx ?? 24);
  const mobileBreakpoint = Number(options.mobileBreakpointPx ?? 760);
  if (options.skipMobile !== false && Number(geometry.viewportWidth ?? 0) <= mobileBreakpoint) {
    return {
      ok: true,
      geometry: {
        ...geometry,
        skipped: 'mobile-menu-positioning-allowed',
        mobileBreakpoint
      }
    };
  }
  const ok = geometry.found
    && geometry.menuOverlapsCardHorizontally
    && (geometry.opensUp || geometry.opensDown)
    && geometry.horizontalDrift <= maxHorizontalDrift
    && geometry.verticalGap <= maxVerticalGap;
  if (!ok && typeof page.recordFinding === 'function') {
    await page.recordStep(options.stepName ?? 'info-card-menu-anchor-finding');
    page.recordFinding({
      severity: options.severity ?? 'warning',
      title: options.title ?? 'Info-card action menu is not anchored cleanly to its trigger',
      details: {
        ...geometry,
        maxHorizontalDrift,
        maxVerticalGap
      }
    });
  }
  return { ok, geometry };
}

export async function inspectInfoCardMenuAnchor(page, options = {}) {
  const settleMs = Number(options.settleMs ?? 250);
  if (settleMs > 0) {
    await page.evaluate(ms => new Promise(resolve => window.setTimeout(resolve, ms)), settleMs);
  }
  return page.evaluate(() => {
    const visible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    const menu = Array.from(document.querySelectorAll('.ui-info-card__menu.is-open, .ui-info-card__menu, .activities-event-action-menu.item-action-menu.is-open'))
      .find(visible);
    const trigger = Array.from(document.querySelectorAll('.ui-info-card__menu-trigger.is-open, .experience-action-menu-trigger.is-open'))
      .filter(visible)
      .map(element => ({
        element,
        rect: element.getBoundingClientRect(),
        card: element.closest('.ui-info-card, .experience-item-card, .activities-row-item')
      }))
      .filter(item => item.card)
      [0] ?? null;
    const card = trigger?.card ?? null;
    if (!trigger || !card || !menu) {
      return {
        found: false,
        viewportWidth: window.innerWidth,
        viewportHeight: window.innerHeight,
        visibleOpenTriggers: Array.from(document.querySelectorAll('.ui-info-card__menu-trigger.is-open, .experience-action-menu-trigger.is-open')).filter(visible).length,
        visibleTriggers: Array.from(document.querySelectorAll('.ui-info-card__menu-trigger, .experience-action-menu-trigger')).filter(visible).length,
        visibleMenus: Array.from(document.querySelectorAll('.ui-info-card__menu.is-open, .ui-info-card__menu, .activities-event-action-menu.item-action-menu.is-open')).filter(visible).length
      };
    }
    const triggerRect = trigger.rect;
    const cardRect = card.getBoundingClientRect();
    const menuRect = menu.getBoundingClientRect();
    const rectToJson = rect => ({
      left: rect.left,
      top: rect.top,
      right: rect.right,
      bottom: rect.bottom,
      width: rect.width,
      height: rect.height
    });
    const triggerCenterX = triggerRect.left + triggerRect.width / 2;
    const menuCenterX = menuRect.left + menuRect.width / 2;
    const horizontalDrift = Math.abs(menuRect.right - triggerRect.right);
    const opensDown = menuRect.top >= triggerRect.bottom;
    const opensUp = menuRect.bottom <= triggerRect.top;
    const verticalGap = opensDown
      ? menuRect.top - triggerRect.bottom
      : opensUp
        ? triggerRect.top - menuRect.bottom
        : 0;
    return {
      found: true,
      viewportWidth: window.innerWidth,
      viewportHeight: window.innerHeight,
      triggerRect: rectToJson(triggerRect),
      cardRect: rectToJson(cardRect),
      menuRect: rectToJson(menuRect),
      horizontalDrift,
      verticalGap,
      opensUp,
      opensDown,
      menuOverlapsCardHorizontally: menuRect.right >= cardRect.left && menuRect.left <= cardRect.right
    };
  });
}

export async function assertNoDocumentHorizontalOverflow(page, options = {}) {
  const tolerance = Number(options.tolerancePx ?? 4);
  const metrics = await page.evaluate(() => {
    const root = document.documentElement;
    const body = document.body;
    return {
      viewportWidth: window.innerWidth,
      rootClientWidth: root.clientWidth,
      rootScrollWidth: root.scrollWidth,
      bodyClientWidth: body?.clientWidth ?? 0,
      bodyScrollWidth: body?.scrollWidth ?? 0
    };
  });
  const overflow = Math.max(
    metrics.rootScrollWidth - metrics.rootClientWidth,
    metrics.bodyScrollWidth - metrics.bodyClientWidth
  );
  assert(overflow <= tolerance, 'Document should not have unexpected horizontal overflow', {
    ...metrics,
    overflow,
    tolerance
  });
}

export async function smartListSnapshot(page, rootSelector) {
  return page.evaluate(rootSelector => {
    const root = document.querySelector(rootSelector) ?? document;
    const visible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    const list = root.querySelector('.smart-list');
    const items = Array.from(root.querySelectorAll('.smart-list__item-shell, .experience-item-card, .activities-row-item'))
      .filter(visible)
      .map(element => ({
        text: (element.textContent ?? '').trim().replace(/\s+/g, ' ').slice(0, 180),
        group: element.getAttribute('data-group-label') || ''
      }));
    return {
      itemCount: items.length,
      groups: Array.from(root.querySelectorAll('.smart-list__group-marker'))
        .filter(visible)
        .map(element => ({
          label: (element.textContent ?? '').trim(),
          attr: element.getAttribute('data-group-label') || ''
        })),
      stickyLabel: (root.querySelector('.smart-list__sticky, .smart-list__sticky-label')?.textContent ?? '').trim(),
      isCalendar: Boolean(root.querySelector('.smart-list--week, .smart-list--month')),
      scrollTop: list?.scrollTop ?? 0,
      scrollHeight: list?.scrollHeight ?? 0,
      clientHeight: list?.clientHeight ?? 0,
      items
    };
  }, rootSelector);
}

export async function scrollSmartList(page, rootSelector, amount = 900) {
  await page.evaluate(({ rootSelector, amount }) => {
    const root = document.querySelector(rootSelector) ?? document;
    const list = root.querySelector('.smart-list');
    if (list) {
      list.scrollBy({ top: amount, left: 0, behavior: 'instant' });
      list.dispatchEvent(new Event('scroll', { bubbles: true }));
    }
  }, { rootSelector, amount });
}

export async function assertNoVisibleSelector(page, selector, label = selector) {
  const count = await page.visibleCount(selector);
  assert(count === 0, `${label} should not be visible`, { count });
}

export async function assertTextAbsent(page, text) {
  const needle = `${text ?? ''}`;
  const present = await page.evaluate(needle => document.body.innerText.includes(needle), needle);
  assert(!present, `Page should not contain text ${needle}`);
}

export async function assertNoVisibleCrashFallback(page) {
  const crashTexts = await page.evaluate(() => {
    const bodyText = document.body.innerText || '';
    return [
      'Application error',
      'Something went wrong',
      'Cannot read properties',
      'TypeError:',
      'ReferenceError:',
      'Unhandled Promise rejection',
      'NG0'
    ].filter(text => bodyText.includes(text));
  });
  if (crashTexts.length > 0) {
    await page.recordStep('visible-crash-fallback-text');
    page.recordFinding?.({
      severity: 'fatal',
      title: 'Page displayed crash/fallback text',
      details: { crashTexts }
    });
  }
  assert(crashTexts.length === 0, 'Page should not show crash/fallback text', { crashTexts });
}

export async function assertNoBrokenImages(page, options = {}) {
  const ignore = (options.ignore ?? []).map(item => `${item}`);
  let brokenImages = await collectBrokenImages(page, ignore);
  if (brokenImages.length > 0) {
    await waitForBackendUiWindow(page, 'body').catch(() => undefined);
    brokenImages = await collectBrokenImages(page, ignore);
  }
  if (brokenImages.length > 0) {
    await page.recordStep('visible-broken-images');
    page.recordFinding?.({
      severity: 'fatal',
      title: 'Visible images failed to load',
      details: { brokenImages }
    });
  }
  assert(brokenImages.length === 0, 'Visible images should load successfully', { brokenImages });
}

async function collectBrokenImages(page, ignore) {
  return page.evaluate(ignore => {
    const isVisible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    return Array.from(document.querySelectorAll('img'))
      .filter(image => isVisible(image))
      .filter(image => {
        const src = image.currentSrc || image.src || '';
        return src && !ignore.some(pattern => src.includes(pattern));
      })
      .filter(image => image.complete && image.naturalWidth === 0)
      .map(image => ({
        src: image.currentSrc || image.src || '',
        alt: image.alt || '',
        selector: image.className || image.closest('[class]')?.className || image.tagName.toLowerCase()
      }));
  }, ignore);
}

export async function waitForVisibleText(page, selector, text, timeoutMs = 15000) {
  const needle = `${text ?? ''}`;
  await page.waitFor(`${selector} visible text ${needle}`, async () => {
    return page.evaluate(({ selector, needle }) => {
      return Array.from(document.querySelectorAll(selector)).some(element => {
        const rect = element.getBoundingClientRect();
        const style = window.getComputedStyle(element);
        return rect.width > 0
          && rect.height > 0
          && style.visibility !== 'hidden'
          && style.display !== 'none'
          && (element.textContent ?? '').includes(needle);
      });
    }, { selector, needle });
  }, timeoutMs);
}

export async function performDemoLogin(page, options = {}) {
  const timeoutMs = Number(options.timeoutMs ?? 45000);
  if (options.geolocation) {
    await page.setGeolocation(options.geolocation);
  }
  await page.primeOrigin(options.storage ?? {});
  await page.navigate(options.entryRoute ?? '/', { loadTimeoutMs: timeoutMs });
  await page.recordStep('suite-login-entry');
  await page.waitForSelector('app-entry-page', { timeoutMs });
  await acceptEntryPrivacyIfPresent(page, timeoutMs);
  await page.recordStep('suite-login-privacy-accepted');
  await clickEntryPrimaryAction(page, timeoutMs);
  await waitForLoginDialogOrDemoSelector(page, timeoutMs);
  if (await page.isSelectorVisible('.dialog')) {
    await page.recordStep('suite-login-location-dialog');
    await page.grantGeolocationPermission();
    await page.click('.dialog-actions-menu .app-menu__button-row-item:last-of-type', { timeoutMs });
  }
  await page.waitForSelector('.demo-user-stage', { timeoutMs });
  await page.waitForSelector('.demo-user-item', { timeoutMs });
  await page.recordStep('suite-login-demo-selector');
  await clickDemoUser(page, options.userId ?? '', timeoutMs);
  await page.waitForUrlPath('/game', timeoutMs);
  await settleFirstRunOverlays(page, { timeoutMs });
  await page.recordStep('suite-login-game-ready');
  return page.storageSnapshot();
}

export async function settleFirstRunOverlays(page, options = {}) {
  const timeoutMs = Number(options.timeoutMs ?? 15000);
  await approveNavigatorPrivacyIfPresent(page, timeoutMs);
  await closeExplanationPopupIfPresent(page, timeoutMs);
  await dismissInstallPromptIfPresent(page, timeoutMs);
}

export async function acceptEntryPrivacyIfPresent(page, timeoutMs = 15000) {
  const popupSelector = 'app-entry-page app-document-viewer .ui-popup__panel';
  const acceptSelector = 'app-entry-page app-document-viewer button[aria-label="Accept and continue"]';
  if (!(await page.isSelectorVisible(popupSelector))) {
    await page.waitForSelector(popupSelector, {
      timeoutMs: Math.min(timeoutMs, 5000)
    }).catch(() => undefined);
  }
  if (!(await page.isSelectorVisible(popupSelector))) {
    return false;
  }
  await page.waitFor('entry privacy accept enabled', async () => page.evaluate(acceptSelector => {
    const button = document.querySelector(acceptSelector);
    return Boolean(button && !button.disabled);
  }, acceptSelector), timeoutMs);
  await page.click(acceptSelector, { timeoutMs });
  await page.waitForSelectorHidden(popupSelector, { timeoutMs });
  await page.recordStep('entry-privacy-accepted');
  return true;
}

export async function approveNavigatorPrivacyIfPresent(page, timeoutMs = 15000) {
  if (!(await page.isSelectorVisible('app-navigator-privacy-popup'))) {
    return false;
  }
  await page.waitFor('navigator privacy save enabled', async () => page.evaluate(() => {
    const button = document.querySelector('.support-privacy-save-btn');
    return Boolean(button && !button.disabled);
  }), timeoutMs);
  await page.click('.support-privacy-save-btn', { timeoutMs });
  await page.waitForSelectorHidden('app-navigator-privacy-popup', { timeoutMs });
  await page.recordStep('navigator-privacy-approved');
  return true;
}

export async function closeExplanationPopupIfPresent(page, timeoutMs = 15000) {
  const popupSelector = 'app-explanation-popup .ui-popup__panel';
  if (!(await page.isSelectorVisible(popupSelector))) {
    return false;
  }
  const clicked = await page.evaluate(popupSelector => {
    const normalize = value => `${value ?? ''}`
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim()
      .toLowerCase();
    const visible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    const popup = document.querySelector(popupSelector);
    const buttons = Array.from(popup?.querySelectorAll('button') ?? [])
      .filter(button => visible(button) && !button.disabled);
    const action = buttons.find(button => {
      const text = normalize(button.textContent);
      return text.includes('got it') || text.includes('ertem');
    }) ?? popup?.querySelector('button[aria-label="got.it"], button[aria-label="Got it"]');
    if (!action || action.disabled) {
      return false;
    }
    action.scrollIntoView({ block: 'center', inline: 'center' });
    action.click();
    return true;
  }, popupSelector);
  if (!clicked) {
    await page.clickVisible(`${popupSelector} button[aria-label="got.it"]`, { timeoutMs });
  }
  await page.waitForSelectorHidden(popupSelector, { timeoutMs });
  await page.recordStep('explanation-popup-closed');
  return true;
}

export async function closeExplanationPopupEventually(page, timeoutMs = 8000) {
  const popupSelector = 'app-explanation-popup .ui-popup__panel';
  if (!(await page.isSelectorVisible(popupSelector))) {
    await page.waitForSelector(popupSelector, {
      timeoutMs: Math.min(Number(timeoutMs) || 8000, 8000)
    }).catch(() => undefined);
  }
  return closeExplanationPopupIfPresent(page, timeoutMs);
}

export async function dismissInstallPromptIfPresent(page, timeoutMs = 15000) {
  if (!(await page.isSelectorVisible('.app-install-prompt'))) {
    return false;
  }
  const clicked = await page.evaluate(() => {
    const visible = element => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0
        && rect.height > 0
        && style.visibility !== 'hidden'
        && style.display !== 'none';
    };
    const buttons = Array.from(document.querySelectorAll('.app-install-prompt button'))
      .filter(button => visible(button) && !button.disabled);
    const later = buttons.find(button => button.classList.contains('app-install-prompt__secondary'))
      ?? buttons.find(button => (button.getAttribute('aria-label') ?? '').trim().toLowerCase().includes('later'))
      ?? buttons.find(button => (button.textContent ?? '').trim().toLowerCase().includes('later'));
    if (!later) {
      return false;
    }
    later.scrollIntoView({ block: 'center', inline: 'center' });
    later.click();
    return true;
  });
  assert(clicked, 'Install prompt should expose a dismiss/later button');
  await page.waitForSelectorHidden('.app-install-prompt', { timeoutMs });
  await page.recordStep('install-prompt-dismissed');
  return true;
}

export async function clickDemoUser(page, userId, timeoutMs) {
  const clicked = await page.evaluate(({ userId }) => {
    const buttons = Array.from(document.querySelectorAll('.demo-user-item'))
      .filter(button => !button.disabled);
    const normalizedUserId = `${userId ?? ''}`.trim();
    const matchById = normalizedUserId
      ? buttons.find(button => button.getAttribute('data-user-id') === normalizedUserId)
      : null;
    const preferred = matchById
      ?? buttons.find(button => !button.classList.contains('demo-user-item-new') && !button.classList.contains('demo-user-item-blocked'))
      ?? buttons[0];
    if (!preferred) {
      return false;
    }
    preferred.scrollIntoView({ block: 'center', inline: 'center' });
    preferred.click();
    return true;
  }, { userId });
  assert(clicked, 'Should select a demo user from the demo selector');
  await page.waitFor('demo selector closes after user selection', async () => {
    return !(await page.isSelectorVisible('.demo-user-stage'));
  }, timeoutMs);
}

export async function waitForLoginDialogOrDemoSelector(page, timeoutMs) {
  await page.waitFor('location dialog or demo selector', async () => {
    return (await page.isSelectorVisible('.dialog'))
      || (await page.isSelectorVisible('.demo-user-stage'));
  }, timeoutMs);
}
