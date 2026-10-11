import { spawn } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';

import { assert } from '../../../shared/assertions.mjs';

const DEFAULT_CHROME_CANDIDATES = [
  '/usr/bin/google-chrome',
  '/usr/bin/google-chrome-stable',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser'
];

export async function launchChromeBrowser(options = {}) {
  const chromePath = options.chromePath || process.env.E2E_CHROME_BIN || await findChromeExecutable();
  const remotePort = options.remotePort || await getOpenPort();
  const userDataDir = options.userDataDir || await mkdtemp(path.join(os.tmpdir(), 'myscoutee-ui-e2e-'));
  const viewport = options.viewport ?? { width: 1440, height: 950 };
  const args = [
    `--remote-debugging-port=${remotePort}`,
    `--user-data-dir=${userDataDir}`,
    '--headless=new',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--disable-dev-shm-usage',
    '--disable-gpu',
    '--disable-extensions',
    '--disable-features=Translate,MediaRouter',
    '--allow-insecure-localhost',
    '--hide-scrollbars',
    '--mute-audio',
    '--no-sandbox',
    `--window-size=${viewport.width},${viewport.height}`,
    'about:blank'
  ];
  const child = spawn(chromePath, args, {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env }
  });
  child.stdout?.resume();
  child.stderr?.resume();
  const browser = new ChromeBrowser({ child, remotePort, userDataDir, baseUrl: options.baseUrl, defaultViewport: viewport });
  try {
    await browser.waitUntilReady();
    return browser;
  } catch (error) {
    await browser.close().catch(() => undefined);
    throw error;
  }
}

export class ChromeBrowser {
  constructor({ child, remotePort, userDataDir, baseUrl, defaultViewport }) {
    this.child = child;
    this.remotePort = remotePort;
    this.userDataDir = userDataDir;
    this.baseUrl = (baseUrl ?? 'https://127.0.0.1').replace(/\/+$/, '');
    this.defaultViewport = defaultViewport ?? { width: 1440, height: 950 };
  }

  async waitUntilReady(timeoutMs = 12000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (this.child.exitCode !== null) {
        throw new Error(`Chrome exited before DevTools was ready (code ${this.child.exitCode})`);
      }
      try {
        const response = await fetch(`http://127.0.0.1:${this.remotePort}/json/version`);
        if (response.ok) {
          return;
        }
      } catch {
        // Chrome needs a moment to open the DevTools endpoint.
      }
      await delay(100);
    }
    throw new Error(`Chrome DevTools endpoint was not ready on port ${this.remotePort}`);
  }

  async newPage(options = {}) {
    const response = await fetch(`http://127.0.0.1:${this.remotePort}/json/new?about:blank`, {
      method: 'PUT'
    });
    assert(response.ok, 'Chrome should create a new DevTools page', { status: response.status });
    const target = await response.json();
    const connection = await CdpConnection.connect(target.webSocketDebuggerUrl);
    const page = new ChromePage({
      browser: this,
      connection,
      targetId: target.id,
      viewport: options.viewport ?? this.defaultViewport
    });
    await page.init();
    return page;
  }

  async closePage(targetId) {
    if (!targetId) {
      return;
    }
    try {
      await fetch(`http://127.0.0.1:${this.remotePort}/json/close/${encodeURIComponent(targetId)}`);
    } catch {
      // Browser shutdown will clean up pages that fail to close explicitly.
    }
  }

  async close() {
    if (this.child.exitCode === null) {
      this.child.kill('SIGTERM');
      await Promise.race([
        new Promise(resolve => this.child.once('exit', resolve)),
        delay(2000).then(() => {
          if (this.child.exitCode === null) {
            this.child.kill('SIGKILL');
          }
        })
      ]);
    }
    if (this.userDataDir) {
      await removeUserDataDir(this.userDataDir);
    }
  }
}

class CdpConnection {
  static async connect(url) {
    assert(typeof WebSocket === 'function', 'Node runtime should provide WebSocket for Chrome DevTools');
    const socket = new WebSocket(url);
    const connection = new CdpConnection(socket);
    await connection.waitForOpen();
    return connection;
  }

