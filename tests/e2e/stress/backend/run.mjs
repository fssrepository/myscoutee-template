#!/usr/bin/env node
import { fileURLToPath } from 'node:url';
import { runBackendStress } from '@fssrepository/myscoutee-e2e/stress/backend/run.mjs';
import { createContext as createProjectContext, sessionActors } from './context.mjs';

const repoRoot = fileURLToPath(new URL('../../../../', import.meta.url));
await runBackendStress({ repoRoot, e2eRoot: fileURLToPath(new URL('../../', import.meta.url)), createProjectContext, sessionActors,
  resetE2eDatabase: async () => {
    throw new Error('Template stress uses owned fixtures. Set E2E_SKIP_E2E_RESET=true.');
  }
});
