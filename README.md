<h1 align="center">MyScoutee Template</h1>

<p align="center">Common infrastructure from MyScoutee, intended for reuse in new projects.</p>

<p align="center">
  <a href="LICENSE">CC BY 4.0 License</a> ·
  <a href="BRANDING.md">Logo &amp; attribution</a> ·
  <a href="CONTRIBUTING.md">Contributing</a>
</p>

## About

This repository is the starting point for extracting MyScoutee's common
infrastructure into a reusable project template.

The active, numbered work plan is
[`MSC-122 Template Implementation Plan`](guides/plans/MSC-122_TEMPLATE_IMPLEMENTATION_PLAN.md).
It tracks the complete minimal framework, including frontend local/HTTP,
dev/prod/E2E, functional and stress tests, KVM, installation, signed updates and
GitHub release/image/Pages publication. The initial release version is **1.4.0**.

**Current status:** the minimal local/HTTP frontend, monolith and four-service KVM
application are implemented. Automated checks cover identity/consent, visual
SmartList, profile/avatar, ordinary chat and video calls, guides, notifications,
API/MCP and the selected operator controls. Report/feedback/support and other
MyScoutee product implementations are omitted. KVM evidence is in
[`MSC122_TEMPLATE_KVM_20261012.json`](guides/qualification/evidence/MSC122_TEMPLATE_KVM_20261012.json).
Packaging, installed updates, final performance comparison and publication remain
in progress; VMware installation is explicitly deferred. The
[QA table and execution notes](guides/qa/README.md) keep these gates separate.

Clone the parent repository, then run:

```sh
tools/bootstrap-submodules.sh
npm ci --prefix frontend
npm --prefix frontend run start:local
```

The [developer/operator guide](guides/manuals/template-getting-started-en.html)
and its [single PDF](guides/manuals/MyScoutee_Template_Getting_Started_EN.pdf)
cover the selected workflows and current qualification limits.

## Target and local repository layout

The deliverable is a complete minimal counterpart of `myscoutee-backend`, with
its own backend/frontend shell, development workflow, versioned releases,
local VMware Debian installation, KVM lab and getting started guide/PDF. Backend
preparation has passed its source KVM and monolith gates; the standalone template
composition is in progress. Its selected backend and frontend are runnable; installed deployment and release
qualification are still in progress.

The template retains the backend structure and operational workflows, with smaller
domain implementations where appropriate. Minimal screens and seed data must not
discard useful generic schemas or extension points: a developer should be able
to build a larger application on the same foundations. Simplifications require
review of existing dependencies and the cost of restoring the capability later.
MyScoutee virtual-company functionality
(Registry enrollment, network accounting and global identity) is excluded; generic
bootstrap, deployment configuration, TLS and software updates remain part of the
foundation.

Backend capabilities and screens are selected separately. The minimal template
centers on a member SmartList and chat, with the member data structures they need.
It retains native identity behavior, including REAL/DEMO sessions, Firebase,
login/logout and account lifecycle, backed by persisted data and a small seed.
MyScoutee event, matchmaking, payment and Registry models are excluded.

Retain the native account/workspace identity fields where the selected models
need them. The initial template does not include the community/group domain or
seed a group merely to satisfy an unrelated dependency. The shell includes an avatar menu with a small profile and
image editor, guide/notification access, minimal settings and the real consent
flow, including versioned documents, persisted acceptance and enforcement.

The minimal template
has no administration screen by default, while required account-deletion jobs,
durable state, schemas and minimal project-owned seed data remain. Help/guide
storage can be retained without its management UI. The operator installation
and configuration surface is part of the foundation. Omitting a screen must not
silently remove the background behavior required by the remaining application.

