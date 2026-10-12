# MSC-122 — Complete Minimal Template Implementation Plan

Updated: 2026-10-12. Initial release version: **1.0.0**.

This is the active, numbered implementation checklist for `myscoutee-template`.
It continues the source-separation work documented in
[`myscoutee-backend`](../../../myscoutee-backend/guides/plans/MSC-122_TEMPLATE_EXTRACTION_PLAN.md).
Update this file as work progresses; keep detailed test results in
[`../qualification/evidence`](../qualification/evidence).

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
| Installed release | Docker images, signed DEB/update metadata, host helpers, Netcup/Debian setup, backup/restore and upgrade verification |
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

## Status and working rules

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
   `1.0.0`; propagate that version through the native build stamping, frontend
   badge, JAR, DEB, image tags, release metadata and Git tag `v1.0.0`. Retain
   attribution and licensing. Verify a clean checkout/submodule initialization.
   Before remote CI publication, resolve the local frontend URL: a GitHub runner
   cannot fetch `/home/raxim/workspace/myscoutee-template-frontend`. Keep this as
   an explicit publication dependency rather than a silently broken checkout.

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

5. **Audit, modularize and remove omitted admin features from the template backend — IN PROGRESS (template KVM qualification pending).**
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

24. **Restore stress/performance tooling and measure a coherent build — PENDING.**
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
    Close with focused KVM checks followed serially
    by monolith checks, CPU/log evidence, correct service credentials and cleanup.

26. **Build and verify production Docker artifacts — PENDING.**
    Reuse native build/stamping, nginx/media/runtime images, image manifests and
    production Compose. Build retained services with project-owned versioned tags
    and immutable identities. Include required assets, seeds and helper files;
    exclude source watchers, test fixtures and secrets. Close with startup and
    retained-flow checks using the actual built images, not a dev bind mount.

27. **Complete DEB, host operations and Debian/Netcup installation — PENDING.**
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
    Close with a fresh isolated Debian/KVM installation and applicable Netcup setup
    verification, restart, backup/restore and data-preserving removal checks.
    Inventory and prepare the VMware deployment requested for the final user smoke
    check, reusing the available installation workflow. Record its evidence
    separately from the KVM lab; one hypervisor does not qualify the other.

28. **Complete signed releases and the updater — PENDING.**
    Reuse native release discovery, canonical signing, manifests, staging,
    verification, durable update jobs and rollback/recovery. A DEB must never be
    published without the required matching signed update metadata. Preserve
    package/client-tool hashes, trusted keys, version checks and compatibility.
    Close with valid update installation, missing/tampered metadata rejection,
    restart/retry and rollback evidence on a disposable installed template.

29. **Wire GitHub release, GHCR and GitHub Pages publication — PENDING.**
    Use the established release/build commands from CI rather than duplicating
    packaging logic. Prepare `v1.0.0`, DEB/client-tools/signatures/checksums and
    versioned Docker image publication to GitHub Container Registry. Configure
    Pages for the existing static/local build with repository base path, deep-link
    fallback, service-worker/assets and version consistency. Resolve submodule
    checkout and required repository permissions/secrets before marking CI ready.
    Close only after published artifacts and Pages behavior are verified; distinguish
    a prepared workflow from an executed publication.

30. **Finish developer/operator documentation and the single PDF — PENDING.**
    Adapt the existing guides and manual builder to this project. Document clean
    setup, local/HTTP, dev/prod/E2E, tests/stress, seeds/reset, domain extension,
    KVM, DEB/Netcup, configuration, TLS, backup/restore, updates and releases.
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

Step **5** source modularization is qualified in MyScoutee on KVM and monolith
through `37f5f708e`. The original features remain available there. The template
removal is implemented at parent `b520360` and frontend `9d035df`; 398 standard
JUnit checks, one embedded Mongo purge check, 293 frontend checks and production
build pass. The retained backend collection passes all 17 cases across its initial
run and focused follow-ups. The artifact monolith also rejects the nine omitted
routes, preserves two-member chat and does not create report/feedback collections.
See `MSC122_TEMPLATE_ADMIN_FEATURE_REMOVAL_20261012.json` for the final local/HTTP
browser results, test corrections, exact source revisions and evidence limits.

Next, compose the minimal independent services in **25**, using the same selected
source owners. This is required to close step 5's outstanding template KVM gate;
it is not permission to reinstate removed features. Reuse the native shell and
separate its hard-coded project/lab inputs at their original owners before copying.
Then complete remaining runtime, performance, packaging and release gates. Earlier
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
