#!/usr/bin/env node

import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptDirectory, '..', '..');
const frontendPackage = path.join(repositoryRoot, 'frontend', 'package.json');
const requireFromFrontend = createRequire(frontendPackage);

const catalog = JSON.parse(await fs.readFile(path.join(scriptDirectory, 'manuals.json'), 'utf8'));
const manuals = catalog.manuals;
const selectedDocumentId = process.argv[2]?.trim();
const manualsToBuild = selectedDocumentId
  ? manuals.filter(manual => manual.documentId === selectedDocumentId)
  : manuals;
if (manualsToBuild.length === 0) {
  throw new Error(`Unknown document ID: ${selectedDocumentId}`);
}

let chromium;
try {
  ({ chromium } = requireFromFrontend('playwright'));
} catch (error) {
  throw new Error(
    'The frontend development dependencies are required to generate the PDFs. '
      + 'Run this first: (cd frontend && npm ci)',
    { cause: error }
  );
}

const browserExecutable =
  process.env.DOCS_CHROME_PATH
  || (await firstExistingPath([
    '/usr/bin/google-chrome',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser'
  ]));

const launchOptions = {
  headless: true,
  args: ['--disable-dev-shm-usage', '--font-render-hinting=none']
};
if (browserExecutable) {
  launchOptions.executablePath = browserExecutable;
}
if (process.getuid?.() === 0) {
  launchOptions.args.push('--no-sandbox');
}

const browser = await chromium.launch(launchOptions);
const generatedManuals = [];
try {
  for (const manual of manualsToBuild) {
    const sourcePath = path.join(scriptDirectory, manual.source);
    const outputPath = path.join(scriptDirectory, manual.output);
    await fs.access(sourcePath);

    const page = await browser.newPage({
      locale: 'en-GB',
      colorScheme: 'light',
      viewport: { width: 1280, height: 900 }
    });
    await page.goto(pathToFileURL(sourcePath).href, { waitUntil: 'networkidle' });
    await page.emulateMedia({ media: 'print' });
    await page.evaluate(async () => {
      await document.fonts.ready;
      for (const image of document.images) {
        if (!image.complete) {
          await new Promise((resolve, reject) => {
            image.addEventListener('load', resolve, { once: true });
            image.addEventListener('error', reject, { once: true });
          });
        }
      }
    });
    const sourceErrors = await page.evaluate(({ documentId, documentVersion, releaseDate }) => {
      const errors = [];
      const ids = [...document.querySelectorAll('[id]')].map(element => element.id);
      const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
      const missingTargets = [...document.querySelectorAll('a[href^="#"]')]
        .map(anchor => anchor.getAttribute('href')?.slice(1) ?? '')
        .filter(target => target && !document.getElementById(decodeURIComponent(target)));
      const bodyText = document.body.textContent ?? '';

      if (document.documentElement.lang !== 'en') {
        errors.push('the root lang attribute is not en');
      }
      if (!document.title.trim()) {
        errors.push('the HTML document title is missing');
      }
      for (const selector of ['article.manual', 'section.cover', '.document-control', '.toc']) {
        if (!document.querySelector(selector)) {
          errors.push(`required element is missing: ${selector}`);
        }
      }
      for (const requiredText of [
        documentId,
        'Document version',
        documentVersion ?? '1.0.0',
        'Released',
        releaseDate ?? '2026-07-30'
      ]) {
        if (!bodyText.includes(requiredText)) {
          errors.push(`required document metadata is missing: ${requiredText}`);
        }
      }
      if (duplicateIds.length > 0) {
        errors.push(`duplicate HTML id: ${duplicateIds.join(', ')}`);
      }
      if (missingTargets.length > 0) {
        errors.push(`missing internal link target: ${[...new Set(missingTargets)].join(', ')}`);
      }
      return errors;
    }, manual);
    if (sourceErrors.length > 0) {
      throw new Error(`${manual.source} is invalid:\n- ${sourceErrors.join('\n- ')}`);
    }

    await page.pdf({
      path: outputPath,
      format: 'A4',
      preferCSSPageSize: true,
      printBackground: true,
      displayHeaderFooter: true,
      tagged: true,
      outline: true,
      headerTemplate: `
        <div style="box-sizing:border-box;color:#5b6773;font-family:Arial,sans-serif;
                    font-size:7.5px;padding:0 16mm;width:100%;">
          <span style="float:left;">${escapeHtml(catalog.brandName)} · ${escapeHtml(manual.title)}</span>
          <span style="float:right;">${manual.documentId} · document version ${manual.documentVersion ?? '1.0.0'}</span>
        </div>`,
      footerTemplate: `
        <div style="box-sizing:border-box;color:#5b6773;font-family:Arial,sans-serif;
                    font-size:7.5px;padding:0 16mm;width:100%;">
          <span style="float:left;">Released · ${manual.releaseDate ?? '2026-07-30'} · EN</span>
          <span style="float:right;">Page <span class="pageNumber"></span> of <span class="totalPages"></span></span>
        </div>`
    });
    await page.close();

    const signature = await readPdfSignature(outputPath);
    if (signature !== '%PDF-') {
      throw new Error(`The generated file is not a PDF: ${outputPath}`);
    }
    const outputStats = await fs.stat(outputPath);
    if (outputStats.size < 10_000) {
      throw new Error(`The generated PDF is suspiciously small: ${outputPath}`);
    }
    generatedManuals.push({ ...manual, outputPath });
    process.stdout.write(`Generated: ${path.relative(repositoryRoot, outputPath)}\n`);
  }
} finally {
  await browser.close();
}

const publicManualDirectory = path.join(repositoryRoot, 'frontend', 'guides', 'manuals');
if (catalog.publishToFrontend) await fs.mkdir(publicManualDirectory, { recursive: true });
for (const manual of catalog.publishToFrontend ? generatedManuals : []) {
  const publicPath = path.join(publicManualDirectory, manual.output);
  await fs.copyFile(manual.outputPath, publicPath);
  process.stdout.write(`Published: ${path.relative(repositoryRoot, publicPath)}\n`);
}

async function firstExistingPath(candidates) {
  for (const candidate of candidates) {
    try {
      await fs.access(candidate);
      return candidate;
    } catch {
      // Check the next known browser path.
    }
  }
  return null;
}

async function readPdfSignature(filePath) {
  const handle = await fs.open(filePath, 'r');
  try {
    const buffer = Buffer.alloc(5);
    await handle.read(buffer, 0, buffer.length, 0);
    return buffer.toString('ascii');
  } finally {
    await handle.close();
  }
}

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
