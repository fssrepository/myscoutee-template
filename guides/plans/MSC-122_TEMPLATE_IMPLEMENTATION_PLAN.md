# MSC-122 — Complete Minimal Template Implementation Plan

Updated: 2026-10-11. Initial release version: **1.0.0**.

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

Step 13 must carry the corresponding `fileReplacements`, serve targets,
`production-serve` alias, `unit-test` build configuration, asset handling,
optimization/source-map settings and relevant package scripts. Keep the existing
`start`/`build` local default, `watch` dev default, mode-specific start/build/watch
commands, component build integration and build-log helpers where required.
Steps 20–22 verify tests against the correct environment; steps 25 and 28 verify
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

5. **Restore the complete dev/prod/E2E runtime structure — IN PROGRESS.**
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
   the complete Compose stacks are not yet assembled or qualified. Close with
   reproducible starts, stops and restarts in each mode and correct data isolation.

6. **Build small, coherent seeds and import/reset tooling — IN PROGRESS.**
   Reuse the canonical Mongo importer, index creation, local seed builder and
   media bootstrap. Create minimal members, the required admin/operator actors,
   approved personal-chat pairs/messages, demonstration cards, consent revisions,
   guide content, localization and required job/configuration rows. Keep physical
   `demo_db`, `e2e_db` and production-safe seed sets and their local equivalents.
   Production contains no synthetic members or activity. Preserve mutable
   operator configuration and uploaded media on reimport. Close with fresh import,
   controlled reset/reimport, stable references/counters and no omitted-domain IDs.

7. **Complete native identity and minimal profile flows — IN PROGRESS.**
   Reuse REAL/DEMO selection, sessions, Firebase/provider authentication,
   operator bootstrap, login/logout, session revocation and account permissions.
   Expose a small profile rather than MyScoutee's full product form. Preserve
   server-owned privileges and fields omitted from the request. Component checks
   already exist; close with real controller/session save/reload and rejection of
   forged identities, stale sessions and unauthorized profile writes.

8. **Complete account deletion and required background work — IN PROGRESS.**
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
   Email cleanup must join this worker when its owner is installed. Provider callbacks and full HTTP/UI/REAL-DEMO/KVM deletion
   qualification remain pending; this checkpoint does not close the step.

9. **Complete personal and support communication — IN PROGRESS.**
   Reuse chat/message storage, participant authorization, paging, mutations,
   read receipts, counters, WebSocket/Redis delivery and the existing support
   workflow. Carry the qualified separation of product conversation creators.
   Do not hide or block stored conversations based only on omitted domain names.
   Audit native call/signaling and email delivery dependencies for retained flows;
   retain the required infrastructure without importing event/campaign features.
   Close with two-user message/read/reconnect flows, support actions and isolation.

10. **Complete notifications, guide/help and consent — IN PROGRESS.**
    Retain native durable delivery, inbox state, collapse/idempotency, retries,
    push/device settings, guide state and versioned consent acceptance/enforcement.
    Use small template-owned content with the existing schemas. Separate required
    storage/background work from optional management screens. Close with delivery,
    unread/read transitions, guide completion, acceptance save/reload and a new
    consent revision in the appropriate REAL/DEMO database.

11. **Complete media, localization and geographic location — IN PROGRESS.**
    Reuse MinIO/S3 access, avatar/image editing, variants, private-media sessions,
    locale bundles and country/location lookup. Both localization and geographic
    location are required. Any operator location switch belongs to the existing
    configuration owner. Preserve batched queries and native indexes. Close with
    image upload/read/delete, unauthorized media denial, locale changes and valid,
    invalid and disabled-policy location cases across the applicable modes.

12. **Complete API and MCP integration without affiliate functionality — IN PROGRESS.**
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

