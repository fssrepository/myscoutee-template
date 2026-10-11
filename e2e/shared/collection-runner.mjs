import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { assert, expectArray, expectDuration, expectObject } from './assertions.mjs';
import { connectWebSocket, toWebSocketUrl } from './websocket.mjs';

export async function discoverCollection(rootDir) {
  const cases = [];
  await walk(rootDir, cases, rootDir);
  return cases.sort((left, right) => {
    const orderDiff = Number(left.request.order ?? 0) - Number(right.request.order ?? 0);
    return orderDiff !== 0 ? orderDiff : left.id.localeCompare(right.id);
  });
}

export async function runCollection(options) {
  const {
    rootDir,
    ctx,
    filter = '',
    group = '',
    listOnly = false,
    record = false,
    artifactsDir = path.join(rootDir, '..', '..', '.artifacts'),
    failFast = false,
    includeMutations = false
  } = options;
  const cases = await discoverCollection(rootDir);
  const selected = cases.filter(testCase => matchesFilter(testCase, {
    filter,
    group,
    includeMutations,
    qaCase: options.qaCase
  }));

  if (listOnly) {
    printCaseList(selected);
    return { passed: 0, failed: 0, skipped: 0, total: selected.length };
  }

  const summary = { passed: 0, failed: 0, skipped: 0, total: selected.length };
  for (const testCase of selected) {
    const label = `${testCase.id} ${testCase.request.name ?? ''}`.trim();
    try {
      const skipReason = await resolveSkipReason(testCase, ctx);
      if (skipReason) {
        summary.skipped += 1;
        console.log(`- SKIP ${label} (${skipReason})`);
        continue;
      }
      const result = await runCase(testCase, ctx);
      summary.passed += 1;
      if (record) {
        await recordCase(artifactsDir, testCase, result);
      }
      console.log(`- PASS ${label} ${result.status} ${Math.round(result.durationMs)}ms`);
    } catch (error) {
      summary.failed += 1;
      console.error(`- FAIL ${label}`);
      console.error(`  ${error.message}`);
      if (error.result?.curl) {
        console.error(`  ${error.result.curl}`);
      }
      if (error.details) {
        console.error(`  details: ${JSON.stringify(error.details).slice(0, 1200)}`);
      }
      if (process.env.E2E_PRINT_STACK === 'true' && error.stack) {
        console.error(error.stack.split('\n').slice(0, 10).join('\n'));
      }
      if (error.result && record) {
        await recordCase(artifactsDir, testCase, error.result, error);
      }
      if (failFast) {
        break;
      }
    }
  }
  return summary;
}

export async function runCase(testCase, ctx) {
  for (const requirement of testCase.request.requires ?? []) {
    if (!ctx.resolveRequirement) {
      throw new Error(`${testCase.id} requires ${requirement}, but this runner has no requirement resolver`);
    }
    await ctx.resolveRequirement(requirement);
  }
  const prepareModule = await importOptional(testCase.dir, 'prepare.mjs');
  const expected = await readJsonOptional(testCase.dir, 'response.json');
  const scriptName = testCase.request.script ?? 'assert.mjs';
  const assertModule = await importOptional(testCase.dir, scriptName);
  if (prepareModule?.prepare) {
    const patch = await prepareModule.prepare({ ctx, testCase });
    if (patch && typeof patch === 'object') {
      testCase = {
        ...testCase,
        request: deepMerge(testCase.request, patch)
      };
    }
  }

  const request = interpolate(testCase.request, ctx.vars ?? {});
  const method = request.method ?? 'GET';
  for (const setupRequest of request.setup ?? []) {
    await executeRequest(ctx, setupRequest);
  }
  if (request.transport === 'websocket') {
    const result = await executeWebSocket(ctx, request);
    expectDuration(
      result,
      Number(request.maxMs ?? expected?.maxMs ?? ctx.defaultMaxMs),
      `${testCase.id} WS ${request.path}`
    );
    if (assertModule?.assertResponse) {
      await assertModule.assertResponse({ ctx, result, request, expected, testCase });
    }
    return result;
  }
  const result = await ctx.http.request(method, request.path, {
    params: request.params,
    headers: request.headers,
    json: request.body,
    expectedStatus: request.expectedStatus,
    allowStatuses: request.allowStatuses,
    timeoutMs: request.timeoutMs
  });
  expectExpectedResponse(result, expected, testCase);
  expectDuration(
    result,
    Number(request.maxMs ?? expected?.maxMs ?? ctx.defaultMaxMs),
    `${testCase.id} ${method} ${request.path}`
  );
  if (assertModule?.assertResponse) {
    await assertModule.assertResponse({ ctx, result, request, expected, testCase });
  }
  return result;
}

