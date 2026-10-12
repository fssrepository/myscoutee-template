#!/usr/bin/env node
import { execFile } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const execFileAsync = promisify(execFile);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..', '..');
const args = parseArgs(process.argv.slice(2));

const scenario = args.scenario || 'server-events-read';
const users = parseUsers(args.users || '4,8,16,32');
const startedAtIso = new Date().toISOString();
const artifactRoot = path.resolve(
  args.artifactsDir || path.join(repoRoot, '.e2e-artifacts', 'stress-ramp', 'backend', `${scenario}-${startedAtIso.replace(/[:.]/g, '-')}`)
);

await mkdir(artifactRoot, { recursive: true });

const runs = [];
for (const userCount of users) {
  console.log(`\n== ramp ${scenario}: users=${userCount} ==`);
  const runArgs = [
    path.join('e2e', 'stress', 'backend', 'run.mjs'),
    '--scenario',
    scenario,
    '--record',
    '--artifacts-dir',
    artifactRoot,
    '--users',
    String(userCount)
  ];
  appendOption(runArgs, '--duration-ms', args.durationMs);
  appendOption(runArgs, '--qa-case', args.qaCase);
  appendOption(runArgs, '--ramp-up-ms', args.rampUpMs);
  appendOption(runArgs, '--think-time-ms', args.thinkTimeMs);
  appendOption(runArgs, '--think-time-jitter-ms', args.thinkTimeJitterMs);

  let stdout = '';
  let stderr = '';
  let exitCode = 0;
  try {
    const result = await execFileAsync('node', runArgs, {
      cwd: repoRoot,
      maxBuffer: 1024 * 1024 * 24,
      env: process.env
    });
    stdout = result.stdout;
    stderr = result.stderr;
  } catch (error) {
    stdout = error.stdout ?? '';
    stderr = error.stderr ?? '';
    exitCode = Number(error.code ?? 1);
  }
  process.stdout.write(stdout);
  process.stderr.write(stderr);

  const runDir = extractArtifactDir(stdout);
  const summary = runDir
    ? JSON.parse(await readFile(path.join(runDir, 'summary.json'), 'utf8'))
    : null;
  runs.push({ userCount, exitCode, runDir, summary });
}

const report = buildReport({ scenario, startedAtIso, artifactRoot, runs });
await writeFile(path.join(artifactRoot, 'ramp-summary.json'), `${JSON.stringify({ scenario, startedAtIso, runs }, null, 2)}\n`);
await writeFile(path.join(artifactRoot, 'ramp-summary.md'), report);
console.log(`\nramp artifacts: ${artifactRoot}`);
console.log(report);

function appendOption(argv, name, value) {
  if (value !== undefined && value !== '') {
    argv.push(name, String(value));
  }
}

function extractArtifactDir(stdout) {
  const match = `${stdout ?? ''}`.match(/artifacts:\s*(.+)\s*$/m);
  return match ? match[1].trim() : '';
}

