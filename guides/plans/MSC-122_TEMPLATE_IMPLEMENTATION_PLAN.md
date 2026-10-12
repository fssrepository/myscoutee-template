# MSC-122 — Complete Minimal Template Implementation Plan

Updated: 2026-10-12. Release version: **1.4.0** (aligned with native MyScoutee by user instruction).

This is the active, numbered implementation checklist for `myscoutee-template`.
It continues the source-separation work documented in
[`myscoutee-backend`](../../../myscoutee-backend/guides/plans/MSC-122_TEMPLATE_EXTRACTION_PLAN.md).
Update this file as work progresses; keep detailed test results in
[`../qualification/evidence`](../qualification/evidence).

## Live execution pointer — 2026-10-12, 11:40 CEST

**Current numbered gate: 14 — frontend foundation and HTTP/local behavior.**
The retained UI corrections also exercise steps 15–20. This is not a claim that
all earlier numbered requirements or all of steps 15–20 are complete. Every
numbered item and its existing subrequirements below remain in force.

| Order now authorized by the user | Exact current status |
| --- | --- |
| 14, with retained surfaces from 15–20 | DEV/HTTP loading checks passed for member/admin/operator profiles and saves; chat uses the native line indicator. Landing layout passed at 320/393/768/1440 px. Automatic local failover, lists, chat and persisted local messages passed. The expanded local role-change/reload check exposed a bootstrap-mode defect; repair and retest remain open. |
| 21–24 | Next: selected tests, mini functional E2E and matched stress measurement on the corrected source. Earlier recorded PASS results remain historical and do not qualify this new batch. |
| 25 | Then repeat KVM qualification, serially after stopping the monolith. |
| 26 | Verify the matching production Docker artifacts before installed testing. |
| 27 | VMware is deferred until the user starts the guest after the KVM repeat. Do not reinstall DEBs during the current DEV correction pass. |
| 28–31 | Signed release, publication, documentation/PDF and final acceptance remain open. Final Git squashing/tagging uses `[MSC-122]` and force-with-lease as authorized; DEBs are uploaded by the user. |

This pointer changes execution order only. No requirement in steps 1–31 is
removed, silently marked passed, or replaced by this summary. Read each item's
remaining gates and its linked evidence when reaching it.

## Delivery contract

Deliver a complete, standalone, minimal counterpart of `myscoutee-backend` from
which a developer can build a different product. Preserve the existing project
structure, development modes, persistence, testing, deployment and release
workflows. Reduce the initial application features and their dependency closure.
The lists below are not permission to omit an unnamed dependency needed to make
the retained features work.

Use the completed domain separation and existing implementations. Select files
and their required dependencies; do not clone the entire MyScoutee application,
invent another framework, or reopen broad source refactors. Record selections
and necessary adaptations in [`tools/upstream-sources.json`](../../tools/upstream-sources.json).
If a concrete source defect blocks a retained flow, repair that owner narrowly
and qualify the affected source batch before carrying it into the template.

**Shared infrastructure before copying:** inspect `packaging/`, `tools/`, runtime
scripts and deployment/build helpers for mixed reusable logic and MyScoutee-specific
configuration. Where needed, separate those concerns in `myscoutee-backend` first,
using existing configuration/manifest/extension points. Verify that MyScoutee keeps
its behavior and performance, then copy the common implementation unchanged into
the template with only its own required configuration/data. Prefer this over
maintaining a large independently edited script variant. This applies to already
staged template adaptations as well: review them before finalizing the batch.
Do not introduce a general framework or refactor unrelated helpers merely for
uniformity; each extraction must remove a concrete obstacle to direct reuse.

Retain the required native model and storage contracts. Omit unneeded model
types rather than importing events, groups, payments or affiliate registration
to satisfy a type reference. A small profile DTO/form may expose fewer fields;
saving it must preserve stored fields that it does not edit. Keep the explicitly
selected `integrationAccess` contract without the affiliate product.

`frontend` remains a local Git submodule for now. Its `components` submodule
uses the existing remote component repository. The template has its own state,
release identity, images and installation; it must not adopt MyScoutee's VM,
databases, secrets, volumes or deployment directories.

Required environment coverage:

| Existing mode or workflow | Required template behavior |
| --- | --- |
| Frontend `local` | Native local adapters, IndexedDB persistence and a small coherent local seed; retained flows work without the Java backend |
| Frontend `http` | Native API adapters and session handling; retained flows use the backend rather than silently falling back to local data |
| Development | Existing dev Compose, hot reload, frontend/backend watchers, seed synchronization and development tools for retained services |
| E2E | Existing standalone, disposable production-style stack, separate physical E2E fixtures, backend and browser runners |
| Production | Built frontend/backend artifacts, production security and secrets, durable data and background work; separate REAL and Explore/DEMO state |
| Microservices / KVM | Existing assembly, transport, deployment, health and measurement tools; Prometheus and Grafana with native metrics/dashboard/access wiring; only the retained application owners and required supporting services |
| Installed release | Docker images, signed DEB/update metadata, host helpers, local VMware Debian setup, backup/restore and upgrade verification |
| GitHub Pages | Native static/local frontend build, correct base path, deep links, assets and visible version; no dependency on a private backend |

### Frontend environment inventory

Checked against the source frontend's `src/environments`, `angular.json` and
`package.json`. Preserve this existing configuration matrix for retained features;
`local`/`http` are data-source modes, not replacements for the build environments.

| Angular configuration | Environment source | Data source | Build output | Service worker | Firebase login flag |
| --- | --- | --- | --- | --- | --- |
| `local` | `environment.ts` | `local` | `local/` | disabled | disabled |
| `dev` | `environment.dev.ts` | `http` | `dev/` | enabled | enabled |
| `e2e` | `environment.e2e.ts` | `http` | `e2e/` | disabled | disabled |
| `production` | `environment.production.ts` | `http` | `prod/` | enabled | enabled |
| `github` | `environment.github.ts` | `local` | `docs/` | enabled | disabled |

These are the existing build flags; actual authenticated/deployment capability
must still follow the native runtime configuration and REAL/DEMO session rules.
Retain the `/api` configuration, relevant messaging/bootstrap settings and the
native environment type contract. Omit Registry/payment settings together with
their absent consumers rather than leaving misleading inactive configuration.

Step 14 must carry the corresponding `fileReplacements`, serve targets,
`production-serve` alias, `unit-test` build configuration, asset handling,
optimization/source-map settings and relevant package scripts. Keep the existing
`start`/`build` local default, `watch` dev default, mode-specific start/build/watch
commands, component build integration and build-log helpers where required.
Steps 21–23 verify tests against the correct environment; steps 26 and 29 verify
the production/Pages outputs, version stamping, base paths, service worker and
deep-link behavior. A working `local` build alone does not close frontend work.

## Status

**Historical source baseline (2026-10-12 correction).** Compare retained behavior
against MyScoutee before the domain/UI extraction, not merely the latest refactored
HEAD: backend `5a23e829f`, its frontend gitlink
`9019fb69c812304c40ca2411a15d75ca3cf3a9aa`. Classify every difference as an explicit
omitted feature, a path/contract extraction, an intentional subsequent fix, or a
regression. Do not redesign inherited behavior while selecting the template.
The old `DeploymentBrandingResolver` already contained the MyScoutee default name,
heart logo, and last-known-good behavior; `ChatPushNotificationService` already
contained the two application-icon constants. The user subsequently explicitly
requested their removal (2026-10-12 10:06): branding must come from the saved
configuration, with no hardcoded product/logo substitution. Apply this correction
to MyScoutee and the template together. Missing or cleared values stay absent;
failed backend reads propagate instead of substituting a stale brand. Notification
snapshots preserve an explicitly empty logo. This is an authorized behavior change,
not part of the mechanical extraction.
Original operator configuration order is TLS, branding, API, privacy contact,
administrators, social links, payment, Firebase, email; omit payment only.


