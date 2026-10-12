#!/usr/bin/env node
import { execFile } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

import { createContext as createProjectContext, sessionActors } from './context.mjs';
import {
  discoverCollection,
  qaBranchIds,
  resolveSkipReason,
  runCase
} from '../../shared/collection-runner.mjs';
import { nativeDemoSessions } from '../../shared/native-demo-sessions.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..', '..');
const serverRoot = path.join(repoRoot, 'e2e/functional/backend');
const execFileAsync = promisify(execFile);
const args = parseArgs(process.argv.slice(2));

if (args.list) {
  await listScenarios(args.qaCase);
  process.exit(0);
}

const scenario = await loadScenario(args.scenario);
assertQaCaseSelection(scenario, args.qaCase);
applyScenarioOverrides(scenario, args);
const serverCases = new Map((await discoverCollection(serverRoot)).map(testCase => [testCase.id, testCase]));
const baseUrl = process.env.E2E_STRESS_BASE_URL ?? process.env.E2E_SERVER_BASE_URL ?? scenario.baseUrl ?? 'https://127.0.0.1';
const mode = process.env.E2E_STRESS_MODE ?? scenario.mode ?? 'contract';
const nativeSessions = process.env.E2E_NATIVE_DEMO_SESSIONS === 'true' ? nativeDemoSessions(baseUrl) : null;
if (nativeSessions && shouldResetE2eDatabase(mode, scenario)) {
  throw new Error('Native DEMO sessions require E2E_SKIP_E2E_RESET=true; production-style data must not be reset by this runner');
}
if (nativeSessions) {
  try {
    await nativeSessions.prepare(...sessionActors());
  } catch (error) {
    await nativeSessions.close();
    throw error;
  }
}

if (shouldResetE2eDatabase(mode, scenario)) {
  const { resetE2eDatabase } = await import('../../functional/backend/hooks/e2e-db.mjs');
  await resetE2eDatabase(createContext({ baseUrl, mode, runId: 'e2e-reset', vu: 0 }));
}

function shouldResetE2eDatabase(currentMode, currentScenario) {
  return (currentMode === 'e2e' || currentMode === 'stress' || currentScenario.resetE2E === true)
    && currentScenario.resetE2E !== false
    && process.env.E2E_SKIP_E2E_RESET !== 'true';
}

const metrics = [];
const startedAtIso = new Date().toISOString();
const startedAt = performance.now();
const resourceMonitor = args.record ? startResourceMonitor() : null;
let runError = null;
try {
  await Promise.all(Array.from({ length: scenario.users ?? 1 }, async (_item, index) => {
    const vu = index + 1;
    const rampDelayMs = rampDelay(index, scenario.users ?? 1, scenario.rampUpMs ?? 0);
    await sleep(rampDelayMs);
    await runVirtualUser({ scenario, serverCases, metrics, baseUrl, mode, vu, startedAt });
  }));
} catch (error) {
  runError = error;
} finally {
  await resourceMonitor?.stop();
  try { await nativeSessions?.close(); } catch (error) { runError ??= error; }
}
const endedAtIso = new Date().toISOString();

const summary = summarize(metrics);
const artifactDir = args.record
  ? await writeArtifacts({
    scenario,
    summary,
    metrics,
    resources: resourceMonitor?.samples ?? [],
    startedAtIso,
    endedAtIso,
    baseUrl,
    mode,
    runError,
    artifactsDir: args.artifactsDir
  })
  : '';
printSummary(scenario, summary, { artifactDir });
if (runError) {
  console.error(`stress runner error: ${runError.stack ?? runError.message}`);
}
const failed = evaluateThresholds(summary, scenario.thresholds ?? {}) || Boolean(runError);
process.exitCode = failed ? 1 : 0;

