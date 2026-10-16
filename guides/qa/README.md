# Template QA

The canonical status table is [TEMPLATE_QA_MATRIX.csv](matrices/TEMPLATE_QA_MATRIX.csv).
It tracks the minimal product and the complete framework around it. The numbered
[implementation plan](../plans/MSC-122_TEMPLATE_IMPLEMENTATION_PLAN.md) remains the
implementation checklist. Repository text and test cases are in English.

The user has delegated automatic template testing. Execute the applicable API and
browser flows without asking for each step, and explicitly authorized **headless
Chrome** for this work. Prefer the native E2E runner in headless mode with isolated,
versioned fixtures. Retain the native fixture baseline and browser-launch
requirements when browser qualification becomes available. Keep only
one dev, E2E, KVM or production stack running at a time.

Previously qualified MyScoutee behavior is the comparison baseline. Its four
outstanding QA tasks remain unverified; do not silently count them as passed.
Before repairing a failed comparison, identify whether the fixture/runner,
template composition or upstream implementation is responsible.

## Status rules

- `PASS` means the stated case passed in that column's deployment and at the
  revision recorded in its evidence. It does not qualify adjacent functionality.
- `FAIL` means an executed case disagreed with its expected result.
- `NOT RUN` means the check remains outstanding. Missing implementation is not a pass.
- `N/A` means that deployment does not exercise the stated boundary.

Each new evidence record names the revision/file hashes, command, selected cases,
results and limits. Link defects and their verified fixes. Keep request captures
and session-bearing curl commands in ignored `.e2e-artifacts/`, not in Git.

## Checkpoints

1. Backend composition: startup, selected seeds, owned login/logout, profile,
   personal chat, documents, notification inbox, location and authority checks.
2. Stateful features: profile/avatar save and reload; two-user chat;
   consent, guide and notification state; account deletion and background jobs.
3. Frontend: landing/version badge, SmartList views, minimal menus, chat, profile
   and shared components in local and HTTP modes. Include responsive layout,
   reload, assets, console errors and persistence.
4. Integration and operations: API/MCP authorization and revocation, operator
   installation/configuration/TLS/update, dev frontend and frontend-sync.
5. Deployment and release: serial KVM/monolith checks, Prometheus targets and
   actual Grafana panel data, matched performance batch, production images,
   DEB on local VMware, backup/restore, signed update and Pages. Netcup access is prohibited.

## User handoff

Detailed qualification and defect diagnosis belong to the automatic QA above.
The user requested only the following final smoke checks in each named deployment;
do not hand over the remaining automated checklist as manual work.

| User action | VMware | Dev monolith | KVM |
| --- | --- | --- | --- |
| Log in as one prepared member | DONE (Codex, user delegated) | NOT RUN | NOT RUN |
| Open the two member pages: SmartList and chat | DONE (Codex, user delegated) | NOT RUN | NOT RUN |

Keep VMware and KVM results distinct. Prepare and verify each environment before
handoff. Identify the available native VMware installation/launch workflow during
the packaging inventory rather than treating a KVM result as VMware evidence.

## Standalone artifact E2E checkpoint — 2026-10-11

The seven-service disposable stack now passes 17 backend collection cases,
the selected admin mutation flow and all 15 HTTP browser flows. The five admin
guide contexts also pass, including their visible controls and mobile report
layout. The native source and template nginx image builds include the separated
components workspace inputs; the reconnect probe uses the actual browser
WebSocket and works in optimized output without Angular debug helpers.
See [E2E evidence](../qualification/evidence/MSC122_TEMPLATE_E2E_RUNTIME_20261011.json)
and [reproducible commands](../../e2e/README.md). The QA table has distinct E2E
API/UI columns. All 14 applicable local flows also pass after stopping E2E;
see [local suite evidence](../qualification/evidence/MSC122_TEMPLATE_LOCAL_UI_SUITE_20261011.json).
Provider, KVM, installed and matched-load gates remain separate.

## Initial backend collection

The template uses the unchanged native `e2e/shared` collection, HTTP, assertion,
WebSocket and owned-session helpers. Requests and seed expectations belong to the
template's `e2e/functional/backend` selection.

List the cases without starting a server or touching data:

```sh
node e2e/functional/backend/run.mjs --list
```

Against an already-running gateway with `/api`:

```sh
E2E_SERVER_BASE_URL=https://127.0.0.1 \
  node e2e/functional/backend/run.mjs --record
```