- **Current correction batch (2026-10-12):** restore the native side-panel
  transition by retaining its closed DOM state; restore the compact profile's
  Basics block; retain the five desktop operator action slots with only two
  selected actions; restore logout confirmation and account-deletion errors.
  The Login action must use `SessionService.authMode` after the existing Firebase
  runtime check, as in the source frontend. DEV's Firebase endpoint returns 204;
  it must open the demo selector. Do not change seeds or activate Firebase to
  compensate for this UI defect.
- **Shared logo presentation:** the existing shared `DeploymentBrandComponent`
  owns the original three-colour 3px underline, used by landing/member/admin/operator.
  Remove duplicate caller rules; keep the original contextual heading sizes.
- **Branding validation:** focused template and MyScoutee JUnit batches passed;
  service-worker push checks cover configured logo, absent logo, cold offline push,
  and distinct sender-photo/app-badge roles. KVM validation is still pending while
  the user is inspecting DEV. No performance parity claim from compilation alone.

- **OPEN — native frontend presentation parity (2026-10-12, user screenshots 09:19–09:22):**
  the custom member header, default Discover home, navigation rows and one-slot
  inline profile editor violate the requested minimal selection. Restore the
  original MyScoutee empty home shell, round avatar launcher, side-menu header
  and two icon tiles. Open SmartList/chat through the existing popup presentation.
  Reuse the native profile header and separate multi-image editor. Audit retained
  operator actions and chat presentation against their original owners. Keep only
  explicitly selected controls; no product-domain restoration or new UI design.
  Default operator branding remains MyScoutee and its native heart assets; the
  requested central landing heading remains Templates. Prior functional tests
  do not qualify these rejected visual adaptations. Verify local and HTTP visible
  browsers after correction, then resume package/environment qualification.

- **Final Git/release instruction (2026-10-12):** after all implementation and
  qualification, inventory each repository's baseline and remote refs, squash
  the task commits per repository using the existing `[MSC-122]` subject format,
  update published branches with explicit `--force-with-lease`, and tag version
  1.4.0. Publish source, Docker images and non-DEB release assets. The user uploads
  the DEBs. Do not rewrite unrelated baseline history or delete unrelated refs.

- **Open deployment blocker (2026-10-12):** complete the native and template
  [environment parity checklist](../../../myscoutee-backend/guides/qualification/ENVIRONMENT_PARITY_CHECKLIST.md).
  Native 1.4.0 source `15858a4cb` now passes actual VMware installation, all
  49/23 demo/production seed inventories, all 199 indexes, 18 retained production
  controls, the historical migration chain, 13 HTTP checks and visible guide/demo
  login. The selected template source corrections are present, but its actual
  1.4.0 install and serial E2E/KVM/monolith checks remain open. Deliver fixes
  through versioned installation/upgrade paths, never direct database repair.
  Netcup remains prohibited; use local VMware, then E2E/KVM/monolith serially.

- **DONE — local frontend Push/Sync (2026-10-12):** the user's 08:25 screenshot
  identified Git's checked-out-branch rejection on the existing local receiver.
  Its working tree was clean and its branch was an ancestor of the two outgoing
  frontend commits. Configured that receiver's standard
  `receive.denyCurrentBranch=updateInstead`; ordinary `git push origin master:master`
  advanced `2eb5ef8` to `6fd3abd` and updated its working tree. Both checkouts are
  clean and the frontend tracks `origin/master` with no ahead/behind difference.
  VS Code uses this same push command. No hosted repository or special push
  wrapper was introduced. Bundle export for the parent remains its existing
  separate source-artifact step; see [local frontend workflow](../../tools/sources/README.md).

- `DONE`: implemented and checked at the stated boundary, with evidence.
- `IN PROGRESS`: some code exists; the step's acceptance conditions are not all proven.
- `PENDING`: not completed. An earlier component test is not proof of this step.
- Every step closes with the tested revision, evidence location and any remaining
  qualification limit. A copied file or successful compilation is not completion.
- Follow the numbered order where there is a dependency. Add tests with their
  feature, and run coherent batches; later verification steps consolidate them.
- Use focused JUnit/frontend tests during implementation. Validate completed
  backend batches on KVM and monolith serially. Reuse the running dev compiler.
- Run only one application stack at a time. Stop dev before KVM/E2E/production
  qualification, stop that stack before the next one, and preserve existing data.
- Assess response time and CPU throughout. Run matched stress measurements after
  coherent batches or a measured concern, not after every edit. Report increases
  and decreases; idle CPU samples do not prove throughput or latency parity.
- Preserve tested commits. Do not publish a release, claim a passed deployment,
  or mark this plan complete while required implementation or verification remains.
- Maintain the template's own [QA matrix](../qa/matrices/TEMPLATE_QA_MATRIX.csv)
  and [execution notes](../qa/README.md). The user delegated automatic testing
  of the template: execute the retained flows as they become available and record
  actual results. An API pass must not mark local/HTTP UI, KVM or release checks
  passed. Reuse native runners and fixture/browser startup requirements.

## Numbered implementation steps

1. **Preserve the qualified MyScoutee source baseline — DONE.**
   The shared source preparation needed to resume template composition is
   committed through `cfa26b923`; the preceding chat boundary is `d11e45546`.
   Chat passed 406 native JUnit checks, 15 HTTP transport checks and 51 runtime
   assertions in each deployment at KVM Helm 141 and monolith. Account operations
   passed 173 JUnit checks and 55 runtime assertions in each deployment at Helm
   142 and monolith. Both batches passed eight service builds/ownership checks.
   Evidence is in the source repository's
   `guides/qualification/evidence/MSC122_CHAT_BOUNDARIES_20261011.json` and
   `MSC122_ACCOUNT_OPERATIONS_20261011.json`. These results qualify source
   preparation, not the unfinished template. No wholesale generalization of
   MyScoutee's product profile/discovery service is required to begin the template.

2. **Complete the source-to-template inventory — IN PROGRESS.**
   Walk the existing `server`, `frontend`, `microservices`, `e2e`, `packaging`,
   `tools`, `guides`, editor/build settings and GitHub configuration. For each
   retained flow, trace models, indexes, seed, API, adapters, UI, background jobs,
   supporting services, tests and installation. Record a concrete reason for
   each omitted product dependency. Verify that imports and runtime wiring do
   not require omitted owners. Classify each required infrastructure helper as
   directly reusable, configured through an existing input, or requiring a narrow
   source-side separation before copying. Record its configuration owner and
   source/template verification. The first completed source separation is Mongo
   seed import: `mongo_import.sh` is now byte-identical to the backend, with the
   selected collection rules in `mongo-seed-policy.inc`. Source evidence is
   `MSC122_SEED_IMPORT_BOUNDARY_20261011.json`; both native deployments passed
   focused tests on isolated test data. This does not close template startup or
   the remaining packaging/tool selection. Close when the remaining steps cover the entire
   required dependency closure, including capabilities not individually named by
   the user; do not treat a list of examples as the scope boundary.

3. **Establish repository and version consistency — IN PROGRESS.**
   Preserve the existing backend/frontend/components topology. `VERSION` is now
   `1.4.0`; propagate that version through the native build stamping, frontend
   badge, JAR, DEB, image tags, release metadata and Git tag `v1.4.0`. Retain
   attribution and licensing. Verify a clean checkout/submodule initialization.
   Portable frontend initialization now uses `tools/bootstrap-submodules.sh`
   and one stable Git bundle at the exact gitlink. Its components still use the
   existing remote repository. The clean independent checkout passed; later
   release/build qualification remains separate.