async function runVirtualUser({ scenario, serverCases, metrics, baseUrl, mode, vu, startedAt }) {
  const durationMs = Number(scenario.durationMs ?? 0);
  const iterations = Number(scenario.iterations ?? 1);
  const reusableCtx = scenario.reuseContext
    ? createContext({ baseUrl, mode, runId: `${scenario.id ?? 'scenario'}-vu${vu}-${Date.now()}`, vu })
    : null;
  let iteration = 0;
  while (true) {
    iteration += 1;
    if (durationMs > 0 && performance.now() - startedAt >= durationMs) {
      return;
    }
    if (durationMs <= 0 && iteration > iterations) {
      return;
    }
    for (const [stepIndex, step] of scenario.steps.entries()) {
      const runId = `${scenario.id ?? 'scenario'}-vu${vu}-i${iteration}-s${stepIndex + 1}-${Date.now()}`;
      const ctx = reusableCtx ?? createContext({ baseUrl, mode, runId, vu });
      if (Array.isArray(step.parallel) && step.parallel.length > 0) {
        const parallelMetrics = await Promise.all(step.parallel.map((parallelStep, parallelIndex) =>
          measureStep({
            step: parallelStep,
            ctx,
            serverCases,
            vu,
            iteration,
            stepIndex: `${stepIndex + 1}.${parallelIndex + 1}`,
            startedAt
          })));
        metrics.push(...parallelMetrics);
        if (parallelMetrics.some(item => !item.ok) && scenario.failFast) {
          return;
        }
      } else {
        const metric = await measureStep({ step, ctx, serverCases, vu, iteration, stepIndex: stepIndex + 1, startedAt });
        metrics.push(metric);
        if (!metric.ok && scenario.failFast) {
          return;
        }
      }
      await sleep(jitter(scenario.thinkTimeMs ?? 0, scenario.thinkTimeJitterMs ?? 0));
    }
  }
}

async function measureStep({ step, ctx, serverCases, vu, iteration, stepIndex, startedAt }) {
  const started = performance.now();
  let ok = true;
  let status = 0;
  let error = '';
  let resultMetric = {};
  try {
    const result = await runStep(step, ctx, serverCases);
    if (result?.skipped) {
      return {
        step: step.name ?? step.collectionCase ?? `step ${stepIndex}`,
        vu,
        iteration,
        ok: true,
        skipped: true,
        skipReason: result.skipReason,
        status: 0,
        transport: '',
        method: 'SKIP',
        url: step.collectionCase ?? '',
        responseBytes: 0,
        messageCount: 0,
        openMs: 0,
        startedAtMs: started - startedAt,
        endedAtMs: performance.now() - startedAt,
        durationMs: performance.now() - started,
        error: ''
      };
    }
    status = result?.status ?? 0;
    resultMetric = metricFromResult(result, step);
  } catch (err) {
    ok = false;
    status = err.result?.status ?? 0;
    error = err.message;
    resultMetric = metricFromResult(err.result, step);
  }
  const ended = performance.now();
  const duration = ended - started;
  return {
    step: step.name ?? step.collectionCase ?? `${step.method ?? 'GET'} ${step.path}` ?? `step ${stepIndex}`,
    vu,
    iteration,
    ok,
    status,
    transport: resultMetric.transport,
    method: resultMetric.method,
    url: resultMetric.url,
    responseBytes: resultMetric.responseBytes,
    messageCount: resultMetric.messageCount,
    openMs: resultMetric.openMs,
    startedAtMs: started - startedAt,
    endedAtMs: ended - startedAt,
    durationMs: duration,
    error
  };
}

async function runStep(step, ctx, serverCases) {
  if (step.collectionCase) {
    const testCase = serverCases.get(step.collectionCase);
    if (!testCase) {
      throw new Error(`Unknown collection case ${step.collectionCase}`);
    }
    if (testCase.request.e2eOnly && ctx.mode !== 'e2e' && ctx.mode !== 'stress') {
      throw new Error(`${step.collectionCase} requires e2e/stress mode`);
    }
    const skipReason = await resolveSkipReason(testCase, ctx);
    if (skipReason) {
      return { skipped: true, skipReason };
    }
    return runCase(testCase, ctx);
  }
  const request = interpolate(step, ctx.vars);
  return ctx.http.request(request.method ?? 'GET', request.path, {
    params: request.params,
    headers: request.headers,
    json: request.body,
    expectedStatus: request.expectedStatus,
    allowStatuses: request.allowStatuses,
    timeoutMs: request.timeoutMs
  });
}