async function executeRequest(ctx, request) {
  const method = request.method ?? 'GET';
  return ctx.http.request(method, request.path, {
    params: request.params,
    headers: request.headers,
    json: request.body,
    expectedStatus: request.expectedStatus,
    allowStatuses: request.allowStatuses,
    timeoutMs: request.timeoutMs
  });
}

async function executeWebSocket(ctx, request) {
  const url = toWebSocketUrl(ctx.http.baseUrl, request.path, request.params);
  return connectWebSocket(url, {
    timeoutMs: request.timeoutMs,
    holdOpenMs: request.holdOpenMs,
    waitForMessage: request.waitForMessage,
    send: request.send
  });
}

export function parseCommonArgs(argv) {
  const args = {
    filter: '',
    group: '',
    listOnly: false,
    record: false,
    failFast: false,
    includeMutations: false,
    qaCase: ''
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--list') {
      args.listOnly = true;
    } else if (arg === '--record') {
      args.record = true;
    } else if (arg === '--fail-fast') {
      args.failFast = true;
    } else if (arg === '--include-mutations') {
      args.includeMutations = true;
    } else if (arg === '--qa-case') {
      args.qaCase = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--qa-case=')) {
      args.qaCase = arg.slice('--qa-case='.length);
    } else if (arg === '--group') {
      args.group = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--group=')) {
      args.group = arg.slice('--group='.length);
    } else if (arg === '--case') {
      args.filter = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--case=')) {
      args.filter = arg.slice('--case='.length);
    } else if (!arg.startsWith('-') && !args.filter) {
      args.filter = arg;
    }
  }
  return args;
}

async function walk(currentDir, cases, rootDir) {
  const entries = await readdir(currentDir, { withFileTypes: true });
  const requestEntry = entries.find(entry => entry.isFile() && entry.name === 'request.json');
  if (requestEntry) {
    const requestPath = path.join(currentDir, requestEntry.name);
    const request = JSON.parse(await readFile(requestPath, 'utf8'));
    const id = path.relative(rootDir, currentDir).replaceAll(path.sep, '/');
    cases.push({
      id,
      dir: currentDir,
      requestPath,
      request
    });
    return;
  }
  for (const entry of entries) {
    if (entry.isDirectory()) {
      await walk(path.join(currentDir, entry.name), cases, rootDir);
    }
  }
}

function matchesFilter(testCase, options) {
  const request = testCase.request;
  if (request.mutation && !options.includeMutations) {
    return false;
  }
  if (options.group && request.group !== options.group && !testCase.id.startsWith(`${options.group}/`)) {
    return false;
  }
  if (options.qaCase && !qaBranchIds(request).includes(options.qaCase)) {
    return false;
  }
  if (!options.filter) {
    return true;
  }
  return testCase.id.includes(options.filter)
    || `${request.name ?? ''}`.toLowerCase().includes(options.filter.toLowerCase())
    || (request.tags ?? []).some(tag => `${tag}`.includes(options.filter));
}

export function qaBranchIds(request = {}) {
  const branches = request.qaBranches;
  if (Array.isArray(branches)) {
    return branches.map(value => `${value}`);
  }
  if (!branches || typeof branches !== 'object') {
    return [];
  }
  return [...new Set(Object.values(branches)
    .flatMap(value => Array.isArray(value) ? value : [])
    .map(value => `${value}`))];
}

export async function resolveSkipReason(testCase, ctx) {
  const request = testCase.request;
  if (request.skip) {
    return request.skip === true ? 'marked skipped' : request.skip;
  }
  if (request.e2eOnly && ctx.mode !== 'e2e' && ctx.mode !== 'stress') {
    return `requires e2e/stress mode, current mode is ${ctx.mode ?? 'contract'}`;
  }
  const prepareModule = await importOptional(testCase.dir, 'prepare.mjs');
  if (prepareModule?.skip) {
    return await prepareModule.skip({ ctx, testCase });
  }
  return '';
}