4. **Compose the minimal backend application — IN PROGRESS.**
   Reuse `Main`, Gradle, the native Spring configuration, security filters,
   routing, caching, validation and selected controllers. Retain actual owner
   implementations and native authorization. Complete the application dependency
   graph without fake successful adapters or permissive substitutes. The current
   HTTP/configuration selection has 17 focused passing checks. The selected dev
   backend now starts with MongoDB, Redis and MinIO; ten automated HTTP cases
   exercise the seed and session/security boundaries. The public-document
   session defect is resolved by the unchanged native fix at `2ab74dd8e`,
   qualified first on source KVM and monolith. Template evidence is
   `MSC122_TEMPLATE_PUBLIC_DOCUMENTS_20261011.json` (48 JUnit tests and all ten
   HTTP cases, including anonymous/stale-session reads after logout).
   This is not proof of the
   complete frontend, integration or deployment composition. Close with a live healthy application and retained
   routes exercised against its own databases.

5. **Audit, modularize and remove omitted admin features from the template backend — DONE.**
    Dependency inventory: [admin feature audit](MSC-122_ADMIN_FEATURE_DEPENDENCY_AUDIT.md).
    Added and prioritized by the user on 2026-10-12. The source audit and
    extraction cover Reports,
    moderation/warning/blocking, admin Support chat, Feedback, and the corresponding
    member report/feedback submission paths. This is authorized implementation
    work, replacing the earlier open decision about whether removal is wanted.
    This gate precedes further service composition, packaging and deployment
    qualification, so they are built from the correct minimal dependency set.
    Start from the existing MyScoutee owners, source history and domain boundaries;
    identify direct and transitive dependencies in Java, local adapters, DTOs,
    repositories, auth, notifications, jobs, seeds/indexes, API/MCP, deployment and
    tests. Record the concrete dependency map and minimum retained contracts before
    editing. Reuse existing ports/configuration; do not move unrelated domains or
    add empty fallback implementations to disguise a missing required owner.
    Make these feature owners independently selectable in **myscoutee-backend**
    first. Preserve the full application's existing behavior, authorization and
    query performance when enabled. Avoid new per-record database/remote calls.
    Verify a coherent source batch with focused JUnit and serial KVM/monolith
    runtime checks, recording CPU and latency; use matched load measurements only
    for the coherent batch or a demonstrated performance concern. Commit the
    verified source batch before proceeding with the template removal.
    Then omit the feature implementations and their exclusive dependencies from
    **myscoutee-template**, including local/HTTP bindings and no-longer-required
    seeds, jobs, packaging and tests. Preserve ordinary member chat and existing
    conversation visibility, identity/session behavior, required API/MCP access,
    notifications/help/consent and account deletion/purge. Decide any affected API
    response selection explicitly from the audited contract; do not silently break
    callers or retain a full feature only to populate an unused UI counter.
    Close with absence/ownership checks, retained local/HTTP flows, focused JUnit,
    working monolith and independent KVM composition, proportional CPU/latency
    evidence, updated provenance/QA matrix and tested commits. Complete this gate
    before final release acceptance in step 31.
    Native source is qualified and committed through `37f5f708e`: all existing
    features remain enabled; KVM and monolith checks pass. The matched KVM admin
    read profile reports +3.93% throughput, -3.81% p50, -7.98% p95 and -0.84% VM
    CPU, with zero request errors. These results do not establish every flow's
    performance. Template source removal is committed at `b520360`, frontend
    `9d035df`: feature routes/services, local/HTTP adapters and exclusive stores
    are omitted. Normal chat, account lifecycle and admin API/MCP remain.
    Unit and artifact-monolith checks pass; runtime evidence is consolidated in
    `MSC122_TEMPLATE_ADMIN_FEATURE_REMOVAL_20261012.json`. The independent template
    KVM check requires the minimal composition from step 25. Start that composition
    from the now-selected sources, then close this remaining gate before release.

6. **Restore the complete dev/prod/E2E runtime structure — IN PROGRESS.**
   Select the necessary services from the existing Compose files, Dockerfiles,
   entrypoints, environment examples and startup/status/stop tools. Preserve the
   common implementations through the source-first infrastructure rule above.
   Supply template-specific service/image/seed settings through their existing
   configuration owners. Preserve the
   original backend hot reload, frontend watcher, frontend-sync and Mongo seed-sync
   behavior in dev and artifact-only execution
   in E2E/production. Include Mongo replica initialization, Redis, MinIO, nginx,
   STUN and MCP wherever required by the retained flows. Keep operational status
   and health reporting. Some native scripts and Dockerfiles are selected;
   the complete Compose stacks are not yet assembled or qualified.
   Dev frontend-sync/nginx now run with the native availability receiver and
   publisher. Two focused publisher tests, thirteen API cases through nginx and
   HTTP/HTTPS consent/login/reload/logout browser checks pass. The HTTPS browser
   uses the native QA allowance for a self-signed local certificate; production
   trust is untested. Locale generation/audit uses unchanged native scripts with
   template-owned inventories (1,790 messages across three physical seed sets).
   See `MSC122_TEMPLATE_DEV_GATEWAY_20261011.json`. Close with
   reproducible starts, stops and restarts in each mode and correct data isolation.

7. **Build small, coherent seeds and import/reset tooling — IN PROGRESS.**
   Reuse the canonical Mongo importer, index creation, local seed builder and
   media bootstrap. Create minimal members, the required admin/operator actors,
   approved personal-chat pairs/messages, demonstration cards, consent revisions,
   guide content, localization and required job/configuration rows. Keep physical
   `demo_db`, `e2e_db` and production-safe seed sets and their local equivalents.
   Production contains no synthetic members or activity. Preserve mutable
   operator configuration and uploaded media on reimport. Close with fresh import,
   controlled reset/reimport, stable references/counters and no omitted-domain IDs.

8. **Complete native identity and minimal profile flows — IN PROGRESS.**
   Reuse REAL/DEMO selection, sessions, Firebase/provider authentication,
   operator bootstrap, login/logout, session revocation and account permissions.
   Expose a small profile rather than MyScoutee's full product form. Preserve
   server-owned privileges and fields omitted from the request. Component checks
   already exist; close with real controller/session save/reload and rejection of
   forged identities, stale sessions and unauthorized profile writes.