function createContext(options) {
  return createProjectContext({ ...options, nativeSessions });
}

async function loadScenario(name) {
  const scenarioName = name || process.env.E2E_STRESS_SCENARIO || 'server-contract-smoke';
  const scenarioPath = path.join(__dirname, 'scenarios', `${scenarioName}.json`);
  const scenario = JSON.parse(await import('node:fs/promises').then(fs => fs.readFile(scenarioPath, 'utf8')));
  scenario.id ??= scenarioName;
  return scenario;
}

async function listScenarios(qaCase = '') {
  const { readdir, readFile } = await import('node:fs/promises');
  const files = (await readdir(path.join(__dirname, 'scenarios'))).filter(file => file.endsWith('.json')).sort();
  for (const file of files) {
    const scenario = JSON.parse(await readFile(path.join(__dirname, 'scenarios', file), 'utf8'));
    if (qaCase && !qaBranchIds(scenario).includes(qaCase)) {
      continue;
    }
    console.log(`${file.replace(/\.json$/, '')} [${scenario.mode ?? 'contract'}] users=${scenario.users ?? 1} iterations=${scenario.iterations ?? '-'} durationMs=${scenario.durationMs ?? '-'} - ${scenario.name ?? ''}`);
  }
}

function assertQaCaseSelection(scenario, qaCase) {
  if (qaCase && !qaBranchIds(scenario).includes(qaCase)) {
    throw new Error(`Stress scenario ${scenario.id} is not linked to QA branch ${qaCase}`);
  }
}

function summarize(metrics) {
  const executed = metrics.filter(item => !item.skipped);
  const skipped = metrics.length - executed.length;
  const durations = executed.map(latencyMs).sort((a, b) => a - b);
  const failed = executed.filter(item => !item.ok).length;
  const total = executed.length;
  const elapsedMs = elapsedFor(executed);
  const responseBytes = executed.reduce((sum, item) => sum + Number(item.responseBytes ?? 0), 0);
  return {
    total,
    skipped,
    failed,
    passed: total - failed,
    errorRate: total === 0 ? 0 : failed / total,
    elapsedMs,
    requestsPerSec: perSecond(total, elapsedMs),
    passedPerSec: perSecond(total - failed, elapsedMs),
    responseBytes,
    statusCounts: countBy(executed.map(item => String(item.status ?? 0))),
    minMs: durations[0] ?? 0,
    maxMs: durations[durations.length - 1] ?? 0,
    avgMs: durations.reduce((sum, value) => sum + value, 0) / Math.max(1, durations.length),
    p50Ms: percentile(durations, 0.50),
    p90Ms: percentile(durations, 0.90),
    p95Ms: percentile(durations, 0.95),
    p99Ms: percentile(durations, 0.99),
    byStep: summarizeByStep(executed)
  };
}

function summarizeByStep(metrics) {
  const groups = new Map();
  for (const item of metrics) {
    const list = groups.get(item.step) ?? [];
    list.push(item);
    groups.set(item.step, list);
  }
  return [...groups.entries()].map(([step, items]) => {
    const transport = dominant(items.map(item => item.transport).filter(Boolean)) ?? '';
    const latencies = items.map(latencyMs).sort((a, b) => a - b);
    const wallDurations = items.map(item => Number(item.durationMs ?? 0)).sort((a, b) => a - b);
    const openDurations = items
            .map(item => Number(item.openMs ?? 0))
            .filter(value => Number.isFinite(value) && value > 0)
            .sort((a, b) => a - b);
    return {
      step,
      transport,
      total: items.length,
      failed: items.filter(item => !item.ok).length,
      passed: items.filter(item => item.ok).length,
      errorRate: items.length === 0 ? 0 : items.filter(item => !item.ok).length / items.length,
      requestsPerSec: perSecond(items.length, elapsedFor(items)),
      responseBytes: items.reduce((sum, item) => sum + Number(item.responseBytes ?? 0), 0),
      statusCounts: countBy(items.map(item => String(item.status ?? 0))),
      avgMs: average(latencies),
      p50Ms: percentile(latencies, 0.50),
      p90Ms: percentile(latencies, 0.90),
      p95Ms: percentile(latencies, 0.95),
      p99Ms: percentile(latencies, 0.99),
      maxMs: Math.max(...latencies),
      wallP95Ms: percentile(wallDurations, 0.95),
      wallMaxMs: Math.max(...wallDurations),
      openP95Ms: openDurations.length > 0 ? percentile(openDurations, 0.95) : 0,
      openMaxMs: openDurations.length > 0 ? Math.max(...openDurations) : 0
    };
  }).sort((left, right) => right.p95Ms - left.p95Ms);
}

