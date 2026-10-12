import { assertUi as entry } from '../entry/assert.mjs';
import { assertShowcase } from '../showcase/assert.mjs';
import { stageChatFixture } from '../chat/fixture.mjs';
export async function assertUi({ page }) {
  const fixture = await stageChatFixture();
  try {
    await entry({ page, local: false, actorId: fixture.ids[0], actorName: fixture.names[0],
      memberReady: () => assertShowcase({ page, local: false }) });
  } finally { await fixture.close(); }
}