9. **Complete account deletion and required background work — IN PROGRESS.**
   Select the qualified account lifecycle/purge owners and retained-domain cleanup
   actions. Preserve durable intent, retries, idempotency, counters and completion
   ordering. Include provider data-deletion callbacks and status when supported by
   the retained authentication flow. Exclude cleanup stages for absent product
   domains explicitly. Close with deletion, retry/restart and retained personal
   data cleanup verified without requiring an admin dashboard.
   The native lifecycle/purge selection and unchanged chat/help/notification
   cleanup owners now run in template dev. The deployment-scoped account-purge
   schedule is seeded in all three physical seed sets. Twenty-four focused
   standard tests, one embedded MongoDB retry/persistence test and the ten dev
   HTTP contract cases pass; see `MSC122_TEMPLATE_ACCOUNT_PURGE_20261011.json`.
   API/MCP credential cleanup now joins the same worker, with embedded retry
   and retained-owner checks in `MSC122_TEMPLATE_API_MCP_20261011.json`.
   The native five-day deletion-warning email job is now selected with configured
   provider delivery/retry/deduplication, encrypted operator configuration and
   anonymous unsubscribe. Twenty-two standard tests, four provider HTTP tests,
   one Mongo purge test, local/HTTP operator settings and the full warning-to-
   unsubscribe/suppression flow pass. The source warning-claim purge fix is
   `4b63e893d`; source KVM (63 checks) and monolith (43 checks) now pass
   serially, with index and settled CPU evidence recorded in native
   `MSC122_ACCOUNT_WARNING_PURGE_20261011.json` at `5d71945a2`. These focused
   runtime checks do not claim matched performance or direct runtime purge. See
   `MSC122_TEMPLATE_ACCOUNT_EMAIL_20261011.json`. Four seeded jobs are retained:
   purge, warning email, notification delivery and integration/MCP callback delivery. Provider callbacks and full HTTP/UI/REAL-DEMO/KVM deletion
   qualification remain pending; this checkpoint does not close the step.

   Retained background-work inventory (source audit, 2026-10-11):

   | Work | Current template owner | Status / boundary |
   | --- | --- | --- |
   | Deleted-account purge | `DeletedAccountPurgeService` | Selected; default `users.account-purge-window-days=30`, native rule-gated scheduled scan, durable cleanup retry |
   | Notification delivery/retry | `NotificationOutboxWorkerService` | Selected; native Mongo change-stream wakeup and due-time scheduling, not an added constant polling loop |
   | Integration/MCP callbacks | `IntegrationWebhookService` | Selected; native durable claims/retry and bounded delivery; external public callback qualification remains open |
   | Pre-purge warning email | Native `DeletedAccountWarningWorker` | Selected; five-day warning, provider retry/deduplication, operator configuration, unsubscribe and purge cleanup pass focused tests and artifact monolith; KVM/installed delivery pending |
   | Chat realtime, call availability, runtime status | Native chat stream consumer, STUN probe, runtime publisher | Infrastructure workers are selected separately from the four seeded job rules; call behavior still needs the step 10 audit |
   | Events, payments, affinity, product content approval and business reports | Omitted product owners | Not required by retained template screens/models; do not import their jobs or model graphs |

   An admin Jobs screen is not required to run these owners. Preserve the native
   seed/configuration and audit state independently of optional management UI.

10. **Complete personal communication and its required infrastructure — IN PROGRESS.**
   Reuse chat/message storage, participant authorization, paging, mutations,
   read receipts, counters and WebSocket/Redis delivery. Admin support workflow
   is omitted from the UI; its backend remains until step 5 qualifies removal.
   Carry the qualified separation of product conversation creators.
   Do not hide or block stored conversations based only on omitted domain names.
   Audit native call/signaling and email delivery dependencies for retained flows;
   retain the required infrastructure without importing event/campaign features.
   Close with two-user message/read/reconnect flows and isolation. Step 5 owns
   removal of admin support creation; do not change generic conversation access.

11. **Complete notifications, guide/help and consent — IN PROGRESS.**
    Retain native durable delivery, inbox state, collapse/idempotency, retries,
    push/device settings, guide state and versioned consent acceptance/enforcement.
    Use small template-owned content with the existing schemas. Separate required
    storage/background work from optional management screens. Close with delivery,
    unread/read transitions, guide completion, acceptance save/reload and a new
    consent revision in the appropriate REAL/DEMO database.

12. **Complete media, localization and geographic location — IN PROGRESS.**
    Reuse MinIO/S3 access, avatar/image editing, variants, private-media sessions,
    locale bundles and country/location lookup. Both localization and geographic
    location are required. Any operator location switch belongs to the existing
    configuration owner. Preserve batched queries and native indexes. Close with
    image upload/read/delete, unauthorized media denial, locale changes and valid,
    invalid and disabled-policy location cases across the applicable modes.

13. **Complete API and MCP integration without affiliate functionality — IN PROGRESS.**
    Select the existing token/OAuth, scopes, revocation, API-settings popup,
    integration access and MCP server/transport needed for ChatGPT and retained
    member/admin actions. Reuse their current owners and errors. Include supported
    callback/webhook jobs only where the retained API requires them. Keep member
    and admin capabilities distinct; omit affiliate registration, attribution,
    business summaries and payment/network accounting. Close with native client
    authorization, allowed requests, denied scopes, revocation and an MCP round trip.
    The dev backend selection passes 54 focused standard and two embedded JUnit
    tests, eight-index Mongo checks and all thirteen HTTP collection cases, including
    API keys and both manual/public OAuth clients. Source fix `c7b2ba032` removes
    the entire Java index initializer; selected indexes belong to Mongo init, seed
    policy and migration012. See `MSC122_TEMPLATE_API_MCP_20261011.json`. The API
    settings/authorization UI, privileged operations and full MCP server/ChatGPT
    round trip remain pending; this backend checkpoint does not close the step.
    The native Go transport is selected under `mcp/` after source `c11b292`
    separated product build inputs. Eight selected Go race tests, 11 Java tests
    and the complete 17-case dev HTTP collection pass, including actual public
    help, private member/admin tools, Full role discovery, blocking, rotation and
    revocation. See `MSC122_TEMPLATE_MCP_TRANSPORT_20261011.json`. External
    provider acceptance, public callbacks and deployment gates remain distinct.

14. **Build the frontend foundation and both adapter modes — IN PROGRESS.**
    Work inside the existing local frontend submodule and reuse the shared
    components. Select Angular configuration, application bootstrap, routing,
    session/store infrastructure, local and HTTP adapters, IndexedDB schema,
    seed loading, translation, error/loading handling and build scripts. Preserve
    all five configurations in the frontend environment inventory above and their
    native serve/build/test wiring.
    Frontend `7f5e0ff` now contains the native Angular build/environment matrix,
    session/Firebase/device owners, scoped IndexedDB engine, localization and
    PWA/version infrastructure. The local build and 97 focused tests pass.
    MyScoutee source frontend `1af20e0b` separately qualifies the existing test
    runner path and Firebase fixture repair (three passing checks). This is the
    foundation only: consent/login screens, local seed, HTTP user adapters and
    browser persistence checks are not complete. Evidence:
    `MSC122_TEMPLATE_FRONTEND_FOUNDATION_20261011.json`.
    Frontend `c4f3c39` additionally passes 108 unit tests and a fresh-browser local
    public-document/consent/demo-entry/reload/logout flow. The selected profile
    repository preserves omitted fields and private devices; stale-session results
    are discarded and HTTP errors do not fall back to local data. Native document
    route rendering is fixed and qualified at frontend `5fbf3e62`. See
    `MSC122_TEMPLATE_LOCAL_ENTRY_20261011.json`. HTTP runtime wiring and the
    full authenticated screens remain pending.
    Close with working local persistence and HTTP persistence for retained flows,
    reload recovery and no silent local fallback in HTTP mode.

15. **Build the landing page and version display — IN PROGRESS.**
    Use the existing landing/login/branding components. Show a large central
    “Templates” title in the requested Gothic/calligraphic style, with the logo
    and login entry on opposite sides and the real build version badge. Preserve
    native demo/real entry, responsive layout and theme behavior. Close with both
    entry modes and matching version metadata in dev, production and Pages builds.
    The requested Gothic title now uses a bundled, unmodified OFL font; it loads
    locally without a font-provider request at frontend `9d59bf9`.

16. **Build the member SmartList example — DONE (local and dev HTTP).**
    Reuse SmartList, its supported views and native state/adapter patterns. Supply
    a few visually useful, project-neutral seeded cards rather than importing
    MyScoutee events to populate the screen. Keep the feature small but persistent
    in both local and HTTP modes. Close with view switching, relevant list controls,
    reload and matching local/API results using the same component implementation.
    Frontend `9d59bf9` supplies four neutral landscape cards through native SmartList,
    ImageCard, SingleRow and AppMenu. Cards, rows and gallery, URL view persistence,
    consent/session entry and logout pass in local and dev HTTP browsers. All 110
    frontend tests and three focused backend JUnit checks pass. Seed import,
    bounded API paging and anonymous denial pass; Mongo uses `_id_` with four
    keys/documents examined for four cards. No events/groups model is selected.
    See `MSC122_TEMPLATE_SHOWCASE_20261011.json`. KVM remains step 25; production
    intentionally has no synthetic sample-card data.