function latencyMs(item) {
  const openMs = Number(item?.openMs ?? 0);
  if (item?.transport === 'websocket' && Number.isFinite(openMs) && openMs > 0) {
    return openMs;
  }
  const durationMs = Number(item?.durationMs ?? 0);
  return Number.isFinite(durationMs) ? durationMs : 0;
}

function printSummary(scenario, summary, options = {}) {
  console.log(`\nstress scenario: ${scenario.name ?? scenario.id}`);
  console.log(`requests: ${summary.total}, passed: ${summary.passed}, failed: ${summary.failed}, skipped: ${summary.skipped}, errorRate: ${(summary.errorRate * 100).toFixed(2)}%`);
  console.log(`throughput: ${summary.requestsPerSec.toFixed(2)} req/s over ${(summary.elapsedMs / 1000).toFixed(1)}s, response=${formatBytes(summary.responseBytes)}`);
  console.log(`status: ${formatCounts(summary.statusCounts)}`);
  console.log(`latency ms: avg=${Math.round(summary.avgMs)} p50=${Math.round(summary.p50Ms)} p90=${Math.round(summary.p90Ms)} p95=${Math.round(summary.p95Ms)} p99=${Math.round(summary.p99Ms)} max=${Math.round(summary.maxMs)}`);
  for (const item of summary.byStep) {
    const websocketDetail = item.transport === 'websocket'
      ? ` openP95=${Math.round(item.openP95Ms)}ms wallP95=${Math.round(item.wallP95Ms)}ms`
      : '';
    console.log(`- ${item.step}: total=${item.total} failed=${item.failed} errorRate=${(item.errorRate * 100).toFixed(2)}% rps=${item.requestsPerSec.toFixed(2)} p50=${Math.round(item.p50Ms)}ms p95=${Math.round(item.p95Ms)}ms p99=${Math.round(item.p99Ms)}ms max=${Math.round(item.maxMs)}ms${websocketDetail}`);
  }
  if (options.artifactDir) {
    console.log(`artifacts: ${options.artifactDir}`);
  }
}

function startResourceMonitor() {
  const intervalMs = Math.max(500, Number(process.env.E2E_STRESS_RESOURCE_INTERVAL_MS ?? 2000) || 2000);
  const samples = [];
  let pending = Promise.resolve();
  let stopped = false;

  async function sample() {
    if (stopped) {
      return;
    }
    const sampledAtIso = new Date().toISOString();
    const host = sampleHost();
    const docker = await sampleDocker();
    samples.push({ sampledAtIso, host, docker });
  }

  const schedule = () => {
    pending = pending.then(sample).catch(error => {
      samples.push({
        sampledAtIso: new Date().toISOString(),
        host: sampleHost(),
        docker: { error: error.message }
      });
    });
  };
  schedule();
  const timer = setInterval(schedule, intervalMs);

  return {
    samples,
    async stop() {
      stopped = true;
      clearInterval(timer);
      await pending;
    }
  };
}

function sampleHost() {
  const totalMemBytes = os.totalmem();
  const freeMemBytes = os.freemem();
  return {
    totalMemBytes,
    freeMemBytes,
    usedMemBytes: totalMemBytes - freeMemBytes,
    loadAvg: os.loadavg(),
    cpuCount: os.cpus().length,
    uptimeSec: os.uptime()
  };
}

