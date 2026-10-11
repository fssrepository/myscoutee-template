export class HttpRequestError extends Error {
  constructor(message, result) {
    super(message);
    this.name = 'HttpRequestError';
    this.result = result;
  }
}

export function createHttpClient(options = {}) {
  const baseUrl = normalizeBaseUrl(options.baseUrl ?? 'https://127.0.0.1');
  const defaultHeaders = options.defaultHeaders ?? {};
  const defaultTimeoutMs = Number(options.timeoutMs ?? 15000);

  async function request(method, path, requestOptions = {}) {
    const url = buildUrl(baseUrl, path, requestOptions.params);
    const headers = {
      Accept: 'application/json, text/plain;q=0.9, */*;q=0.8',
      ...defaultHeaders,
      ...(await options.headersForRequest?.(method, url, requestOptions) ?? {}),
      ...(requestOptions.headers ?? {})
    };
    const init = {
      method: method.toUpperCase(),
      headers
    };
    if (requestOptions.json !== undefined) {
      init.body = JSON.stringify(requestOptions.json);
      init.headers = {
        'Content-Type': 'application/json',
        ...headers
      };
    } else if (requestOptions.body !== undefined) {
      init.body = requestOptions.body;
    }

    const timeoutMs = Number(requestOptions.timeoutMs ?? defaultTimeoutMs);
    const controller = new AbortController();
    const timeout = Number.isFinite(timeoutMs) && timeoutMs > 0
      ? setTimeout(() => controller.abort(), timeoutMs)
      : null;
    init.signal = controller.signal;

    const startedAt = performance.now();
    let response;
    let text = '';
    let data = null;
    try {
      response = await fetch(url, init);
      text = await response.text();
      data = parseBody(text, response.headers.get('content-type'));
    } catch (error) {
      const durationMs = performance.now() - startedAt;
      const result = {
        method: init.method,
        url,
        status: 0,
        headers: {},
        text: '',
        data: null,
        durationMs,
        curl: toCurl(init.method, url, init),
        error
      };
      throw new HttpRequestError(`Request failed: ${init.method} ${url}: ${error.message}`, result);
    } finally {
      if (timeout) {
        clearTimeout(timeout);
      }
    }

    const durationMs = performance.now() - startedAt;
    const result = {
      method: init.method,
      url,
      status: response.status,
      headers: Object.fromEntries(response.headers.entries()),
      text,
      data,
      durationMs,
      curl: toCurl(init.method, url, init)
    };

    const allowedStatuses = requestOptions.allowStatuses
      ?? [requestOptions.expectedStatus ?? 200];
    if (!allowedStatuses.includes(response.status)) {
      throw new HttpRequestError(
        `Unexpected status ${response.status} for ${init.method} ${url}`,
        result
      );
    }
    return result;
  }

  return {
    baseUrl,
    get: (path, options) => request('GET', path, options),
    post: (path, options) => request('POST', path, options),
    delete: (path, options) => request('DELETE', path, options),
    head: (path, options) => request('HEAD', path, options),
    request
  };
}

export function buildUrl(baseUrl, path, params = undefined) {
  const url = /^https?:\/\//i.test(path)
    ? new URL(path)
    : new URL(path.startsWith('/') ? path : `/${path}`, baseUrl);
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    url.searchParams.set(key, String(value));
  }
  return url.toString();
}

export function normalizeBaseUrl(value) {
  const url = `${value ?? ''}`.trim() || 'https://127.0.0.1';
  return url.endsWith('/') ? url : `${url}/`;
}

export function resolveResourceUrl(baseUrl, value) {
  const url = `${value ?? ''}`.trim();
  if (!url || /^(data:|blob:|indexeddb:)/i.test(url)) {
    return null;
  }
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  if (url.startsWith('/')) {
    return new URL(url, baseUrl).toString();
  }
  return new URL(url, baseUrl).toString();
}

function parseBody(text, contentType = '') {
  if (!text) {
    return null;
  }
  if ((contentType ?? '').toLowerCase().includes('json')) {
    return JSON.parse(text);
  }
  const trimmed = text.trim();
  if ((trimmed.startsWith('{') && trimmed.endsWith('}'))
      || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
    try {
      return JSON.parse(trimmed);
    } catch {
      return null;
    }
  }
  return null;
}

function toCurl(method, url, init) {
  const parts = ['curl', '-i', '-X', shellQuote(method), shellQuote(url)];
  for (const [key, value] of Object.entries(init.headers ?? {})) {
    if (value === undefined) {
      continue;
    }
    parts.push('-H', shellQuote(`${key}: ${value}`));
  }
  if (init.body !== undefined && typeof init.body === 'string') {
    parts.push('--data', shellQuote(init.body));
  }
  return parts.join(' ');
}

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\"'\"'")}'`;
}