Use the deployment's trusted certificate with `NODE_EXTRA_CA_CERTS` where needed;
do not disable TLS verification. During backend composition, before the gateway
exists, the same runner can target the dev container directly:

```sh
E2E_SERVER_BASE_URL="http://$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' myscoutee-template-dev-profilemanagement):8080" \
E2E_API_PREFIX='' node e2e/functional/backend/run.mjs --record
```

Use `--case`, `--group` or `--qa-case` for a focused check. `--record` writes
responses under `.e2e-artifacts/functional/backend`; every executed run writes
its summary there. The runner registers and revokes only its own DEMO sessions.
Normal server login audits, security notifications and chat read receipts remain;
it does not reset existing databases or erase this evidence. These initial cases
are contract/session checks on the small template fixture, not full mutation,
browser, standalone E2E, KVM or load qualification.

Use the existing isolated JUnit command for focused implementation tests. Record
CPU and response time for coherent batches; repeat stress only for a defined
comparison or a measured regression.

## Entry browser checkpoint

The versioned `functional/ui/entry/assert.mjs` case uses the native isolated
Chrome helper. Until the complete UI collection is assembled in plan step 22,
run this focused case from the repository root:

```sh
E2E_UI_BASE_URL=http://localhost E2E_UI_MODE=full node --input-type=module <<'JS'
import { launchChromeBrowser } from './e2e/functional/ui/lib/chrome-cdp.mjs';
import { assertUi } from './e2e/functional/ui/entry/assert.mjs';
const browser = await launchChromeBrowser({
  baseUrl: process.env.E2E_UI_BASE_URL,
  ignoreCertificateErrors: process.env.E2E_ALLOW_SELF_SIGNED === 'true'
});
const page = await browser.newPage();
try {
  await assertUi({ page, ctx: { mode: process.env.E2E_UI_MODE } });
  console.log('PASS entry, public documents, consent, reload and logout');
} finally {
  await page.close();
  await browser.close();
}
JS
```

For the local frontend, use `E2E_UI_MODE=local` and the existing local serve URL.
Stop frontend-sync before starting that compiler; restart frontend-sync only
when the local compiler has stopped. For the development HTTPS listener only,
`E2E_ALLOW_SELF_SIGNED=true` explicitly enables the same disposable-browser
certificate allowance used by the native manual-QA launcher. It does not test
production certificate trust or alter host/browser trust outside that test.

The dev gateway images are built from the unchanged native Dockerfiles:

```sh
docker build -f server/docker/images/nginx/base/Dockerfile \
  -t myscoutee-template-nginx:1.0.0-base server/docker
docker compose --env-file server/docker-dev.env -f server/docker-compose-dev.yml \
  build nginx frontend-sync
docker compose --env-file server/docker-dev.env -f server/docker-compose-dev.yml up -d
```

Use `frontend/scripts/generate-i18n-mongo-seed.js` and
`frontend/scripts/audit-i18n-mongo-seed.js` after changing selected translations.
They preserve the native dated translation revision format, independently of
application version `1.0.0`. The native dev Mongo watcher imports changed seed
files; the localization change stream refreshes the API cache.

## Selected member flows

The standalone focused runners reuse the native disposable Chrome helper.
Use the existing frontend configuration and only one frontend compiler:

```sh
node e2e/functional/ui/chat/run-local.mjs
node e2e/functional/ui/profile-editor/run.mjs --local
node e2e/functional/ui/settings/run.mjs --local
node e2e/functional/ui/notifications/run.mjs --local
node e2e/functional/ui/guide/run.mjs --local
```

With the dev HTTP gateway, each following runner creates UUID-owned actors and
cleans its own state. Select the template Mongo container explicitly:

```sh
E2E_MONGO_CONTAINER=myscoutee-template-dev-mongodb E2E_DATABASE=demo_db node e2e/functional/ui/chat/run.mjs
E2E_MONGO_CONTAINER=myscoutee-template-dev-mongodb E2E_DATABASE=demo_db node e2e/functional/ui/profile-editor/run.mjs
E2E_MONGO_CONTAINER=myscoutee-template-dev-mongodb E2E_DATABASE=demo_db node e2e/functional/ui/settings/run.mjs
E2E_MONGO_CONTAINER=myscoutee-template-dev-mongodb E2E_DATABASE=demo_db node e2e/functional/ui/notifications/run.mjs
E2E_MONGO_CONTAINER=myscoutee-template-dev-mongodb E2E_DATABASE=demo_db node e2e/functional/ui/guide/run.mjs
```