async function sampleDocker() {
  try {
    const result = await execFileAsync('docker', ['stats', '--no-stream', '--format', '{{json .}}'], {
      timeout: Number(process.env.E2E_STRESS_DOCKER_STATS_TIMEOUT_MS ?? 5000),
      maxBuffer: 1024 * 1024
    });
    return result.stdout
      .trim()
      .split('\n')
      .filter(Boolean)
      .map(line => JSON.parse(line));
  } catch (error) {
    return { error: error.message };
  }
}

async function writeArtifacts({ scenario, summary, metrics, resources, startedAtIso, endedAtIso, baseUrl, mode, runError, artifactsDir }) {
  const rootDir = artifactsDir
    ? path.resolve(artifactsDir)
    : path.join(repoRoot, '.e2e-artifacts', 'stress', 'backend');
  const runDir = path.join(rootDir, `${safeName(scenario.id ?? 'scenario')}-${startedAtIso.replace(/[:.]/g, '-')}`);
  await mkdir(runDir, { recursive: true });

  const payload = {
    scenario: {
      id: scenario.id,
      name: scenario.name,
      mode,
      baseUrl,
      users: scenario.users ?? 1,
      rampUpMs: scenario.rampUpMs ?? 0,
      durationMs: scenario.durationMs ?? 0,
      iterations: scenario.iterations ?? null,
      thresholds: scenario.thresholds ?? {}
    },
    startedAtIso,
    endedAtIso,
    summary,
    resourceSummary: summarizeResources(resources),
    runError: runError ? { message: runError.message, stack: runError.stack } : null
  };

  await writeFile(path.join(runDir, 'summary.json'), `${JSON.stringify(payload, null, 2)}\n`);
  await writeFile(path.join(runDir, 'metrics.json'), `${JSON.stringify(metrics, null, 2)}\n`);
  await writeFile(path.join(runDir, 'resources.json'), `${JSON.stringify(resources, null, 2)}\n`);
  await writeFile(path.join(runDir, 'index.md'), renderArtifactIndex(payload));
  return runDir;
}

function renderArtifactIndex(payload) {
  const resourceSummary = payload.resourceSummary;
  const lines = [
    `# ${payload.scenario.name ?? payload.scenario.id}`,
    '',
    `- Scenario: \`${payload.scenario.id}\``,
    `- Mode/base URL: \`${payload.scenario.mode}\` at \`${payload.scenario.baseUrl}\``,
    `- Started: ${payload.startedAtIso}`,
    `- Ended: ${payload.endedAtIso}`,
    `- Requests: ${payload.summary.total} total, ${payload.summary.passed} passed, ${payload.summary.failed} failed`,
    `- Error rate: ${(payload.summary.errorRate * 100).toFixed(2)}%`,
    `- Throughput: ${payload.summary.requestsPerSec.toFixed(2)} req/s over ${(payload.summary.elapsedMs / 1000).toFixed(1)}s`,
    `- Response bytes: ${formatBytes(payload.summary.responseBytes)}`,
    `- Status counts: ${formatCounts(payload.summary.statusCounts)}`,
    `- Latency: avg ${Math.round(payload.summary.avgMs)}ms, p95 ${Math.round(payload.summary.p95Ms)}ms, p99 ${Math.round(payload.summary.p99Ms)}ms, max ${Math.round(payload.summary.maxMs)}ms`,
    `- Host memory peak: ${formatBytes(resourceSummary.host.maxUsedMemBytes)} used, min free ${formatBytes(resourceSummary.host.minFreeMemBytes)}`,
    `- Host load peak: ${resourceSummary.host.maxLoadAvg1.toFixed(2)} over ${resourceSummary.host.cpuCount} CPUs`,
    '',
    '## Step Latency',
    '',
    '| Step | Total | Failed | Error Rate | Req/s | Avg | p50 | p95 | p99 | Max | WS wall p95 | Status |',
    '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |',
    ...payload.summary.byStep.map(item => `| ${escapeTable(item.step)} | ${item.total} | ${item.failed} | ${(item.errorRate * 100).toFixed(2)}% | ${item.requestsPerSec.toFixed(2)} | ${Math.round(item.avgMs)}ms | ${Math.round(item.p50Ms)}ms | ${Math.round(item.p95Ms)}ms | ${Math.round(item.p99Ms)}ms | ${Math.round(item.maxMs)}ms | ${item.transport === 'websocket' ? `${Math.round(item.wallP95Ms)}ms` : '-'} | ${escapeTable(formatCounts(item.statusCounts))} |`),
    '',
    '## Container Peaks',
    '',
    '| Container | Max CPU | Max Memory |',
    '| --- | ---: | ---: |',
    ...resourceSummary.containers.map(item => `| ${escapeTable(item.name)} | ${item.maxCpuPercent.toFixed(2)}% | ${formatBytes(item.maxMemBytes)} |`),
    ''
  ];
  if (payload.runError) {
    lines.push('## Runner Error', '', '```', payload.runError.stack ?? payload.runError.message, '```', '');
  }
  lines.push('Raw files: `summary.json`, `metrics.json`, `resources.json`.', '');
  return `${lines.join('\n')}\n`;
}