17. **Build the minimal member chat screen — DONE (local and HTTP; KVM remains step 25).**
    Connect the existing chat components and adapters to the retained backend/local
    owners. Apply the requested compact chrome: avatar pill and collapse control;
    omit product-specific badges, invite/report actions and the bottom add-item “+” menu. Preserve audio/video calls, replies, reactions, pin/unpin and pinned-message access, typing, editing, unsending, read receipts and reconnect. Keep the actions needed
    for the retained conversation flow. Close with message send/edit/read/reconnect
    checks and consistent state across two participants and reload.
    The earlier text-only qualification did not establish native basic chat parity.
    The 2026-10-12 correction reuses the native call implementation and extracts
    its product-independent message menu before selection. Local/HTTP reply,
    reaction and pin save/reload, author-only edit/unsend and two consecutive
    two-party HTTP audio/video calls pass. Invite/report stay omitted.
    Two native call defects (one-way media negotiation and rejected-commit WASM
    traps) are fixed at source and selected unchanged. All 297 frontend tests,
    seven crypto/voice tests and both production builds pass. See
    `MSC122_TEMPLATE_CHAT_PARITY_20261012.json`.
    Frontend `7b3ab41`: 114 unit checks, local send/edit/read/delete/reload across
    both participants, and two-browser HTTP delivery/reconnect/no-duplicate/
    persistence checks passed. The native local read-receipt repair `bf4760f9`
    passed 16 focused source tests before selection. See
    `MSC122_TEMPLATE_CHAT_20261011.json`. Admin support UI is omitted under step 20; backend removal is step 5;
    template KVM remains step 25.

    **Pending source/template naming follow-up:** introduce `ChatFeedFilters` in
    the chat contract for the existing chat-only filter fields currently named
    `ActivitiesFeedFilters`. First check all consumers in the source frontend:
    its shared activity contract also serves non-chat lists. Keep those callers
    compatible (the broader contract may extend the chat contract where valid),
    then select the chat type and imports in the template. Preserve field names,
    serialized queries, filtering behavior and runtime/DB calls. This is a
    planned type/import cleanup, not an implemented change. Verify source and
    template TypeScript builds and focused chat-filter tests in one coherent
    frontend batch; no independent deployment or stress run is needed for a
    type-only rename.

18. **Build the avatar menu, profile editor and minimal settings — DONE (local/dev HTTP).**
    Reuse the existing menu, FormFlow/profile and image-editor entry points. Show
    only the small editable profile and settings required by retained features.
    Preserve logout, account deletion, consent and relevant notification/device
    controls. Remove the shared-list header and product actions from the side menu.
    Close with save/reopen, image editing and no clearing of hidden stored fields.
    The native avatar/menu/HeaderCard, FormFlow, ImageCarousel and selected media
    adapters now compose the name/avatar editor. Local and HTTP browser checks
    cover upload/replace/remove, save/reload, cancellation and preservation of
    stored fields outside the form. Privacy/terms and confirmed account deletion
    work in both modes. Notification inbox and guide integration are qualified in
    18. Native device setup, permission cancellation, location save/disable,
    nested help and contextual setup guide now pass in both modes. The same
    minimal editor handles onboarding and resumes after reload. Native account
    reactivation confirmation/cancellation restores the prior state and clears
    HTTP purge tracking. The combined frontend checks cover 182 tests in 23 files;
    see `MSC122_TEMPLATE_SETUP_LIFECYCLE_20261011.json`, frontend `3244e2e`.
    Configured Firebase/provider login, actual push delivery, installed PWA and
    KVM qualification remain deployment/integration gates; disabled unconfigured
    push controls are not proof of delivery.

19. **Connect frontend guide, consent and notification surfaces — DONE (local/dev HTTP).**
    Reuse the existing top-menu access, drawers/dialogs, guide engine and consent
    flow. Seed guidance only for retained screens. Preserve unread/seen state,
    document versions and local/HTTP behavior. Close with initial entry, required
    consent, guide completion, notification navigation and persistence after reload.
    Native notification paging/read/mute state, the 30-second HTTP realtime badge,
    chat navigation and contextual guides now pass in both modes. The retained
    public entry/menu/profile/chat tours are covered, with 131 passing unit tests
    and successful chat regression tests. The root geometry and local content
    startup dependencies are included. See
    `MSC122_TEMPLATE_NOTIFICATIONS_GUIDE_20261011.json`, frontend `ae25d6f`.
    Device/setup controls and guide are qualified in 18; live push delivery and
    KVM/installed artifacts remain pending.

20. **Complete the minimal admin and operator shells — IN PROGRESS.**
    Keep the admin function menu empty. Retain common identity/settings,
    notifications/help/guide and API access. Keep operator bootstrap,
    deployment/Firebase configuration, installation/TLS and update/status controls.
    Omit virtual-company Registry screens. Reuse existing authority checks.
    Scope decision, 2026-10-12: remove the template Reports, Support chat and
    Feedback screens and their UI stores/bindings; remove member message-report
    and feedback-submission entry points. Preserve normal member chat and existing
    conversations. Remove the five guides for the omitted admin popups from local
    and Mongo content seeds. Do not alter backend models, services or endpoints
    as part of this UI selection. The new selection passes 309 frontend tests, production/artifact builds and
    four local plus four artifact HTTP flows at frontend `87b9715`; see
    `MSC122_TEMPLATE_MINIMAL_ADMIN_UI_20261012.json`.
    The earlier moderation UI evidence below describes the previous selection.
    **Backend removal implemented on 2026-10-12:** step **5** supersedes the
    earlier retention decision. The native owners remain installed and qualified;
    the template omits their implementations and exclusive bindings at `b520360`.
    Admin API attention omits the report/feedback/support fields, retaining normal
    unread messages, version and job status. Template KVM is still unverified.
    Member API/MCP settings are selected and qualified at frontend `7c9baee`: 18
    focused tests, local/HTTP create/mask/permission/revoke flows, contextual guides
    and dev HTTP browser OAuth consent/PKCE/login return pass. See
    `MSC122_TEMPLATE_API_MCP_UI_20261011.json`. Admin/operator controls and full
    external MCP transport remain open; this does not close step 20.
    Operator configuration now passes at frontend `6e532c1`: 20 focused tests,
    local/HTTP save/reopen/reload and guide flows, member read/write denial, and
    public/private settings separation. Branding, API URL, privacy, administrator
    emails, social links and Firebase configuration are selected without Registry
    or payment state. See `MSC122_TEMPLATE_OPERATOR_CONFIGURATION_20261011.json`.
    TLS controls and the TLS/update backend are now selected at frontend
    `800ff16`: 36 focused Java checks and 16 frontend checks pass. Local TLS
    save/reopen/disable and dev HTTP capability/role denial pass. See
    `MSC122_TEMPLATE_TLS_UPDATE_SELECTION_20261011.json`. This backend batch still needs
    KVM qualification; host execution and live Firebase remain open.
    Updater UI is qualified at frontend `4b13d99`: 41 focused frontend tests,
    local simulated installation/PWA reload, dev HTTP version/check and notification
    target/guide flows pass. See `MSC122_TEMPLATE_UPDATE_UI_20261011.json`.
    This is not signed DEB installation or rollback qualification.
    Historical, superseded selection: admin report/feedback/moderation and support
    HTTP actions passed with native
    chat evidence, role isolation and required MCP role-delivery ownership. See
    `MSC122_TEMPLATE_ADMIN_BACKEND_20261011.json`. Frontend `43980be` now also
    qualifies local/dev HTTP feedback/report/moderation/support flows, empty queues,
    canonical admin selection, reload and native admin API create/mask/block/revoke.
    Twenty frontend checks and five administrator API Java checks pass; see
    `MSC122_TEMPLATE_ADMIN_UI_20261011.json`. The minimal admin API keeps the native
    Attention shape and omits the optional analytics statistics field/model/worker.
    Five former admin contextual tours passed in local and artifact E2E, including
    warning and read-only evidence contexts. Live role login and KVM/installed
    host controls remain open.

