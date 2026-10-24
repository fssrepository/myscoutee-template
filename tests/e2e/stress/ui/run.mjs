#!/usr/bin/env node
import { fileURLToPath } from 'node:url';
import { runUiStress } from '@fssrepository/myscoutee-e2e/stress/ui/run.mjs';

const repoRoot = fileURLToPath(new URL('../../../../', import.meta.url));
await runUiStress({ repoRoot, e2eRoot: fileURLToPath(new URL('../../', import.meta.url)), resetE2eDatabase: async () => {
    throw new Error('Template stress uses owned fixtures. Set E2E_SKIP_E2E_RESET=true.');
  }
});