function summarizeResources(resources) {
  const hostSummary = {
    cpuCount: 0,
    minFreeMemBytes: Number.POSITIVE_INFINITY,
    maxUsedMemBytes: 0,
    maxLoadAvg1: 0
  };
  const containers = new Map();

  for (const sample of resources) {
    if (sample.host) {
      hostSummary.cpuCount = sample.host.cpuCount ?? hostSummary.cpuCount;
      hostSummary.minFreeMemBytes = Math.min(hostSummary.minFreeMemBytes, sample.host.freeMemBytes ?? Number.POSITIVE_INFINITY);
      hostSummary.maxUsedMemBytes = Math.max(hostSummary.maxUsedMemBytes, sample.host.usedMemBytes ?? 0);
      hostSummary.maxLoadAvg1 = Math.max(hostSummary.maxLoadAvg1, sample.host.loadAvg?.[0] ?? 0);
    }
    if (!Array.isArray(sample.docker)) {
      continue;
    }
    for (const item of sample.docker) {
      const name = item.Name ?? item.NameOrID ?? item.Container ?? item.ID ?? 'unknown';
      const current = containers.get(name) ?? { name, maxCpuPercent: 0, maxMemBytes: 0 };
      current.maxCpuPercent = Math.max(current.maxCpuPercent, parsePercent(item.CPUPerc));
      current.maxMemBytes = Math.max(current.maxMemBytes, parseDockerMemoryBytes(item.MemUsage));
      containers.set(name, current);
    }
  }

  if (!Number.isFinite(hostSummary.minFreeMemBytes)) {
    hostSummary.minFreeMemBytes = 0;
  }

  return {
    host: hostSummary,
    containers: [...containers.values()].sort((a, b) => b.maxMemBytes - a.maxMemBytes)
  };
}