[`frontend`](https://github.com/fssrepository/myscoutee-template-frontend)
is a separate Git repository, pinned here as a submodule. Run
`tools/bootstrap-submodules.sh` to initialize it and its shared `components`
submodule. Commit and push frontend changes there first, then commit its updated
gitlink here. The [live demo](https://fssrepository.github.io/myscoutee-template-frontend/)
is published from the frontend repository's `master` branch and `docs/` directory.

## Selected backend foundations

The file-level source manifest is [`tools/upstream-sources.json`](tools/upstream-sources.json).
It pins upstream revisions and hashes and records each deliberate adaptation.
The native `Users` model omits affiliate registration; the deployment document
and DTOs omit payments. Remaining selected fields keep their upstream names and
semantics. No events, matchmaking, payment or Registry implementation is included.

Account selection is taken from the MyScoutee revision already checked on KVM
and monolith. Deployment settings retain branding, privacy contacts, integration
base URL, encrypted credentials and verified Firebase activation. REAL and DEMO
separation, optimistic locking and credential redaction remain in place.

The native policy engine keeps its cached rules and explicit denials. Only the
12 implemented account/chat/consent/media-write policy records are selected from
the upstream seed. Public help, terms, privacy and guide reads retain their native
revision/consent schemas; revision administration and community scope are omitted.
Durable notification delivery retains native collapse/idempotency, inbox pagination,
unread counters, REAL/DEMO routing, retries, Firebase delivery snapshots and
change-stream wakeups. The template omits product group recipient splitting,
event invitations and community activity counters. Native delivery limits are
configured under `myscoutee.notifications`; omitted values use existing defaults.
The deployment-scoped outbox and account-purge rules are seeded. Account purge
keeps the native 30-day grace period; `users.account-purge-window-days` selects
the native bounded range of 1–365 days. The current purge selection covers the
installed identity/media/chat/help/notification owners. It keeps durable retries
and deletes the account only after those cleanup stages succeed.

Personal/support chat retains native message storage, paging, read receipts,
unread counters, stable message identity, WebSocket/Redis transport and durable
push delivery. Product chat creators, metrics and event navigation are omitted.
A personal chat requires its existing approved contact pair; reopening it does
not overwrite the conversation. Initial live HTTP checks now verify the seeded
two-user conversation and messages; full message mutation/reconnect and UI
qualification remain outstanding.

Localization keeps native REAL/DEMO caches and change-stream invalidation.
Location handling keeps native geospatial lookup and targeted coordinate writes;
the region eligibility gate is configurable. The small profile response omits
product fields without clearing those fields in the stored account on save.

Staged test evidence is in [`guides/qualification/evidence`](guides/qualification/evidence).

With Java 25 installed, run the selected tests from the repository root:

```sh
tools/run-backend-junit.sh standard
```

This uses isolated JUnit outputs and does not start a server or connect to an
application database. Passing these tests does not qualify installation, a live
Firebase project, frontend flows or the unfinished service composition.

## License — free to reuse with required credit

The repository's original code and documentation are licensed under
[Creative Commons Attribution 4.0 International (CC BY 4.0)](LICENSE).
Anyone may copy, modify and redistribute them, including commercially.

**Attribution is a condition of the license, not an optional request.**
When sharing the material, including modified versions, follow **Section 3(a)**
of [LICENSE](LICENSE): credit **MyScoutee (fssrepository)**, retain the required
notices, link to the source and license, and indicate changes.

Place the credit somewhere readers or users can access it, as appropriate to
what you share: a README, documentation, an About/Credits page or a website
footer. The full, unmodified standard license text is in [LICENSE](LICENSE).
The attribution information for this repository is supplied in the credit
below. This requirement applies when you share the licensed material; private
use alone does not require publishing a credit.

Example credit (also describe changes if you made any):

> Based on [MyScoutee Template](https://github.com/fssrepository/myscoutee-template)
> by MyScoutee (fssrepository). Copyright © 2026, fssrepository.
> Licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
> Provided without warranties; see [LICENSE](LICENSE), Section 5.

MyScoutee logos and brand artwork are excluded from CC BY 4.0 and follow
[BRANDING.md](BRANDING.md). Separately identified third-party material retains
its own license and notices.

## Make it your own

Use your own project name and logo. Any MyScoutee logo in template source,
examples or UI is demonstration branding: remove or replace it wherever it
identifies your product, including an adapted README header.

You may display the MyScoutee logo in a clearly labeled source-credit section
or when demonstrating the original MyScoutee project. It must not suggest that
your project is MyScoutee, an official MyScoutee service, or endorsed by its
maintainers. A text credit satisfies attribution; displaying the logo is not
required. See [BRANDING.md](BRANDING.md).

## Contributing

Bug reports, documentation improvements and focused pull requests are welcome.
Read [CONTRIBUTING.md](CONTRIBUTING.md) to get started.

## Related project

[MyScoutee Components](https://github.com/fssrepository/myscoutee-components)
is the companion repository for reusable UI components.
