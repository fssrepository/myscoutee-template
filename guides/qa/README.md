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
2. Stateful features: profile/avatar save and reload; two-user chat and support;
   consent, guide and notification state; account deletion and background jobs.
3. Frontend: landing/version badge, SmartList views, minimal menus, chat, profile
   and shared components in local and HTTP modes. Include responsive layout,
   reload, assets, console errors and persistence.
4. Integration and operations: API/MCP authorization and revocation, operator
   installation/configuration/TLS/update, dev frontend and frontend-sync.
5. Deployment and release: serial KVM/monolith checks, Prometheus targets and
   actual Grafana panel data, matched performance batch, production images,
   DEB/Netcup, backup/restore, signed update and Pages.

## User handoff

Detailed qualification and defect diagnosis belong to the automatic QA above.
The user requested only the following final smoke checks in each named deployment;
do not hand over the remaining automated checklist as manual work.

| User action | VMware | Dev monolith | KVM |
| --- | --- | --- | --- |
| Log in as one prepared member | NOT RUN | NOT RUN | NOT RUN |
| Open the two member pages: SmartList and chat | NOT RUN | NOT RUN | NOT RUN |

Keep VMware and KVM results distinct. Prepare and verify each environment before
handoff. Identify the available native VMware installation/launch workflow during
the packaging inventory rather than treating a KVM result as VMware evidence.

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