function buildReport({ scenario, startedAtIso, artifactRoot, runs }) {
  const lines = [
    `# Stress Ramp: ${scenario}`,
    '',
    `- Started: ${startedAtIso}`,
    `- Artifacts: \`${artifactRoot}\``,
    '',
    '| Users | Exit | Req/s | Error | p50 | p95 | p99 | Max | Host Load | Host Memory | Worst Step | Artifact |',
    '| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |'
  ];
  for (const run of runs) {
    const summary = run.summary;
    if (!summary) {
      lines.push(`| ${run.userCount} | ${run.exitCode} | - | - | - | - | - | - | - | - | missing summary | - |`);
      continue;
    }
    const item = summary.summary;
    const resources = summary.resourceSummary ?? {};
    const host = resources.host ?? {};
    const worstStep = [...(item.byStep ?? [])].sort((left, right) => Number(right.p95Ms ?? 0) - Number(left.p95Ms ?? 0))[0];
    lines.push([
      `| ${run.userCount}`,
      run.exitCode,
      number(item.requestsPerSec),
      percent(item.errorRate),
      ms(item.p50Ms),
      ms(item.p95Ms),
      ms(item.p99Ms),
      ms(item.maxMs),
      number(host.maxLoadAvg1),
      bytes(host.maxUsedMemBytes),
      worstStep ? `${worstStep.step} p95 ${ms(worstStep.p95Ms)}` : '-',
      run.runDir ? `\`${path.basename(run.runDir)}\`` : '-',
      '|'
    ].join(' | '));
  }
  lines.push('', '## Step Detail', '');
  for (const run of runs) {
    const summary = run.summary?.summary;
    if (!summary) {
      continue;
    }
    lines.push(`### Users ${run.userCount}`, '');
    lines.push('| Step | Req/s | Error | p95 | p99 | Max |');
    lines.push('| --- | ---: | ---: | ---: | ---: | ---: |');
    for (const step of summary.byStep ?? []) {
      lines.push(`| ${escapeTable(step.step)} | ${number(step.requestsPerSec)} | ${percent(step.errorRate)} | ${ms(step.p95Ms)} | ${ms(step.p99Ms)} | ${ms(step.maxMs)} |`);
    }
    lines.push('');
  }
  return `${lines.join('\n')}\n`;
}

function number(value) {
  return Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '-';
}

function percent(value) {
  return Number.isFinite(Number(value)) ? `${(Number(value) * 100).toFixed(2)}%` : '-';
}

function ms(value) {
  return Number.isFinite(Number(value)) ? `${Math.round(Number(value))}ms` : '-';
}

function bytes(value) {
  const bytesValue = Number(value);
  if (!Number.isFinite(bytesValue) || bytesValue <= 0) {
    return '-';
  }
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
  let amount = bytesValue;
  let unitIndex = 0;
  while (amount >= 1024 && unitIndex < units.length - 1) {
    amount /= 1024;
    unitIndex += 1;
  }
  return `${amount.toFixed(amount >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function escapeTable(value) {
  return `${value ?? ''}`.replace(/\|/g, '\\|').replace(/\n/g, ' ');
}

function parseUsers(value) {
  const parsed = `${value ?? ''}`
    .split(',')
    .map(item => Number(item.trim()))
    .filter(item => Number.isInteger(item) && item > 0);
  if (parsed.length === 0) {
    throw new Error('Ramp requires at least one positive integer user count');
  }
  return parsed;
}

function parseArgs(argv) {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--scenario') {
      parsed.scenario = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--scenario=')) {
      parsed.scenario = arg.slice('--scenario='.length);
    } else if (arg === '--qa-case') {
      parsed.qaCase = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--qa-case=')) {
      parsed.qaCase = arg.slice('--qa-case='.length);
    } else if (arg === '--users') {
      parsed.users = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--users=')) {
      parsed.users = arg.slice('--users='.length);
    } else if (arg === '--duration-ms') {
      parsed.durationMs = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--duration-ms=')) {
      parsed.durationMs = arg.slice('--duration-ms='.length);
    } else if (arg === '--ramp-up-ms') {
      parsed.rampUpMs = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--ramp-up-ms=')) {
      parsed.rampUpMs = arg.slice('--ramp-up-ms='.length);
    } else if (arg === '--think-time-ms') {
      parsed.thinkTimeMs = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--think-time-ms=')) {
      parsed.thinkTimeMs = arg.slice('--think-time-ms='.length);
    } else if (arg === '--think-time-jitter-ms') {
      parsed.thinkTimeJitterMs = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--think-time-jitter-ms=')) {
      parsed.thinkTimeJitterMs = arg.slice('--think-time-jitter-ms='.length);
    } else if (arg === '--artifacts-dir') {
      parsed.artifactsDir = argv[index + 1] ?? '';
      index += 1;
    } else if (arg.startsWith('--artifacts-dir=')) {
      parsed.artifactsDir = arg.slice('--artifacts-dir='.length);
    } else if (!arg.startsWith('-') && !parsed.scenario) {
      parsed.scenario = arg;
    }
  }
  return parsed;
}