  constructor(socket) {
    this.socket = socket;
    this.nextId = 1;
    this.pending = new Map();
    this.handlers = new Map();
    socket.addEventListener('message', event => this.onMessage(event.data));
    socket.addEventListener('close', () => {
      for (const { reject } of this.pending.values()) {
        reject(new Error('Chrome DevTools socket closed'));
      }
      this.pending.clear();
    });
  }

  waitForOpen(timeoutMs = 6000) {
    if (this.socket.readyState === WebSocket.OPEN) {
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Chrome DevTools socket did not open')), timeoutMs);
      this.socket.addEventListener('open', () => {
        clearTimeout(timeout);
        resolve();
      }, { once: true });
      this.socket.addEventListener('error', () => {
        clearTimeout(timeout);
        reject(new Error('Chrome DevTools socket failed to open'));
      }, { once: true });
    });
  }

  send(method, params = {}) {
    const id = this.nextId;
    this.nextId += 1;
    const payload = JSON.stringify({ id, method, params });
    const promise = new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject, method });
    });
    this.socket.send(payload);
    return promise;
  }

  on(eventName, handler) {
    const handlers = this.handlers.get(eventName) ?? [];
    handlers.push(handler);
    this.handlers.set(eventName, handlers);
    return () => {
      const next = (this.handlers.get(eventName) ?? []).filter(item => item !== handler);
      this.handlers.set(eventName, next);
    };
  }

  async close() {
    try {
      this.socket.close();
    } catch {
      // The browser may already have closed the socket.
    }
  }

  onMessage(rawData) {
    const text = typeof rawData === 'string' ? rawData : Buffer.from(rawData).toString('utf8');
    const message = JSON.parse(text);
    if (message.id) {
      const pending = this.pending.get(message.id);
      if (!pending) {
        return;
      }
      this.pending.delete(message.id);
      if (message.error) {
        pending.reject(new Error(`${pending.method} failed: ${message.error.message}`));
      } else {
        pending.resolve(message.result ?? {});
      }
      return;
    }
    const handlers = this.handlers.get(message.method) ?? [];
    for (const handler of handlers) {
      handler(message.params ?? {});
    }
  }
}

export class ChromePage {
  constructor({ browser, connection, targetId, viewport }) {
    this.browser = browser;
    this.connection = connection;
    this.targetId = targetId;
    this.viewport = viewport;
    this.network = [];
    this.failedNetwork = [];
    this.requests = new Map();
    this.consoleMessages = [];
    this.pageErrors = [];
    this.uiFindings = [];
  }

  async init() {
    await Promise.all([
      this.connection.send('Page.enable'),
      this.connection.send('Runtime.enable'),
      this.connection.send('Network.enable'),
      this.connection.send('Log.enable'),
      this.connection.send('Performance.enable')
    ]);
    await this.setViewport(this.viewport);
    this.connection.on('Network.requestWillBeSent', params => {
      this.requests.set(params.requestId, {
        url: params.request?.url ?? '',
        method: params.request?.method ?? ''
      });
    });
    this.connection.on('Network.responseReceived', params => {
      this.network.push({
        requestId: params.requestId,
        url: params.response?.url ?? '',
        status: params.response?.status ?? 0,
        type: params.type ?? '',
        mimeType: params.response?.mimeType ?? '',
        fromDiskCache: params.response?.fromDiskCache === true,
        encodedDataLength: null
      });
    });
    this.connection.on('Network.loadingFinished', params => {
      const entry = this.network.find(item => item.requestId === params.requestId);
      if (entry) {
        entry.encodedDataLength = params.encodedDataLength ?? null;
      }
    });
    this.connection.on('Network.loadingFailed', params => {
      const request = this.requests.get(params.requestId) ?? {};
      this.failedNetwork.push({
        requestId: params.requestId,
        url: request.url ?? '',
        method: request.method ?? '',
        type: params.type,
        errorText: params.errorText,
        blockedReason: params.blockedReason
      });
    });
    this.connection.on('Runtime.consoleAPICalled', params => {
      this.consoleMessages.push({
        type: params.type,
        text: (params.args ?? []).map(arg => arg.value ?? arg.description ?? '').join(' ')
      });
    });
    this.connection.on('Runtime.exceptionThrown', params => {
      this.pageErrors.push(params.exceptionDetails?.text ?? params.exceptionDetails?.exception?.description ?? 'Runtime exception');
    });
  }