21. **Consolidate JUnit and frontend unit/component coverage — PASS for selected suites.**
    Preserve `tools/run-backend-junit.sh`, native standard/embedded/integration
    groups and the existing frontend test runner. Select tests for retained
    contracts and add meaningful checks for composition or adapted behavior.
    Integration tests remain explicit; test runs must not touch dev/production
    data or trigger a second compiler. Close with a reproducible coherent batch,
    recorded counts and no unexplained failure or skipped required contract.
    The complete selected suites now pass: 400 backend tests in 69 classes and
    256 frontend tests in 33 files, with no backend failures/errors/skips. Two
    test fixtures were aligned with the already-selected security filter and
    canonical media policy; no application behavior changed. See
    `MSC122_TEMPLATE_CONSOLIDATED_TESTS_20261011.json`. Explicit integration and
    deployment qualification remain separate gates.

    The subsequent frontend domain-folder batch passes all **1267 native** and
    **305 template** frontend tests, both production builds and all 14 applicable
    template local UI flows. Core services, repositories, contracts and related
    adapter layers now use their existing backend domain folders. The resolved
    source imports and optimized bundle dependency graphs are unchanged by the
    moves; initial JavaScript sizes are unchanged. Active generators, parent
    scripts and documentation links use the new paths. See
    `MSC122_FRONTEND_DOMAINS_20261011.json` for the exact revisions, separately
    committed behavior fixes and validation limits. Build CPU measurements do
    not establish runtime performance parity.

    Current admin-removal selection passes 398 standard Java tests in 68 suites,
    one embedded Mongo purge check and 293 frontend tests in 34 files. The count
    changed with the deliberately omitted feature tests; it is not an additional
    aggregate over earlier runs. Production build also passes. See
    `MSC122_TEMPLATE_ADMIN_FEATURE_REMOVAL_20261012.json`.

22. **Implement backend functional E2E — IN PROGRESS.**
    Reuse `e2e/shared`, `e2e/functional/backend`, fixture hooks, contract and
    concurrent runners. Select and adapt health, identity, profile, chat, media,
    guide/consent, notifications, API/MCP and operator flows. Preserve safe read
    mode, explicit mutation cases, isolated fixtures, cleanup and endpoint coverage
    auditing. Close with real HTTP mutation/read/reload checks and negative
    authorization checks on the standalone template E2E stack.
    The native artifact-only E2E stack now starts seven healthy selected services
    on isolated e2e_db. All 17 contract/session/MCP cases and the complete selected
    admin mutation runner pass, with owned-session/fixture cleanup. Fifteen browser
    flows also exercise persistence through the actual built edge/API/DB/media
    images. See MSC122_TEMPLATE_E2E_RUNTIME_20261011.json. Provider callbacks and
    email-owner selection remain earlier-step gaps, not silently passed endpoints.

23. **Implement frontend E2E and local/HTTP parity checks — PASS for selected flows.**
    Reuse `e2e/functional/ui` and existing browser helpers/checks for the retained
    routes and flows. Cover entry, SmartList views, chat, profile/avatar, consent,
    guide/notification/settings and privileged shells in the applicable modes.
    Verify reload, responsive layouts, navigation, assets and browser errors.
    The user explicitly authorized headless Chrome for automatic template QA;
    prefer that mode with the native runner and isolated, versioned fixtures.
    Use previously qualified MyScoutee behavior as the comparison baseline, keeping
    its four outstanding QA tasks explicitly outside the proven baseline. Classify
    a failure as fixture/runner, template composition or upstream behavior before
    changing application code; do not redefine the established behavior to make
    a new test pass.
    Respect the repository's browser authorization boundary; a manual-QA handoff
    does not itself authorize automated checklist actions. Close with reproducible
    isolated E2E evidence rather than screenshots of an unconnected mock UI.
    The serial suite maps existing fixture-owning runners to the QA table and
    records JSON/CSV results. All 15 artifact HTTP flows pass, including the
    optimized-build chat reconnect and administrator guides. All 14 applicable
    local flows also pass after stopping E2E, including the same admin guides.
    See MSC122_TEMPLATE_LOCAL_UI_SUITE_20261011.json. OAuth remains HTTP-only.

24. **Restore stress/performance tooling and measure a coherent build — IN PROGRESS.**
    The native backend load engine, ramp and comparator are selected unchanged;
    its product fixture context is separate in the source owner. Two 60-second
    runs of the minimal read workload (6 users, 15-second warmup) pass with zero
    errors: 6769/6930 completed cases, p95 62.69/58.34 ms. Java peak CPU is
    201.66/196.32% (about two cores); Mongo peak is 154.74/114.32%.
    These are same-image baseline/repeatability observations, not a source or
    before/after speed comparison. One prematurely stopped attempt is excluded
    and its owned sessions were explicitly revoked before a full successful retry.
    Browser/WebSocket stress selection remains pending. See
    `MSC122_TEMPLATE_RELEASE_TOOLS_PERFORMANCE_20261012.json`.
    Reuse `e2e/stress/backend`, `e2e/stress/ui`, shared measurement helpers and
    microservice runtime probes. Select the minimal API/WebSocket/UI workload and
    small deterministic fixtures. Record revisions, data, warmup, concurrency,
    deployment limits, errors, throughput, latency percentiles, CPU and memory.
    Compare equivalent builds/workloads and investigate regressions. Close with
    repeatable commands and an honest baseline; do not repeatedly stress every edit.

25. **Compose and qualify the minimal microservices/KVM deployment — IN PROGRESS.**
    Reuse `microservices` assemblies, bindings, transport/authentication, build and
    ownership audits, image locks, manifests/charts, gateway, VM/host tools and
    monitoring, including **Prometheus and Grafana**. Retain the existing metric
    endpoints/exporters, scrape configuration, dashboards, access/port forwarding
    and resource limits needed by the selected services. Supply the template's own
    service names, namespace/VM identity and state through the existing configuration
    owners. Verify healthy Prometheus targets, actual backend/container/VM metrics
    and populated Grafana panels for the retained CPU, memory, request/latency and
    service-health measurements; running monitoring containers alone is insufficient.
    Select only retained owners; the template need not run MyScoutee's
    eight JVMs. Preserve monolith support and REAL/DEMO semantics. Use a template-owned
    VM with no host-boot autostart. Include the native start/stop/status commands
    and live VM/Kubernetes/container status terminal. Separate its project name,
    state directory, lock and terminal title at the source owner before reuse.
    Open the live status terminal when starting KVM, as requested by the user.
    Current composition selects four owners: identity (accounts/access/admin/
    platform/integrations/showcase), communication (chat/notifications/email/
    localization), media and help. It reuses the native bootstrap and selected
    typed transport, with no community/payment/Registry/moderation service owner.
    Four service JARs and ownership audits pass, together with 51 transport tests
    in 11 suites and 23 focused canonical Java tests. Eight selected wire
    contracts validate 182 actual HTTP responses. See
    `MSC122_TEMPLATE_ASSEMBLY_SELECTION_20261012.json`. The VM, gateway,
    monitoring and lifecycle configuration remain in progress.
    Source `65c091364` moves build identity and optional shared imports to the
    existing assembly manifest. The next coherent batch also configures the
    existing repository factory and extra shared sources without changing native
    values. A required template realtime unread count uses the existing
    notification-read transport: same owner count query, signed actor/body/mode
    validation and no page fetch/retry. Native HTTP tests and contract validation
    pass; runtime qualification of this new operation remains open.
    Native configurable-lab follow-up `9a8c48319` passed KVM and monolith checks.
    The template selects 59 framework files (57 byte-identical; two chart metadata/
    support-inventory adaptations), with its four owners and project identity in
    `deploy/runtime.json`. Terraform validation passes; the pinned cloud image and
    a new private VM identity are prepared in template-owned local state. Actual
    template deployment and monitoring checks remain in progress.
    Close with focused KVM checks followed serially
    by monolith checks, CPU/log evidence, correct service credentials and cleanup.