13. **Build the frontend foundation and both adapter modes — PENDING.**
    Work inside the existing local frontend submodule and reuse the shared
    components. Select Angular configuration, application bootstrap, routing,
    session/store infrastructure, local and HTTP adapters, IndexedDB schema,
    seed loading, translation, error/loading handling and build scripts. Preserve
    all five configurations in the frontend environment inventory above and their
    native serve/build/test wiring.
    Close with working local persistence and HTTP persistence for retained flows,
    reload recovery and no silent local fallback in HTTP mode.

14. **Build the landing page and version display — PENDING.**
    Use the existing landing/login/branding components. Show a large central
    “Templates” title in the requested Gothic/calligraphic style, with the logo
    and login entry on opposite sides and the real build version badge. Preserve
    native demo/real entry, responsive layout and theme behavior. Close with both
    entry modes and matching version metadata in dev, production and Pages builds.

15. **Build the member SmartList example — PENDING.**
    Reuse SmartList, its supported views and native state/adapter patterns. Supply
    a few visually useful, project-neutral seeded cards rather than importing
    MyScoutee events to populate the screen. Keep the feature small but persistent
    in both local and HTTP modes. Close with view switching, relevant list controls,
    reload and matching local/API results using the same component implementation.

16. **Build the minimal member chat screen — PENDING.**
    Connect the existing chat components and adapters to the retained backend/local
    owners. Apply the requested compact chrome: avatar pill and collapse control;
    omit the extra top action and bottom add-item “+” menu. Keep the actions needed
    for the retained conversation flow. Close with message send/edit/read/reconnect
    checks and consistent state across two participants and reload.

17. **Build the avatar menu, profile editor and minimal settings — PENDING.**
    Reuse the existing menu, FormFlow/profile and image-editor entry points. Show
    only the small editable profile and settings required by retained features.
    Preserve logout, account deletion, consent and relevant notification/device
    controls. Remove the shared-list header and product actions from the side menu.
    Close with save/reopen, image editing and no clearing of hidden stored fields.

18. **Connect frontend guide, consent and notification surfaces — PENDING.**
    Reuse the existing top-menu access, drawers/dialogs, guide engine and consent
    flow. Seed guidance only for retained screens. Preserve unread/seen state,
    document versions and local/HTTP behavior. Close with initial entry, required
    consent, guide completion, notification navigation and persistence after reload.

19. **Complete the minimal admin and operator shells — PENDING.**
    Keep the admin menu empty of MyScoutee product dashboards. Include support and
    moderation actions needed by retained chat/accounts and the relevant API entry.
    Keep operator bootstrap, deployment/Firebase configuration, installation/TLS,
    update/status and other required operational controls. Omit virtual-company
    Registry screens. Reuse existing components and authority checks. Close with
    member/admin/operator access checks and the retained operations exercised.

20. **Consolidate JUnit and frontend unit/component coverage — IN PROGRESS.**
    Preserve `tools/run-backend-junit.sh`, native standard/embedded/integration
    groups and the existing frontend test runner. Select tests for retained
    contracts and add meaningful checks for composition or adapted behavior.
    Integration tests remain explicit; test runs must not touch dev/production
    data or trigger a second compiler. Close with a reproducible coherent batch,
    recorded counts and no unexplained failure or skipped required contract.

21. **Implement backend functional E2E — PENDING.**
    Reuse `e2e/shared`, `e2e/functional/backend`, fixture hooks, contract and
    concurrent runners. Select and adapt health, identity, profile, chat, media,
    guide/consent, notifications, API/MCP and operator flows. Preserve safe read
    mode, explicit mutation cases, isolated fixtures, cleanup and endpoint coverage
    auditing. Close with real HTTP mutation/read/reload checks and negative
    authorization checks on the standalone template E2E stack.
    The shared collection/HTTP/assertion/session helpers are now selected unchanged;
    ten initial contract/session cases run against the dev backend. This preliminary
    qualification does not close disposable E2E fixtures or mutation flows.

22. **Implement frontend E2E and local/HTTP parity checks — PENDING.**
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