  async setViewport(viewport) {
    this.viewport = {
      width: Number(viewport?.width ?? 1440),
      height: Number(viewport?.height ?? 950)
    };
    await this.connection.send('Emulation.setDeviceMetricsOverride', {
      width: this.viewport.width,
      height: this.viewport.height,
      deviceScaleFactor: 1,
      mobile: this.viewport.width <= 760
    });
    await this.connection.send('Emulation.setTouchEmulationEnabled', {
      enabled: this.viewport.width <= 760,
      maxTouchPoints: this.viewport.width <= 760 ? 5 : 0
    }).catch(() => undefined);
  }

  async setCpuThrottlingRate(rate = 1) {
    const normalized = Math.max(1, Number(rate) || 1);
    await this.connection.send('Emulation.setCPUThrottlingRate', { rate: normalized });
  }

  async emulateNetworkConditions(options = {}) {
    const hasThrottle = options.offline === true
      || Number(options.latencyMs ?? 0) > 0
      || Number(options.downloadKbps ?? 0) > 0
      || Number(options.uploadKbps ?? 0) > 0;
    if (!hasThrottle) {
      await this.connection.send('Network.emulateNetworkConditions', {
        offline: false,
        latency: 0,
        downloadThroughput: -1,
        uploadThroughput: -1
      });
      return;
    }
    await this.connection.send('Network.emulateNetworkConditions', {
      offline: options.offline === true,
      latency: Math.max(0, Number(options.latencyMs ?? 0) || 0),
      downloadThroughput: throughputBytesPerSecond(options.downloadKbps),
      uploadThroughput: throughputBytesPerSecond(options.uploadKbps),
      connectionType: options.connectionType ?? 'cellular4g'
    });
  }

  async setCacheDisabled(disabled = true) {
    await this.connection.send('Network.setCacheDisabled', { cacheDisabled: disabled === true });
  }

  async setExtraHttpHeaders(headers = {}) {
    await this.connection.send('Network.setExtraHTTPHeaders', { headers });
  }

  async clearBrowserCache() {
    await this.connection.send('Network.clearBrowserCache').catch(() => undefined);
  }

  async addInitScript(source) {
    await this.connection.send('Page.addScriptToEvaluateOnNewDocument', { source: `${source ?? ''}` });
  }

  async performanceMetrics() {
    const result = await this.connection.send('Performance.getMetrics').catch(() => ({ metrics: [] }));
    return Object.fromEntries((result.metrics ?? []).map(item => [item.name, item.value]));
  }

  async setGeolocation(coordinates = {}) {
    const latitude = Number(coordinates.latitude);
    const longitude = Number(coordinates.longitude);
    const accuracy = Number(coordinates.accuracy ?? 20);
    assert(Number.isFinite(latitude) && Number.isFinite(longitude), 'Geolocation override requires finite latitude and longitude', {
      coordinates
    });
    if (coordinates.grant !== false) {
      await this.grantGeolocationPermission();
    }
    await this.connection.send('Emulation.setGeolocationOverride', {
      latitude,
      longitude,
      accuracy: Number.isFinite(accuracy) ? Math.max(1, accuracy) : 20
    });
  }

  async grantGeolocationPermission() {
    const origin = new URL(this.browser.baseUrl).origin;
    await this.connection.send('Browser.grantPermissions', {
      origin,
      permissions: ['geolocation']
    }).catch(() => undefined);
  }