export async function importOptional(dir, filename) {
  try {
    return await import(pathToFileURL(path.join(dir, filename)).href);
  } catch (error) {
    if (error.code === 'ERR_MODULE_NOT_FOUND' || error.code === 'MODULE_NOT_FOUND') {
      return null;
    }
    if (error.message?.includes(filename)) {
      return null;
    }
    throw error;
  }
}

async function readJsonOptional(dir, filename) {
  try {
    return JSON.parse(await readFile(path.join(dir, filename), 'utf8'));
  } catch (error) {
    if (error.code === 'ENOENT') {
      return null;
    }
    throw error;
  }
}

function expectExpectedResponse(result, expected, testCase) {
  if (!expected) {
    return;
  }
  if (expected.status !== undefined) {
    assert(
      result.status === Number(expected.status),
      `${testCase.id} expected status ${expected.status}, got ${result.status}`
    );
  }
  if (expected.body) {
    expectBodyContract(result.data, expected.body, `${testCase.id}.body`);
  }
}

function expectBodyContract(value, contract, pathLabel) {
  if (!contract || typeof contract !== 'object') {
    return;
  }
  if (contract.type === 'array') {
    const array = expectArray(value, pathLabel);
    if (contract.minItems !== undefined) {
      assert(array.length >= Number(contract.minItems), `${pathLabel} should have at least ${contract.minItems} item(s)`, {
        length: array.length
      });
    }
    if (contract.items) {
      array.forEach((item, index) => expectBodyContract(item, contract.items, `${pathLabel}[${index}]`));
    }
    return;
  }
  if (contract.type === 'object') {
    const object = expectObject(value, pathLabel);
    for (const key of contract.required ?? []) {
      assert(Object.prototype.hasOwnProperty.call(object, key), `${pathLabel}.${key} should be present`, {
        value: object
      });
    }
    for (const [key, childContract] of Object.entries(contract.properties ?? {})) {
      if (object[key] === undefined || object[key] === null) {
        if (childContract.optional) {
          continue;
        }
      }
      expectBodyContract(object[key], childContract, `${pathLabel}.${key}`);
    }
    return;
  }
  if (contract.type === 'string') {
    assert(typeof value === 'string', `${pathLabel} should be a string`, { value });
    if (contract.minLength !== undefined) {
      assert(value.length >= Number(contract.minLength), `${pathLabel} should have length >= ${contract.minLength}`, { value });
    }
    return;
  }
  if (contract.type === 'number') {
    assert(
      typeof value === 'number' && Number.isFinite(value),
      `${pathLabel} should be a finite number`,
      { value }
    );
    return;
  }
  if (contract.type === 'boolean') {
    assert(typeof value === 'boolean', `${pathLabel} should be boolean`, { value });
  }
}

function interpolate(value, vars) {
  if (typeof value === 'string') {
    return value.replace(/\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}/g, (_match, key) => {
      if (key.startsWith('$env.')) {
        return process.env[key.slice('$env.'.length)] ?? '';
      }
      if (!Object.prototype.hasOwnProperty.call(vars, key)) {
        throw new Error(`Missing collection variable: ${key}`);
      }
      return vars[key];
    });
  }
  if (Array.isArray(value)) {
    return value.map(item => interpolate(item, vars));
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, interpolate(entry, vars)]));
  }
  return value;
}

export function deepMerge(left, right) {
  if (!right || typeof right !== 'object' || Array.isArray(right)) {
    return right;
  }
  const result = { ...(left ?? {}) };
  for (const [key, value] of Object.entries(right)) {
    result[key] = value && typeof value === 'object' && !Array.isArray(value)
      ? deepMerge(result[key], value)
      : value;
  }
  return result;
}

async function recordCase(artifactsDir, testCase, result, error = null) {
  const outputDir = path.join(artifactsDir, testCase.id);
  await mkdir(outputDir, { recursive: true });
  await writeFile(path.join(outputDir, 'response.json'), JSON.stringify({
    id: testCase.id,
    status: result.status,
    durationMs: result.durationMs,
    headers: result.headers,
    body: result.data ?? result.text,
    error: error ? error.message : null
  }, null, 2));
  await writeFile(path.join(outputDir, 'curl.sh'), `${result.curl}\n`);
}

function printCaseList(cases) {
  for (const testCase of cases) {
    const marker = testCase.request.mutation ? 'mutation' : 'safe';
    console.log(`${testCase.id} [${testCase.request.group ?? 'ungrouped'}:${marker}] ${testCase.request.name ?? ''}`);
  }
}