The image runner derives the matching MinIO container from the Mongo name;
`E2E_MINIO_CONTAINER` may explicitly select another template container. It uses
the seed bootstrap's configured `mc` alias and removes only its unique actor's
image prefix. `E2E_SCREENSHOT_DIR` optionally captures desktop/mobile profile
images outside the repository. The functional image fixtures are selected native
E2E images, not template product branding.

The notification runner covers read/new/all state, mute/unmute and reload. HTTP
also checks a notification arriving while the inbox is closed at the native
30-second interval, then opens its personal chat and verifies the notification
read in MongoDB. The guide runner starts from a fresh public entry and completes
the retained landing/menu/profile/chat tours. The native guide persists its
enabled preference, not the currently selected step; no additional progress
storage is introduced. Desktop/mobile notification screenshots can be captured
with `E2E_SCREENSHOT_DIR`.


## Device setup and retained account entry — 2026-10-11

Frontend `3244e2e` adds the native permission/setup/location owner. The 182
frontend tests and local/HTTP browser runs cover nested help, the setup guide,
location save/reload and disabling tracking, delete/reactivate cancellation and
confirmation, and minimal onboarding/save/reload. The HTTP reactivation and
onboarding probes verify cleared purge tracking. The native location API case
passes at 9 ms. See [the evidence](../qualification/evidence/MSC122_TEMPLATE_SETUP_LIFECYCLE_20261011.json).

Run `e2e/functional/ui/settings/setup.mjs`, `settings/run.mjs` and
`settings/onboarding.mjs` with the same explicit template Mongo fixture inputs
as chat. Use `--local` for the sole local frontend runtime. Setup screenshots
are optional through `E2E_SCREENSHOT_DIR`. The HTTP onboarding fixture retains
an owned authenticated session because the native demo selector intentionally
omits onboarding profiles. It does not impersonate a real Firebase sign-up.
Actual provider/push delivery, installed PWA, KVM and matched load comparison
remain open. Only one frontend compiler and one application stack ran at a time.

## Member API and MCP connection settings — 2026-10-11

Frontend `7c9baee` selects the native settings/store/adapters and consent UI,
without affiliate or product community fields. Eighteen focused tests pass.
Local and dev HTTP browser checks cover key/client creation, one-time secrets,
masked reload, staged permissions, persistence and revocation. HTTP OAuth checks
also cover blocked access, cancellation, login return, explicit approval, PKCE,
single-use code and revocation. This qualifies the browser/backend authorization
boundary, not a complete external ChatGPT/MCP connection.
See [the evidence](../qualification/evidence/MSC122_TEMPLATE_API_MCP_UI_20261011.json).

Run `e2e/functional/ui/integrations/run.mjs` and `integrations/guide.mjs` with
`--local` or the explicit template Mongo fixture variables documented above.
`integrations/authorization.mjs` requires HTTP and starts only a temporary
loopback OAuth callback, not another application stack. Fixture keys, grants,
registered clients and actors are removed after the run. The guide runner can
save desktop/mobile screenshots through `E2E_SCREENSHOT_DIR`; it creates no keys.
Native frontend `c90a5b1f` also repairs the permission button's zero-sized guide
anchor with one CSS declaration, verified using the selected component.

## Operator configuration checkpoint

Frontend `6e532c1` selects native configuration controls without Registry/payment
state. Twenty focused tests and local/dev HTTP browser save/reload, authority and
seven-step guide checks pass. See [operator configuration evidence](../qualification/evidence/MSC122_TEMPLATE_OPERATOR_CONFIGURATION_20261011.json).
The fixtures retain and restore the existing deployment configuration through its
owner, then restore the exact Mongo snapshot and remove their owned sessions.
TLS/update/host workers and live Firebase are separate pending qualifications.

```sh
node e2e/functional/ui/operator/configuration.mjs --local
E2E_MONGO_CONTAINER=myscoutee-template-dev-mongodb E2E_DATABASE=demo_db \
  node e2e/functional/ui/operator/configuration.mjs
```