23. **Restore stress/performance tooling and measure a coherent build — PENDING.**
    Reuse `e2e/stress/backend`, `e2e/stress/ui`, shared measurement helpers and
    microservice runtime probes. Select the minimal API/WebSocket/UI workload and
    small deterministic fixtures. Record revisions, data, warmup, concurrency,
    deployment limits, errors, throughput, latency percentiles, CPU and memory.
    Compare equivalent builds/workloads and investigate regressions. Close with
    repeatable commands and an honest baseline; do not repeatedly stress every edit.

24. **Compose and qualify the minimal microservices/KVM deployment — PENDING.**
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
    Close with focused KVM checks followed serially
    by monolith checks, CPU/log evidence, correct service credentials and cleanup.

25. **Build and verify production Docker artifacts — PENDING.**
    Reuse native build/stamping, nginx/media/runtime images, image manifests and
    production Compose. Build retained services with project-owned versioned tags
    and immutable identities. Include required assets, seeds and helper files;
    exclude source watchers, test fixtures and secrets. Close with startup and
    retained-flow checks using the actual built images, not a dev bind mount.

26. **Complete DEB, host operations and Debian/Netcup installation — PENDING.**
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

27. **Complete signed releases and the updater — PENDING.**
    Reuse native release discovery, canonical signing, manifests, staging,
    verification, durable update jobs and rollback/recovery. A DEB must never be
    published without the required matching signed update metadata. Preserve
    package/client-tool hashes, trusted keys, version checks and compatibility.
    Close with valid update installation, missing/tampered metadata rejection,
    restart/retry and rollback evidence on a disposable installed template.

28. **Wire GitHub release, GHCR and GitHub Pages publication — PENDING.**
    Use the established release/build commands from CI rather than duplicating
    packaging logic. Prepare `v1.0.0`, DEB/client-tools/signatures/checksums and
    versioned Docker image publication to GitHub Container Registry. Configure
    Pages for the existing static/local build with repository base path, deep-link
    fallback, service-worker/assets and version consistency. Resolve submodule
    checkout and required repository permissions/secrets before marking CI ready.
    Close only after published artifacts and Pages behavior are verified; distinguish
    a prepared workflow from an executed publication.

29. **Finish developer/operator documentation and the single PDF — PENDING.**
    Adapt the existing guides and manual builder to this project. Document clean
    setup, local/HTTP, dev/prod/E2E, tests/stress, seeds/reset, domain extension,
    KVM, DEB/Netcup, configuration, TLS, backup/restore, updates and releases.
    Explain how a developer builds a different application on these foundations.
    Keep English repository text, one canonical manual source and one generated
    getting-started PDF path; do not accumulate duplicate version-named PDFs in Git.
    Close with commands checked against the delivered template.

30. **Run final completeness and release acceptance — PENDING.**
    Revisit the inventory from step 2 against the actual artifact tree. Confirm
    every required capability has its implementation, data, configuration, test
    and operational path, and that no omitted product domain is accidentally needed.
    Reproduce the project from a clean checkout, complete submodule/build/test
    and install paths, and verify local, HTTP, E2E, KVM, monolith, DEB/update and
    Pages outcomes at their proper boundaries. Record measured CPU/latency limits,
    tested revisions and any real external blocker. Commit tested batches and
    mark the template complete only when required gates are satisfied.
    The user's final manual check is limited to logging in as one member and
    opening the SmartList and chat pages on VMware, dev monolith and KVM. Complete
    detailed automatic QA and prepare the environments before that handoff; do not
    delegate unfinished functional testing to the user.

## Current continuation point

Continue steps **4–6**: compose the selected backend using the native runtime,
prepare its own small seed and prove startup. The latest source baseline is
qualified; the template itself is still under construction. Its frontend contains
repository/submodule scaffolding only. Selected runtime files are unqualified
until the complete stack and fixtures are assembled. Steps 12–30 must not be
silently dropped merely because the first backend or UI screen starts.