  async setGeolocationPermission(setting) {
    const normalized = `${setting ?? ''}`.trim();
    assert(['granted', 'denied', 'prompt'].includes(normalized), 'Geolocation permission setting must be granted, denied, or prompt', {
      setting
    });
    const origin = new URL(this.browser.baseUrl).origin;
    await this.connection.send('Browser.setPermission', {
      origin,
      permission: { name: 'geolocation' },
      setting: normalized
    });
  }

  async resetPermissions() {
    await this.connection.send('Browser.resetPermissions').catch(() => undefined);
  }

  async primeOrigin({ localStorage = {}, sessionStorage = {} } = {}) {
    await this.navigate('/', { waitForLoad: true });
    await this.evaluate(({ localStorageEntries, sessionStorageEntries }) => {
      window.localStorage.clear();
      window.sessionStorage.clear();
      for (const [key, value] of Object.entries(localStorageEntries)) {
        window.localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value));
      }
      for (const [key, value] of Object.entries(sessionStorageEntries)) {
        window.sessionStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value));
      }
    }, { localStorageEntries: localStorage, sessionStorageEntries: sessionStorage });
    this.clearNetworkLog();
  }

  async navigate(route, options = {}) {
    const target = absoluteUrl(this.browser.baseUrl, route);
    const loadPromise = this.waitForEvent('Page.loadEventFired', options.loadTimeoutMs ?? 15000);
    await this.connection.send('Page.navigate', { url: target });
    if (options.waitForLoad !== false) {
      await loadPromise.catch(() => undefined);
    }
    return target;
  }

  async waitForEvent(eventName, timeoutMs = 10000) {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        unsubscribe();
        reject(new Error(`Timed out waiting for ${eventName}`));
      }, timeoutMs);
      const unsubscribe = this.connection.on(eventName, params => {
        clearTimeout(timeout);
        unsubscribe();
        resolve(params);
      });
    });
  }

  async evaluate(fnOrExpression, arg = undefined) {
    const expression = typeof fnOrExpression === 'function'
      ? `(${fnOrExpression})(${JSON.stringify(arg)})`
      : `${fnOrExpression}`;
    const result = await this.connection.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
      userGesture: true
    });
    if (result.exceptionDetails) {
      throw new Error(result.exceptionDetails.exception?.description ?? result.exceptionDetails.text ?? 'Runtime.evaluate failed');
    }
    return result.result?.value;
  }

  async waitForSelector(selector, options = {}) {
    const timeoutMs = Number(options.timeoutMs ?? 15000);
    const visible = options.visible !== false;
    return this.waitFor(`selector ${selector}`, async () => {
      return this.evaluate(({ selector, visible }) => {
        const element = document.querySelector(selector);
        if (!element) {
          return false;
        }
        if (!visible) {
          return true;
        }
        const rect = element.getBoundingClientRect();
        const style = window.getComputedStyle(element);
        return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
      }, { selector, visible });
    }, timeoutMs);
  }

  async waitForSelectorHidden(selector, options = {}) {
    const timeoutMs = Number(options.timeoutMs ?? 15000);
    return this.waitFor(`selector ${selector} hidden`, async () => {
      return !(await this.isSelectorVisible(selector));
    }, timeoutMs);
  }

  async isSelectorVisible(selector) {
    return this.evaluate(selector => {
      const element = document.querySelector(selector);
      if (!element) {
        return false;
      }
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
    }, selector);
  }

  async visibleCount(selector) {
    return this.evaluate(selector => {
      return Array.from(document.querySelectorAll(selector)).filter(element => {
        const rect = element.getBoundingClientRect();
        const style = window.getComputedStyle(element);
        return rect.width > 0
          && rect.height > 0
          && style.visibility !== 'hidden'
          && style.display !== 'none';
      }).length;
    }, selector);
  }

  async textContent(selector, options = {}) {
    await this.waitForSelector(selector, { timeoutMs: options.timeoutMs ?? 15000 });
    return this.evaluate(selector => document.querySelector(selector)?.textContent ?? '', selector);
  }

  async fill(selector, value, options = {}) {
    await this.waitForSelector(selector, { timeoutMs: options.timeoutMs ?? 15000 });
    const filled = await this.evaluate(({ selector, value }) => {
      const element = document.querySelector(selector);
      if (!element || !('value' in element)) {
        return false;
      }
      element.scrollIntoView({ block: 'center', inline: 'center' });
      element.focus();
      element.value = `${value ?? ''}`;
      element.dispatchEvent(new Event('input', { bubbles: true }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
      element.blur();
      return true;
    }, { selector, value });
    assert(filled, `Should fill ${selector}`);
  }

  async fillVisible(selector, value, options = {}) {
    const timeoutMs = Number(options.timeoutMs ?? 15000);
    await this.waitFor(`${selector} visible input`, async () => {
      return this.visibleCount(selector).then(count => count > 0);
    }, timeoutMs);
    const filled = await this.evaluate(({ selector, value }) => {
      const isVisible = element => {
        const rect = element.getBoundingClientRect();
        const style = window.getComputedStyle(element);
        return rect.width > 0
          && rect.height > 0
          && style.visibility !== 'hidden'
          && style.display !== 'none';
      };
      const element = Array.from(document.querySelectorAll(selector))
        .find(item => isVisible(item) && 'value' in item);
      if (!element) {
        return false;
      }
      element.scrollIntoView({ block: 'center', inline: 'center' });
      element.focus();
      element.value = `${value ?? ''}`;
      element.dispatchEvent(new Event('input', { bubbles: true }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
      element.blur();
      return true;
    }, { selector, value });
    assert(filled, `Should fill visible ${selector}`);
  }

  async clickVisible(selector, options = {}) {
    const timeoutMs = Number(options.timeoutMs ?? 15000);
    await this.waitFor(`${selector} visible`, async () => {
      return this.visibleCount(selector).then(count => count > 0);
    }, timeoutMs);
    const clicked = await this.evaluate(selector => {
      const isVisible = element => {
        const rect = element.getBoundingClientRect();
        const style = window.getComputedStyle(element);
        return rect.width > 0
          && rect.height > 0
          && style.visibility !== 'hidden'
          && style.display !== 'none';
      };
      const element = Array.from(document.querySelectorAll(selector))
        .find(item => isVisible(item) && !item.disabled);
      if (!element) {
        return false;
      }
      element.scrollIntoView({ block: 'center', inline: 'center' });
      element.click();
      return true;
    }, selector);
    assert(clicked, `Should click visible ${selector}`);
  }

  async mouseClickVisible(selector, options = {}) {
    const timeoutMs = Number(options.timeoutMs ?? 15000);
    await this.waitFor(`${selector} visible mouse target`, async () => {
      return this.visibleCount(selector).then(count => count > 0);
    }, timeoutMs);
    const point = await this.evaluate(selector => {
      const isVisible = element => {
        const rect = element.getBoundingClientRect();
        const style = window.getComputedStyle(element);
        return rect.width > 0
          && rect.height > 0
          && style.visibility !== 'hidden'
          && style.display !== 'none';
      };
      const element = Array.from(document.querySelectorAll(selector))
        .find(item => isVisible(item) && !item.disabled);
      if (!element) {
        return null;
      }
      element.scrollIntoView({ block: 'center', inline: 'center' });
      const rect = element.getBoundingClientRect();
      return {
        x: rect.left + rect.width / 2,
        y: rect.top + rect.height / 2
      };
    }, selector);
    assert(point, `Should find visible mouse target ${selector}`);
    await this.connection.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: point.x,
      y: point.y,
      button: 'none'
    });
    await this.connection.send('Input.dispatchMouseEvent', {
      type: 'mousePressed',
      x: point.x,
      y: point.y,
      button: 'left',
      buttons: 1,
      clickCount: 1
    });
    await this.connection.send('Input.dispatchMouseEvent', {
      type: 'mouseReleased',
      x: point.x,
      y: point.y,
      button: 'left',
      buttons: 0,
      clickCount: 1
    });
  }

  async waitForText(text, options = {}) {
    const timeoutMs = Number(options.timeoutMs ?? 15000);
    const needle = `${text}`;
    return this.waitFor(`text ${needle}`, async () => {
      return this.evaluate(needle => document.body.innerText.includes(needle), needle);
    }, timeoutMs);
  }

  async waitForUrlPath(pathname, timeoutMs = 15000) {
    return this.waitFor(`url path ${pathname}`, async () => {
      return this.evaluate(expectedPath => window.location.pathname === expectedPath, pathname);
    }, timeoutMs);
  }

  async waitForNetwork(pattern, options = {}) {
    const timeoutMs = Number(options.timeoutMs ?? 15000);
    return this.waitFor(`network ${pattern}`, async () => this.hasNetwork(pattern, options), timeoutMs);
  }

  hasNetwork(pattern, options = {}) {
    const minStatus = Number(options.minStatus ?? 200);
    const maxStatus = Number(options.maxStatus ?? 399);
    return this.network.some(entry => {
      return matchesPattern(entry.url, pattern) && entry.status >= minStatus && entry.status <= maxStatus;
    });
  }

  async click(selector, options = {}) {
    await this.waitForSelector(selector, { timeoutMs: options.timeoutMs ?? 15000 });
    const clicked = await this.evaluate(selector => {
      const element = document.querySelector(selector);
      if (!element) {
        return false;
      }
      element.scrollIntoView({ block: 'center', inline: 'center' });
      if ('disabled' in element && element.disabled) {
        return false;
      }
      element.click();
      return true;
    }, selector);
    assert(clicked, `Should click ${selector}`);
  }

  async setFileInputFiles(selector, filePaths, options = {}) {
    const files = Array.isArray(filePaths) ? filePaths : [filePaths];
    const index = Math.max(0, Math.trunc(Number(options.index ?? 0) || 0));
    await this.waitForSelector(selector, {
      visible: false,
      timeoutMs: options.timeoutMs ?? 15000
    });
    const expression = `(() => Array.from(document.querySelectorAll(${JSON.stringify(selector)}))[${index}] ?? null)()`;
    const result = await this.connection.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: false,
      userGesture: true
    });
    if (result.exceptionDetails) {
      throw new Error(result.exceptionDetails.exception?.description ?? result.exceptionDetails.text ?? 'Runtime.evaluate failed');
    }
    const objectId = result.result?.objectId;
    assert(objectId, `Should resolve file input ${selector} at index ${index}`);
    try {
      await this.connection.send('DOM.setFileInputFiles', {
        objectId,
        files
      });
    } finally {
      await this.connection.send('Runtime.releaseObject', { objectId }).catch(() => undefined);
    }
    if (options.dispatchChange !== false) {
      const changed = await this.evaluate(({ selector, index }) => {
        const input = Array.from(document.querySelectorAll(selector))[index];
        if (!(input instanceof HTMLInputElement)) {
          return false;
        }
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      }, { selector, index });
      assert(changed, `Should dispatch change for file input ${selector} at index ${index}`);
    }
  }

  async clickByText(selector, text, options = {}) {
    const timeoutMs = Number(options.timeoutMs ?? 15000);
    await this.waitFor(`${selector} text ${text}`, async () => this.evaluate(({ selector, text }) => {
      return Array.from(document.querySelectorAll(selector)).some(element => element.textContent?.includes(text));
    }, { selector, text }), timeoutMs);
    const clicked = await this.evaluate(({ selector, text }) => {
      const element = Array.from(document.querySelectorAll(selector)).find(item => item.textContent?.includes(text));
      if (!element) {
        return false;
      }
      element.scrollIntoView({ block: 'center', inline: 'center' });
      element.click();
      return true;
    }, { selector, text });
    assert(clicked, `Should click ${selector} with text ${text}`);
  }

  async waitFor(description, predicate, timeoutMs = 15000, intervalMs = 100) {
    const deadline = Date.now() + timeoutMs;
    let lastError;
    while (Date.now() < deadline) {
      try {
        if (await predicate()) {
          return true;
        }
      } catch (error) {
        lastError = error;
      }
      await delay(intervalMs);
    }
    if (lastError) {
      throw lastError;
    }
    throw new Error(`Timed out waiting for ${description}`);
  }

  async screenshot() {
    const result = await this.connection.send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: true
    });
    return Buffer.from(result.data, 'base64');
  }

  async recordStep(name) {
    if (!this.stepRecorder) {
      return null;
    }
    return this.stepRecorder(name, this);
  }

  recordFinding(finding) {
    const lastStep = Array.isArray(this.recordedSteps) && this.recordedSteps.length > 0
      ? this.recordedSteps[this.recordedSteps.length - 1]
      : null;
    this.uiFindings.push({
      severity: finding?.severity ?? 'info',
      title: finding?.title ?? 'UI finding',
      details: finding?.details ?? null,
      screenshot: finding?.screenshot ?? lastStep?.path ?? null
    });
  }

  clearNetworkLog() {
    this.network = [];
    this.failedNetwork = [];
    this.requests = new Map();
    this.consoleMessages = [];
    this.pageErrors = [];
  }

  async storageSnapshot() {
    return this.evaluate(() => ({
      localStorage: Object.fromEntries(
        Array.from({ length: window.localStorage.length }, (_value, index) => {
          const key = window.localStorage.key(index);
          return key ? [key, window.localStorage.getItem(key)] : null;
        }).filter(Boolean)
      ),
      sessionStorage: Object.fromEntries(
        Array.from({ length: window.sessionStorage.length }, (_value, index) => {
          const key = window.sessionStorage.key(index);
          return key ? [key, window.sessionStorage.getItem(key)] : null;
        }).filter(Boolean)
      )
    }));
  }

  async close() {
    await this.connection.close();
    await this.browser.closePage(this.targetId);
  }
}