The TLS extension at frontend `800ff16` also passes local save/reopen/disable and
dev HTTP host-capability denial. Its backend selection has 36 focused passing
Java checks; 16 TLS/configuration frontend tests pass. See [TLS/update selection](../qualification/evidence/MSC122_TEMPLATE_TLS_UPDATE_SELECTION_20261011.json).
The backend batch still requires KVM and real host-worker qualification.

## Operator update UI checkpoint

Frontend `4b13d99` passes 41 focused tests across HTTP progress/recovery,
workspace completion/cache behavior and persisted local simulation. Local and dev
HTTP browser checks cover explicit discovery, version display, guides, notification
target and logout. The local test stages a release only in its disposable browser,
then verifies simulated installation and the native PWA reload. Actual host install
and rollback remain open. See [update UI evidence](../qualification/evidence/MSC122_TEMPLATE_UPDATE_UI_20261011.json).

```sh
node e2e/functional/ui/operator/updates.mjs --local
E2E_MONGO_CONTAINER=myscoutee-template-dev-mongodb E2E_DATABASE=demo_db \
  node e2e/functional/ui/operator/updates.mjs
```

## Omitted admin features and retained API — 2026-10-12

The template omits Reports, Feedback, moderation commands and support-chat
creation/state changes from both frontend adapters and the backend. MyScoutee
retains these features after its separately qualified dependency extraction.
Normal two-member chat, account status/lifecycle handling, notifications, guide
and administrator API/MCP remain. Shared conversation/model shapes are preserved;
there is no filtering of existing conversations because their creator is absent.

Run the absence/retained-feature check against the already-running artifact stack:

```sh
NODE_EXTRA_CA_CERTS=/tmp/myscoutee-template-e2e-nginx-ca.pem \
E2E_SERVER_BASE_URL=https://127.0.0.1 E2E_SERVER_MODE=e2e \
E2E_MONGO_CONTAINER=myscoutee-template-e2e-mongodb E2E_DATABASE=e2e_db \
  node e2e/functional/backend/admin/run.mjs
```

The runner verifies nine removed routes return 404, the admin API is available
only to the appropriate role, ordinary messages persist and both participants
can read them, and report/feedback collections are not initialized. It creates
only owned accounts and a normal chat fixture, restores operator configuration,
and closes its own sessions and data. It does not create removed feature stores.

`e2e/functional/ui/run.mjs --case admin` checks the empty shell, omitted menus and
old targets, normal chat persistence, and API-key creation/masking/block/revocation.
Use `--local` for the same frontend checks with local persistence after stopping
the backend stack. Also run `--case chat/lifecycle` and `--case guide/member` at
both boundaries. The API/MCP tests require retained attention fields and explicitly
reject report/feedback/support counters and omitted moderation event advertisement.

See [removal evidence](../qualification/evidence/MSC122_TEMPLATE_ADMIN_FEATURE_REMOVAL_20261012.json).
Template KVM, installed artifacts, external provider callbacks and live Firebase
role qualification remain separate gates. The previous
[backend](../qualification/evidence/MSC122_TEMPLATE_ADMIN_BACKEND_20261011.json),
[admin UI](../qualification/evidence/MSC122_TEMPLATE_ADMIN_UI_20261011.json) and
[first UI removal](../qualification/evidence/MSC122_TEMPLATE_MINIMAL_ADMIN_UI_20261012.json)
records are historical; they do not describe the final backend selection.

## Upstream central Feedback review — 2026-10-15

The upstream central Feedback domain, evaluation forms, collections, product
translations and transport adapters are N/A to the minimal template: those
product capabilities are intentionally excluded. This review adds no template
runtime PASS and starts no template stack. Shared guide-introduction behavior
remains owned by T-CONTENT-002; record any applicable MSC-116 introduction fixes
there for later template verification.

MSC-116 introduction correction: `guide-options.ts` now gives the temporary
landing.guide context priority over the pending consent popup. T-CONTENT-002
must verify a fresh visitor sees the launcher introduction (1/1), not the consent
tour; all dismissal routes preserve pending consent and do not repeat after
reload. Template runtime verification is NOT RUN.

## Collected upstream regression

The existing [MSC115-TEMPLATE-REGRESSION checklist](checklists/general/MSC115-TEMPLATE-REGRESSION.md) and [record](evidence/general/MSC115-TEMPLATE-REGRESSION.json) now belong to this QA ledger. Moved from MyScoutee on 2026-10-16; execution remains NOT RUN. No template test is authorized by this move.
