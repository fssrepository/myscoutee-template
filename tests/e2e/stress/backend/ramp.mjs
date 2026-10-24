#!/usr/bin/env node
import { fileURLToPath } from 'node:url';
import { runBackendRamp } from '@fssrepository/myscoutee-e2e/stress/backend/ramp.mjs';

await runBackendRamp({ repoRoot: fileURLToPath(new URL('../../../../', import.meta.url)), e2eRoot: fileURLToPath(new URL('../../', import.meta.url)) });