function parsePercent(value) {
  const parsed = Number.parseFloat(`${value ?? ''}`.replace('%', ''));
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseDockerMemoryBytes(value) {
  const firstPart = `${value ?? ''}`.split('/')[0].trim();
  const match = firstPart.match(/^([\d.]+)\s*([kmgtp]?i?b?)$/i);
  if (!match) {
    return 0;
  }
  const amount = Number.parseFloat(match[1]);
  const unit = match[2].toLowerCase();
  const multipliers = {
    b: 1,
    kb: 1000,
    kib: 1024,
    mb: 1000 ** 2,
    mib: 1024 ** 2,
    gb: 1000 ** 3,
    gib: 1024 ** 3,
    tb: 1000 ** 4,
    tib: 1024 ** 4,
    pb: 1000 ** 5,
    pib: 1024 ** 5
  };
  return Number.isFinite(amount) ? amount * (multipliers[unit] ?? 1) : 0;
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 B';
  }
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function escapeTable(value) {
  return `${value ?? ''}`.replace(/\|/g, '\\|').replace(/\n/g, ' ');
}

function safeName(value) {
  return `${value}`.replace(/[^a-zA-Z0-9_.-]+/g, '-');
}

function evaluateThresholds(summary, thresholds) {
  let failed = false;
  const maxChecks = [
    ['errorRate', summary.errorRate, thresholds.errorRate],
    ['p95Ms', summary.p95Ms, thresholds.p95Ms],
    ['p99Ms', summary.p99Ms, thresholds.p99Ms],
    ['maxMs', summary.maxMs, thresholds.maxMs]
  ];
  const minChecks = [
    ['requestsPerSec', summary.requestsPerSec, thresholds.requestsPerSecMin],
    ['passedPerSec', summary.passedPerSec, thresholds.passedPerSecMin]
  ];
  for (const [name, actual, expected] of maxChecks) {
    if (expected === undefined) {
      continue;
    }
    if (actual > Number(expected)) {
      failed = true;
      console.error(`threshold failed: ${name} actual=${actual} expected<=${expected}`);
    }
  }
  for (const [name, actual, expected] of minChecks) {
    if (expected === undefined) {
      continue;
    }
    if (actual < Number(expected)) {
      failed = true;
      console.error(`threshold failed: ${name} actual=${actual} expected>=${expected}`);
    }
  }
  for (const [stepName, stepThresholds] of Object.entries(thresholds.byStep ?? thresholds.steps ?? {})) {
    const step = summary.byStep.find(item => item.step === stepName);
    if (!step) {
      failed = true;
      console.error(`threshold failed: missing step ${stepName}`);
      continue;
    }
    failed = evaluateStepThresholds(step, stepThresholds) || failed;
  }
  return failed;
}

function evaluateStepThresholds(step, thresholds) {
  let failed = false;
  const maxChecks = [
    ['errorRate', step.errorRate, thresholds.errorRate],
    ['p95Ms', step.p95Ms, thresholds.p95Ms],
    ['p99Ms', step.p99Ms, thresholds.p99Ms],
    ['maxMs', step.maxMs, thresholds.maxMs]
  ];
  const minChecks = [
    ['requestsPerSec', step.requestsPerSec, thresholds.requestsPerSecMin]
  ];
  for (const [name, actual, expected] of maxChecks) {
    if (expected !== undefined && actual > Number(expected)) {
      failed = true;
      console.error(`threshold failed: ${step.step}.${name} actual=${actual} expected<=${expected}`);
    }
  }
  for (const [name, actual, expected] of minChecks) {
    if (expected !== undefined && actual < Number(expected)) {
      failed = true;
      console.error(`threshold failed: ${step.step}.${name} actual=${actual} expected>=${expected}`);
    }
  }
  return failed;
}

function metricFromResult(result, step) {
  if (!result) {
    return {
      transport: step.transport ?? '',
      method: step.method ?? '',
      url: step.path ?? '',
      responseBytes: 0,
      messageCount: 0,
      openMs: 0
    };
  }
  const messages = Array.isArray(result.messages) ? result.messages : null;
  const responseText = result.text ?? (messages ? messages.join('\n') : '');
  return {
    transport: messages ? 'websocket' : 'http',
    method: result.method ?? step.method ?? (messages ? 'WS' : 'GET'),
    url: result.url ?? step.path ?? '',
    responseBytes: Buffer.byteLength(`${responseText ?? ''}`, 'utf8'),
    messageCount: messages?.length ?? 0,
    openMs: Number(result.openMs ?? 0)
  };
}

function elapsedFor(metrics) {
  if (metrics.length === 0) {
    return 0;
  }
  const minStarted = Math.min(...metrics.map(item => Number(item.startedAtMs ?? 0)));
  const maxEnded = Math.max(...metrics.map(item => Number(item.endedAtMs ?? (item.startedAtMs ?? 0) + item.durationMs)));
  return Math.max(0, maxEnded - minStarted);
}

function perSecond(count, elapsedMs) {
  return elapsedMs > 0 ? count / (elapsedMs / 1000) : 0;
}

function average(values) {
  return values.reduce((sum, value) => sum + value, 0) / Math.max(1, values.length);
}

function countBy(values) {
  const counts = {};
  for (const value of values) {
    counts[value] = (counts[value] ?? 0) + 1;
  }
  return counts;
}

function dominant(values) {
  const counts = countBy(values);
  return Object.entries(counts).sort((left, right) => right[1] - left[1])[0]?.[0] ?? '';
}

function formatCounts(counts) {
  const entries = Object.entries(counts ?? {}).sort((left, right) => left[0].localeCompare(right[0]));
  return entries.length ? entries.map(([key, value]) => `${key}:${value}`).join(', ') : '-';
}

function percentile(values, ratio) {
  if (values.length === 0) {
    return 0;
  }
  const index = Math.min(values.length - 1, Math.ceil(values.length * ratio) - 1);
  return values[index];
}

function rampDelay(index, users, rampUpMs) {
  if (users <= 1 || rampUpMs <= 0) {
    return 0;
  }
  return Math.round((rampUpMs / users) * index);
}

function jitter(baseMs, jitterMs) {
  const base = Number(baseMs) || 0;
  const jitter = Number(jitterMs) || 0;
  return Math.max(0, base + (Math.random() * jitter * 2) - jitter);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, Math.max(0, Number(ms) || 0)));
}

