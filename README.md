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

**Current status:** repository groundwork. The template implementation, setup
instructions and runnable examples have not been published here yet.

## Target and local repository layout

The deliverable is a complete minimal counterpart of `myscoutee-backend`, with
its own backend/frontend shell, development workflow, versioned releases,
Debian/Netcup installation, KVM lab and getting started guide/PDF. Backend
separation is being verified in MyScoutee first; this checkout is not runnable
yet.

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

Retain group/workspace scoping where identity and chat need it, initially with a
minimal seeded group. The shell includes an avatar menu with a small profile and
image editor, guide/notification access, minimal settings and the real consent
flow, including versioned documents, persisted acceptance and enforcement.

The minimal template
has no administration screen by default, while required account-deletion jobs,
durable state, schemas and minimal project-owned seed data remain. Help/guide
storage can be retained without its management UI. The operator installation
and configuration surface is part of the foundation. Omitting a screen must not
silently remove the background behavior required by the remaining application.

`frontend` is a Git submodule backed for now by the local repository
`/home/raxim/workspace/myscoutee-template-frontend`. Its `components` submodule
uses the existing remote `https://github.com/fssrepository/myscoutee-components.git`.
The local frontend may receive its own remote later; update its origin and the
parent's `git submodule set-url frontend <repository-url>` together at that point.
The current absolute local URL is groundwork for this workspace, not a portable
published template release.

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
