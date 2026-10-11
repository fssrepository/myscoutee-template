import { assert } from './assertions.mjs';

export async function connectWebSocket(url, options = {}) {
  assert(typeof WebSocket === 'function', 'Node runtime should provide WebSocket support');
  const timeoutMs = Number(options.timeoutMs ?? 10000);
  const messages = [];
  const ws = new WebSocket(url, options.protocols);
  const started = performance.now();
  let openedAt = 0;
  let closed = null;
  let error = null;

  ws.addEventListener('message', event => {
    messages.push(typeof event.data === 'string' ? event.data : Buffer.from(event.data).toString('utf8'));
  });
  ws.addEventListener('close', event => {
    closed = {
      code: event.code,
      reason: event.reason,
      wasClean: event.wasClean
    };
  });
  ws.addEventListener('error', event => {
    error = event?.message || 'WebSocket error';
  });

  await waitForOpen(ws, timeoutMs);
  openedAt = performance.now();

  for (const payload of options.send ?? []) {
    ws.send(typeof payload === 'string' ? payload : JSON.stringify(payload));
  }

  if (options.waitForMessage) {
    await waitFor(() => messages.length > 0, timeoutMs, 'WebSocket message');
  } else {
    await delay(Number(options.holdOpenMs ?? 250));
  }

  ws.close(1000, 'e2e complete');
  await waitFor(() => ws.readyState === WebSocket.CLOSED || closed !== null, Math.min(timeoutMs, 3000), 'WebSocket close')
    .catch(() => undefined);

  return {
    status: openedAt > 0 ? 101 : 0,
    durationMs: performance.now() - started,
    openMs: openedAt > 0 ? openedAt - started : 0,
    url,
    messages,
    closed,
    error
  };
}

export function toWebSocketUrl(baseUrl, path, params = {}) {
  const url = new URL(path, baseUrl);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value !== null && value !== undefined && `${value}`.length > 0) {
      url.searchParams.set(key, `${value}`);
    }
  }
  return url.toString();
}

function waitForOpen(ws, timeoutMs) {
  if (ws.readyState === WebSocket.OPEN) {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out opening WebSocket after ${timeoutMs}ms`));
    }, timeoutMs);
    const onOpen = () => {
      cleanup();
      resolve();
    };
    const onError = () => {
      cleanup();
      reject(new Error('WebSocket failed before opening'));
    };
    const onClose = event => {
      cleanup();
      reject(new Error(`WebSocket closed before opening: ${event.code} ${event.reason}`.trim()));
    };
    const cleanup = () => {
      clearTimeout(timeout);
      ws.removeEventListener('open', onOpen);
      ws.removeEventListener('error', onError);
      ws.removeEventListener('close', onClose);
    };
    ws.addEventListener('open', onOpen);
    ws.addEventListener('error', onError);
    ws.addEventListener('close', onClose);
  });
}

async function waitFor(predicate, timeoutMs, description) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (predicate()) {
      return true;
    }
    await delay(50);
  }
  throw new Error(`Timed out waiting for ${description}`);
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, Math.max(0, Number(ms) || 0)));
}