function parseArgs(argv) {
  const args = { list: false, scenario: '', record: false, artifactsDir: '', qaCase: '' };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--list') {
      args.list = true;
    } else if (arg === '--record') {
      args.record = true;
    } else if (arg === '--artifacts-dir') {
      args.artifactsDir = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--artifacts-dir=')) {
      args.artifactsDir = arg.slice('--artifacts-dir='.length);
    } else if (arg === '--scenario') {
      args.scenario = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--scenario=')) {
      args.scenario = arg.slice('--scenario='.length);
    } else if (arg === '--qa-case') {
      args.qaCase = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--qa-case=')) {
      args.qaCase = arg.slice('--qa-case='.length);
    } else if (arg === '--users') {
      args.users = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--users=')) {
      args.users = arg.slice('--users='.length);
    } else if (arg === '--duration-ms') {
      args.durationMs = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--duration-ms=')) {
      args.durationMs = arg.slice('--duration-ms='.length);
    } else if (arg === '--ramp-up-ms') {
      args.rampUpMs = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--ramp-up-ms=')) {
      args.rampUpMs = arg.slice('--ramp-up-ms='.length);
    } else if (arg === '--think-time-ms') {
      args.thinkTimeMs = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--think-time-ms=')) {
      args.thinkTimeMs = arg.slice('--think-time-ms='.length);
    } else if (arg === '--think-time-jitter-ms') {
      args.thinkTimeJitterMs = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--think-time-jitter-ms=')) {
      args.thinkTimeJitterMs = arg.slice('--think-time-jitter-ms='.length);
    } else if (!arg.startsWith('-') && !args.scenario) {
      args.scenario = arg;
    }
  }
  return args;
}

function applyScenarioOverrides(scenario, args) {
  applyNumberOverride(scenario, 'users', args.users);
  applyNumberOverride(scenario, 'durationMs', args.durationMs);
  applyNumberOverride(scenario, 'rampUpMs', args.rampUpMs);
  applyNumberOverride(scenario, 'thinkTimeMs', args.thinkTimeMs);
  applyNumberOverride(scenario, 'thinkTimeJitterMs', args.thinkTimeJitterMs);
}

function applyNumberOverride(target, key, rawValue) {
  if (rawValue === undefined || rawValue === '') {
    return;
  }
  const value = Number(rawValue);
  if (!Number.isFinite(value) || value < 0) {
    throw new Error(`Invalid numeric override for ${key}: ${rawValue}`);
  }
  target[key] = value;
}

function interpolate(value, vars) {
  if (typeof value === 'string') {
    return value.replace(/\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}/g, (_match, key) => vars[key] ?? '');
  }
  if (Array.isArray(value)) {
    return value.map(item => interpolate(item, vars));
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, interpolate(entry, vars)]));
  }
  return value;
}