26. **Build and verify production Docker artifacts — IN PROGRESS.**
    All five managed 1.0.0 images have been built with the native release builder.
    Seven production services are healthy in an isolated artifact-only local
    qualification stack. The 17 backend cases pass with the DEMO MCP realm
    explicitly enabled as a test fixture; production retains its disabled default.
    Production entry/SmartList, complete chat lifecycle and two consecutive
    audio/video calls pass; the omitted-admin/API gate also passes. The actual
    five images were published to GHCR, pulled back and matched by config ID.
    See `MSC122_TEMPLATE_PRODUCTION_PAGES_20261012.json`. Private GHCR access
    and the missing fresh-build MinIO source remain release-delivery gaps.
    Reuse native build/stamping, nginx/media/runtime images, image manifests and
    production Compose. Build retained services with project-owned versioned tags
    and immutable identities. Include required assets, seeds and helper files;
    exclude source watchers, test fixtures and secrets. Close with startup and
    retained-flow checks using the actual built images, not a dev bind mount.

27. **Complete DEB, host operations and local VMware Debian installation — IN PROGRESS.**
    The first template 1.4.0 install exposed a host-account collision with the
    retained MyScoutee UID/GID 10001. Native source `f5d612bce` makes runtime
    ownership package-owned; template selects 10002. Host helpers, API image
    and sysusers are checked together, including another retained account and
    repeated configuration. All focused host/worker and four-environment checks
    pass. The subsequent actual install identified an older shared migration
    runner that did not select migration-owned JSON inputs. Select the unchanged
    current native runner and exercise the complete selected chain, including
    input receipts, interruption and retry. All 73 deployment files declared
    unchanged now match the current source owner. The actual installer replay
    and visible browser flows remain required. Shared ports require serial execution.

    **VMware order (user correction, 2026-10-12): first build and deploy the
    current modified MyScoutee backend to the existing local guest, then validate
    its installed startup, retained flows, response time and CPU. The user waived
    the currently unavailable email-verification registration check on 2026-10-12;
    record it as SKIPPED and continue with template installation. The old installed
    1.3.0 is not evidence for these changes. Preserve the deployment state. After
    that gate, stop MyScoutee and install the template with its own fresh database
    and media directories. Never use the original MyScoutee database as template
    test data. Leave the qualified template accessible at its browser URL.**
    Compare the installed environment against DEV before the next installation:
    audit all runtime inputs, Mongo seeds/import rules, image assets, mounts and
    configuration together; do not patch one omitted file per failed start.
    The host implementation is now selected: shared Debian lifecycle, systemd,
    fresh credentials, TLS, status, image verification, seed migration, backup/
    restore and purge. Native install/host/recovery policy was separated at
    `b130cfb07`, `349f2a8af` and `8ceb635bc`; MyScoutee retains Registry/payment
    behavior while template project data excludes it. The template requires nine
    production catalogs, five bundled managed images and seven Compose services.
    Native package-content, Registry recovery, update ingress, TLS, bootstrap and
    seed-parity checks pass. Template command-double lifecycle tests pass without
    touching a deployment. The user subsequently authorized local VMware qualification; use the existing local guest, preserve its MyScoutee installation and leave the template available for browser review. Open a visible browser at each VMware, monolith and KVM deployment check, as requested. After VMware qualification, shut down the VMware VM itself before starting E2E; those environments must never run together.

    Concrete source-reuse gap identified: the existing update worker hard-codes
    the MyScoutee package name/path, installed service and image inventory
    (including Registry/payment images and the 1.3.0 MCP transition). Separate
    these product inputs in the source owner before copying the worker. Preserve
    its strict schema, signature, ownership and image-identity validation. The
    signing script is already shared unchanged after source `7a1d375c8`; its
    product-specific installed-worker history is in an adjacent JSON policy.
    Select the existing `packaging/debian`, `packaging/scripts`, release tools,
    client tools and deployment verifier. Preserve install/configure/start/status/
    stop, systemd and privileged-helper boundaries, TLS, permissions, fresh
    credentials, data paths, service readiness, backup/restore and removal behavior.
    Keep generic operational capabilities needed by the minimal installation.
    Native source `fcce8cfa1` fixes the historical help input and adds the missing
    forward v5 update as migration013. Twenty focused Mongo check groups pass
    on each of KVM and monolith; see native `MSC122_HELP_V5_UPGRADE_20261011.json`.
    Released checksums remain intact. Do not select MyScoutee's help/product
    migrations into the template. The native test-package attempt additionally
    found unclassified newer product/guide seeds in `check-system-seed-parity.py`;
    reconcile that inventory before package qualification, preserving the audit.
    Separate mixed product-specific package names, image/service inventories,
    seed selections and deployment paths at the existing MyScoutee owners first
    when their current inputs cannot already express the template. Copy the
    qualified shared helper, rather than forking its complete implementation.
    **Current execution order (latest user instruction, 2026-10-12): finish
    DEV/frontend-local qualification, run mini E2E and stress, then repeat KVM.
    VMware follows when the user starts the existing guest. Its installation,
    backup/restore and browser acceptance requirements remain open and unchanged.
    Preserve its MyScoutee deployment before switching stacks.**
    Perform installation qualification only on a local VMware Debian VM: fresh
    install, restart, backup/restore and data-preserving removal. Reuse the existing
    installation workflow and prepare this same VMware deployment for the final
    user smoke check. Record its evidence separately from the KVM lab; one
    hypervisor does not qualify the other.
    **Netcup is strictly out of scope: do not connect to, inspect, deploy to or
    modify any Netcup host or resource under any circumstances.**

28. **Complete signed releases and the updater — IN PROGRESS.**
    The minimal client-tools archive now builds through the unchanged source
    builder, using the selected manifest. Shared install/purge clients validate
    the template identity and retain the original signature/SSH boundaries.
    All 13 external deployment checks pass on the actual production-image
    monolith (not VMware); only public content assertions are project-owned.
    The host lifecycle/recovery implementation and focused tests are delivered in step27; local VMware installation is now authorized and pending.
    Selected the unchanged native privileged updater, artifact signer/verifier,
    and strict five-image template policy. Source `prepare-release.py` now owns
    complete package/client-tool signatures plus matching signed update metadata;
    the existing source QA helper delegates to it. Real disposable Ed25519 signing,
    tampered-catalog rejection, metadata/hash binding and overwrite rejection pass
    for native and minimal project identities. The template release authority is generated outside Git with mode 0600; only its public key is versioned. No CI signing secret is configured. Installed update/recovery evidence remains pending.
    Reuse native release discovery, canonical signing, manifests, staging,
    verification, durable update jobs and rollback/recovery. A DEB must never be
    published without the required matching signed update metadata. Preserve
    package/client-tool hashes, trusted keys, version checks and compatibility.
    Close with valid update installation, missing/tampered metadata rejection,
    restart/retry and rollback evidence on a disposable installed template.