export function demoSessionStorage(userId = '9f3b3f085e23af6c89dbc432') {
  return {
    'myscoutee.http.session.v1': JSON.stringify({ kind: 'demo', userId }),
    'myscoutee.http.demo.active-user.v1': userId
  };
}

export function adminSessionStorage(adminUserId = 'myscoutee@gmail.com') {
  return {
    ...demoSessionStorage(adminUserId),
    'myscoutee-admin-session': JSON.stringify({ adminUserId })
  };
}

function absoluteUrl(baseUrl, route) {
  if (/^https?:\/\//i.test(route)) {
    return route;
  }
  return `${baseUrl}${route.startsWith('/') ? route : `/${route}`}`;
}

function matchesPattern(value, pattern) {
  if (pattern instanceof RegExp) {
    return pattern.test(value);
  }
  const text = `${pattern ?? ''}`;
  if (text.startsWith('/') && text.endsWith('/') && text.length > 2) {
    return new RegExp(text.slice(1, -1)).test(value);
  }
  return value.includes(text);
}

function throughputBytesPerSecond(kbps) {
  const value = Number(kbps);
  return Number.isFinite(value) && value > 0 ? Math.round((value * 1000) / 8) : -1;
}

async function findChromeExecutable() {
  const { access } = await import('node:fs/promises');
  for (const candidate of DEFAULT_CHROME_CANDIDATES) {
    try {
      await access(candidate);
      return candidate;
    } catch {
      // Try the next well-known browser path.
    }
  }
  throw new Error(`Unable to find Chrome. Set E2E_CHROME_BIN or install one of: ${DEFAULT_CHROME_CANDIDATES.join(', ')}`);
}

function getOpenPort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : 0;
      server.close(() => resolve(port));
    });
  });
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function removeUserDataDir(userDataDir) {
  let lastError;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    try {
      await rm(userDataDir, { recursive: true, force: true });
      return;
    } catch (error) {
      lastError = error;
      if (error.code === 'ENOENT') {
        return;
      }
      await delay(200 * (attempt + 1));
    }
  }
  if (lastError?.code !== 'ENOENT') {
    throw lastError;
  }
}
