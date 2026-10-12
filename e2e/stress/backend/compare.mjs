#!/usr/bin/env node
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';

const args = parseArgs(process.argv.slice(2));

if (args.help || args.inputs.length < 2) {
  printHelp();
  process.exit(args.help ? 0 : 1);
}

const baseline = await readSummary(args.inputs[0]);
const candidate = await readSummary(args.inputs[1]);

printComparison(baseline, candidate);

async function readSummary(input) {
  const resolved = path.resolve(input);
  const summaryPath = (await stat(resolved)).isDirectory()
    ? path.join(resolved, 'summary.json')
    : resolved;
  const payload = JSON.parse(await readFile(summaryPath, 'utf8'));
  return {
    path: summaryPath,
    payload,
    scenario: payload.scenario ?? {},
    summary: payload.summary ?? payload,
    resourceSummary: payload.resourceSummary ?? {}
  };
}

function printComparison(baseline, candidate) {
  console.log(`# Stress Comparison\n`);
  console.log(`- Baseline: \`${baseline.scenario.id ?? 'unknown'}\` from \`${baseline.path}\``);
  console.log(`- Candidate: \`${candidate.scenario.id ?? 'unknown'}\` from \`${candidate.path}\``);
  console.log('');

  const rows = [
    metricRow('Requests', baseline.summary.total, candidate.summary.total, formatNumber, 'neutral'),
    metricRow('Error rate', baseline.summary.errorRate, candidate.summary.errorRate, formatPercent, 'lower'),
    metricRow('Req/s', baseline.summary.requestsPerSec, candidate.summary.requestsPerSec, formatRate, 'higher'),
    metricRow('Passed req/s', baseline.summary.passedPerSec, candidate.summary.passedPerSec, formatRate, 'higher'),
    metricRow('Avg latency', baseline.summary.avgMs, candidate.summary.avgMs, formatMs, 'lower'),
    metricRow('p50 latency', baseline.summary.p50Ms, candidate.summary.p50Ms, formatMs, 'lower'),
    metricRow('p95 latency', baseline.summary.p95Ms, candidate.summary.p95Ms, formatMs, 'lower'),
    metricRow('p99 latency', baseline.summary.p99Ms, candidate.summary.p99Ms, formatMs, 'lower'),
    metricRow('Max latency', baseline.summary.maxMs, candidate.summary.maxMs, formatMs, 'lower'),
    metricRow('Response bytes', baseline.summary.responseBytes, candidate.summary.responseBytes, formatBytes, 'neutral'),
    metricRow('Host peak memory', baseline.resourceSummary.host?.maxUsedMemBytes, candidate.resourceSummary.host?.maxUsedMemBytes, formatBytes, 'lower'),
    metricRow('Host peak load1', baseline.resourceSummary.host?.maxLoadAvg1, candidate.resourceSummary.host?.maxLoadAvg1, formatNumber, 'lower')
  ];

  printTable(
    ['Metric', 'Baseline', 'Candidate', 'Delta', 'Change'],
    rows.map(row => [row.name, row.before, row.after, row.delta, row.change])
  );

  const baselineSteps = new Map((baseline.summary.byStep ?? []).map(item => [item.step, item]));
  const candidateSteps = new Map((candidate.summary.byStep ?? []).map(item => [item.step, item]));
  const stepNames = [...new Set([...baselineSteps.keys(), ...candidateSteps.keys()])]
    .sort((left, right) => (candidateSteps.get(right)?.p95Ms ?? -1) - (candidateSteps.get(left)?.p95Ms ?? -1));

  if (stepNames.length) {
    console.log('\n## Step Deltas\n');
    printTable(
      ['Step', 'Base p95', 'Cand p95', 'p95 Delta', 'p95 Change', 'Base Req/s', 'Cand Req/s', 'Base Error', 'Cand Error'],
      stepNames.map(stepName => {
        const before = baselineSteps.get(stepName);
        const after = candidateSteps.get(stepName);
        const p95 = metricDelta(before?.p95Ms, after?.p95Ms, formatMs, 'lower');
        return [
          stepName,
          before ? formatMs(before.p95Ms) : '-',
          after ? formatMs(after.p95Ms) : '-',
          p95.delta,
          p95.change,
          before ? formatRate(before.requestsPerSec) : '-',
          after ? formatRate(after.requestsPerSec) : '-',
          before ? formatPercent(before.errorRate) : '-',
          after ? formatPercent(after.errorRate) : '-'
        ];
      })
    );
  }
}

function metricRow(name, before, after, formatter, direction) {
  const delta = metricDelta(before, after, formatter, direction);
  return {
    name,
    before: isFiniteNumber(before) ? formatter(before) : '-',
    after: isFiniteNumber(after) ? formatter(after) : '-',
    delta: delta.delta,
    change: delta.change
  };
}

function metricDelta(before, after, formatter, direction) {
  if (!isFiniteNumber(before) || !isFiniteNumber(after)) {
    return { delta: '-', change: '-' };
  }
  const diff = after - before;
  const percent = before === 0 ? 0 : (diff / before) * 100;
  const marker = direction === 'higher'
    ? diff > 0 ? 'better' : diff < 0 ? 'worse' : 'same'
    : direction === 'lower'
      ? diff < 0 ? 'better' : diff > 0 ? 'worse' : 'same'
      : 'neutral';
  const sign = diff > 0 ? '+' : diff < 0 ? '-' : '';
  return {
    delta: `${sign}${formatter(Math.abs(diff))}`,
    change: `${percent >= 0 ? '+' : ''}${percent.toFixed(1)}% ${marker}`
  };
}

function printTable(headers, rows) {
  console.log(`| ${headers.map(escapeTable).join(' | ')} |`);
  console.log(`| ${headers.map(() => '---').join(' | ')} |`);
  for (const row of rows) {
    console.log(`| ${row.map(value => escapeTable(value ?? '-')).join(' | ')} |`);
  }
}

function formatMs(value) {
  return `${Math.round(Number(value))}ms`;
}

function formatRate(value) {
  return `${Number(value).toFixed(2)}`;
}

function formatNumber(value) {
  return Number(value).toFixed(Number.isInteger(Number(value)) ? 0 : 2);
}

function formatPercent(value) {
  return `${(Number(value) * 100).toFixed(2)}%`;
}

function formatBytes(bytes) {
  if (!Number.isFinite(Number(bytes)) || Number(bytes) <= 0) {
    return '0 B';
  }
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
  let value = Number(bytes);
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function isFiniteNumber(value) {
  return Number.isFinite(Number(value));
}

function escapeTable(value) {
  return `${value}`.replace(/\|/g, '\\|').replace(/\n/g, ' ');
}

function parseArgs(argv) {
  const parsed = { help: false, inputs: [] };
  for (const arg of argv) {
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
    } else {
      parsed.inputs.push(arg);
    }
  }
  return parsed;
}

function printHelp() {
  console.log(`Usage:
  node e2e/stress/backend/compare.mjs BASELINE_SUMMARY_OR_DIR CANDIDATE_SUMMARY_OR_DIR

Examples:
  node e2e/stress/backend/compare.mjs .e2e-artifacts/stress/backend/server-events-read-old .e2e-artifacts/stress/backend/server-events-read-new
  node e2e/stress/backend/compare.mjs baseline/summary.json candidate/summary.json`);
}