29. **Publish GitHub release, GHCR and GitHub Pages from local builds — IN PROGRESS.**
    User correction, 2026-10-12: GitHub Actions was not requested. Remove the
    added repository workflows; run builds, tests, signing and image publication
    locally with the existing versioned commands. Do not make Actions permissions
    or hosted CI a release prerequisite and do not configure CI credentials.
    Portable frontend checkout uses the exact local submodule Git bundle and the
    existing remote components repository; a clean independent checkout passed.
    Pages is live at source `460f5c2`; all fourteen existing local UI flows passed
    against its real repository base path. Preserve that site and publish future
    static output from a locally built Pages branch.
    Five production images were locally published and pulled back with matching
    IDs. They are private. The unavailable upstream MinIO amd64 image was
    preserved byte-for-byte in GHCR; authenticated local builds can use it.
    The earlier unrequested Actions attempt received 403; retain that historical
    evidence, but it is not a required workflow or an open template gate.
    Prepare `v1.4.0` and upload the verified client-tools/update metadata/
    signature/checksum set to a draft release. The user will upload the DEB
    personally (2026-10-12 instruction); do not upload any DEB.
    The user also authorized an intermediate native MyScoutee GitHub release
    after the current deployment checks, followed by another after the remaining
    manual QA. Apply the same user-uploaded DEB and draft-release boundary there. Keep the release
    draft until the matching DEB is present and verified. Validate published artifacts
    separately from source tests. Document registry authentication requirements.

30. **Finish developer/operator documentation and the single PDF — IN PROGRESS.**
    The native PDF builder now reads a project-owned catalog. The template has
    one English HTML source and one stable seven-page PDF path, generated and
    visually checked. Installed/release instructions remain explicitly pending
    their delivered commands; the manual does not claim VMware/publication passed.
    Adapt the existing guides and manual builder to this project. Document clean
    setup, local/HTTP, dev/prod/E2E, tests/stress, seeds/reset, domain extension,
    KVM, local VMware/DEB, configuration, TLS, backup/restore, updates and releases.
    Explain how a developer builds a different application on these foundations.
    Keep English repository text, one canonical manual source and one generated
    getting-started PDF path; do not accumulate duplicate version-named PDFs in Git.
    Close with commands checked against the delivered template.

31. **Run final completeness and release acceptance — PENDING.**
    Revisit the inventory from step 2 against the actual artifact tree. Confirm
    every required capability has its implementation, data, configuration, test
    and operational path, and that no omitted product domain is accidentally needed.
    Reproduce the project from a clean checkout, complete submodule/build/test
    and install paths, and verify local, HTTP, E2E, KVM, monolith, DEB/update and
    Pages outcomes at their proper boundaries. Record measured CPU/latency limits,
    tested revisions and any real external blocker. Commit tested batches and
    mark the template complete only when required gates are satisfied, including
    the subsequently added backend dependency/removal gate in step 5.
    The user's final manual check is limited to logging in as one member and
    opening the SmartList and chat pages on VMware, dev monolith and KVM. Complete
    detailed automatic QA and prepare the environments before that handoff; do not
    delegate unfinished functional testing to the user.

## Current continuation point

**Resume at step 14 using the live execution pointer at the top of this file.**
The current DEV correction batch is newer than the qualification results below.
Close the local role-change/reload defect and retained loading checks first, then
continue with 21–24, 25, 26 and the user-started VMware gate in 27. The earlier
results below remain evidence for their named revisions, not an automatic PASS
for the corrected working tree.

### Previous qualified baseline

Template KVM Helm 6 passed the 17 backend cases, personal-chat lifecycle,
two consecutive audio/video calls (including a 35-second call across delegated
authorization renewal), guide, notifications, entry/SmartList and empty-admin
browser checks. The omitted nine admin/report/feedback/support routes returned
404 at Helm 5, with personal chat retained. All four Java services now expose
CPU/heap/request/histogram metrics through the actual Grafana datasource. See
`MSC122_TEMPLATE_KVM_20261012.json`. The source policy startup correction and
WebSocket fixes passed source KVM (Helm 154, all eight services) and source
monolith; the refreshed template monolith passed all 17 backend cases, the
chat lifecycle with two consecutive audio/video calls and the removed-admin gate.
The stacks were run serially and stopped between deployments. This closes step 5's template KVM removal gate;
it does not close release/performance qualification.

Step **5** source modularization is qualified in MyScoutee on KVM and monolith
through `37f5f708e`. The original features remain available there. The template
removal is implemented at parent `b520360` and frontend `9d035df`; 398 standard
JUnit checks, one embedded Mongo purge check, 293 frontend checks and production
build pass. The retained backend collection passes all 17 cases across its initial
run and focused follow-ups. The artifact monolith also rejects the nine omitted
routes, preserves two-member chat and does not create report/feedback collections.
See `MSC122_TEMPLATE_ADMIN_FEATURE_REMOVAL_20261012.json` for the final local/HTTP
browser results, test corrections, exact source revisions and evidence limits.

The minimal independent-service deployment in **25** is now qualified as
recorded above. Continue the remaining performance, host-package and release
work; do not reinstate removed features. Earlier
steps retain their explicitly pending provider, live Firebase, installed-host and
release checks; completed component or E2E checks do not close those gates.

The previous report/feedback/support UI and backend evidence describes the earlier
selection only. Ordinary member chat, API/MCP, operator configuration/TLS/update,
notifications/help/guide/consent, local/HTTP adapter modes and lifecycle jobs remain
selected. The current step number does not mean every earlier gate is complete.

## Outstanding source QA and template impact

Additional queued source defect: **CM-LOCAL-001**, reported on 2026-10-11,
covers delayed opening of the native frontend-local content-moderation settings
popup and missing SmartList loading feedback. The detailed reproduction and
acceptance notes are in the source
[frontend-local delay audit](../../../myscoutee-backend/guides/plans/FRONTEND_LOCAL_DELAY_AUDIT.md#open-user-reported-follow-up--2026-10-11).
First confirm the current revision; the observed build may be old. This product
screen is not selected in the template. Carry and verify a correction here only
when its actual owner is a shared component or another retained implementation.
The report is queued, not reproduced or passed.

The four source tasks previously identified with the user remain separate
qualification gates; scheduled testing is not a PASS:

| Source task | Retained-template impact |
| --- | --- |
| MSC-115 Work/Community | Product owners are omitted; a defect in shared components can still apply |
| MSC-116 API/MCP access | Direct: retained consent, credentials, permissions and revocation |
| MSC-117 audio/video calls | Direct: native call service and popup are selected; qualify two-party audio/video, mute/camera, hang-up, reconnect and isolation on template HTTP and KVM. Frontend-local calls stay unavailable, matching the native adapter. |
| MSC-119 MCP Events, roles and guide | Direct for the selected role/callback/guide paths; omitted product tools stay omitted |

Carry verified corrections from their actual source owner with the selected
contract and targeted regression evidence. Do not treat these pending source
checks as passed because template functional suites pass.

The source QA checklists and CSV/XLSX ledger now require a separate template
impact decision for each defect, source/template fix revisions and targeted
template evidence. The shared policy is in the source repository at
`guides/qa/checklists/README.md#template-regression-and-fix-propagation--msc-122`.
A source PASS never substitutes for a template result.
