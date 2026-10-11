export class AssertionError extends Error {
  constructor(message, details = undefined) {
    super(message);
    this.name = 'AssertionError';
    this.details = details;
  }
}

export function assert(condition, message, details = undefined) {
  if (!condition) {
    throw new AssertionError(message, details);
  }
}

export function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

export function expectObject(value, path = 'value') {
  assert(isObject(value), `${path} should be an object`, { value });
  return value;
}

export function expectArray(value, path = 'value') {
  assert(Array.isArray(value), `${path} should be an array`, { value });
  return value;
}

export function expectNonEmptyArray(value, path = 'value') {
  const array = expectArray(value, path);
  assert(array.length > 0, `${path} should not be empty`);
  return array;
}

export function expectString(value, path = 'value', options = {}) {
  assert(typeof value === 'string', `${path} should be a string`, { value });
  if (!options.allowEmpty) {
    assert(value.trim().length > 0, `${path} should not be empty`, { value });
  }
  return value;
}

export function expectOptionalString(value, path = 'value') {
  if (value === null || value === undefined) {
    return value;
  }
  return expectString(value, path, { allowEmpty: true });
}

export function expectNumber(value, path = 'value') {
  assert(
    typeof value === 'number' && Number.isFinite(value),
    `${path} should be a finite number`,
    { value }
  );
  return value;
}

export function expectOptionalNumber(value, path = 'value') {
  if (value === null || value === undefined) {
    return value;
  }
  return expectNumber(value, path);
}

export function expectBoolean(value, path = 'value') {
  assert(typeof value === 'boolean', `${path} should be a boolean`, { value });
  return value;
}

export function expectOptionalBoolean(value, path = 'value') {
  if (value === null || value === undefined) {
    return value;
  }
  return expectBoolean(value, path);
}

export function expectOneOf(value, allowed, path = 'value') {
  assert(allowed.includes(value), `${path} should be one of ${allowed.join(', ')}`, { value });
  return value;
}

export function expectKeys(object, keys, path = 'value') {
  const target = expectObject(object, path);
  for (const key of keys) {
    assert(Object.prototype.hasOwnProperty.call(target, key), `${path}.${key} should be present`, {
      value: target
    });
  }
  return target;
}

export function expectDuration(result, maxMs, label = result?.url ?? 'request') {
  if (!Number.isFinite(maxMs) || maxMs <= 0) {
    return;
  }
  assert(
    result.durationMs <= maxMs,
    `${label} should finish within ${maxMs}ms, got ${Math.round(result.durationMs)}ms`,
    { durationMs: result.durationMs, maxMs }
  );
}

export function collectStringsByKey(value, key, result = []) {
  if (Array.isArray(value)) {
    for (const item of value) {
      collectStringsByKey(item, key, result);
    }
    return result;
  }
  if (!isObject(value)) {
    return result;
  }
  for (const [entryKey, entryValue] of Object.entries(value)) {
    if (entryKey === key) {
      if (typeof entryValue === 'string') {
        result.push(entryValue);
      } else if (Array.isArray(entryValue)) {
        for (const item of entryValue) {
          if (typeof item === 'string') {
            result.push(item);
          }
        }
      }
      continue;
    }
    collectStringsByKey(entryValue, key, result);
  }
  return result;
}

export function unique(values) {
  return [...new Set(values.map(value => `${value ?? ''}`.trim()).filter(Boolean))];
}

export function looksLikeImageUrl(value) {
  const url = `${value ?? ''}`.trim();
  if (!url) {
    return false;
  }
  return /^(https?:|data:image\/|blob:|indexeddb:|\/|assets\/|help-seeded-image:)/i.test(url);
}

export function expectImageUrl(value, path = 'imageUrl') {
  expectString(value, path);
  assert(looksLikeImageUrl(value), `${path} should look like an image URL`, { value });
  return value;
}

export function firstOrNull(value) {
  return Array.isArray(value) && value.length > 0 ? value[0] : null;
}
