# MyScoutee Template

A full-stack foundation for substantial applications, extracted from MyScoutee.
It includes member, administrator and operator interfaces, identity and consent,
profiles, SmartList, chat and calls, notifications, guides and API/MCP integration.

[Live demo](https://fssrepository.github.io/myscoutee-template-frontend/) ·
[Download 1.4.0](https://github.com/fssrepository/myscoutee-template/releases/tag/v1.4.0) ·
[Frontend](https://github.com/fssrepository/myscoutee-template-frontend)

Angular supports browser-local and HTTP modes. The Java backend runs as a
monolith or four services in the KVM lab. Debian installation, signed updates,
backup and recovery use the same operational foundations as MyScoutee.
The template omits its matchmaking, events, payments and Registry business domains.

## Start locally

Requires Node.js 24 and npm 11. HTTP development also requires Java 25 and Docker Compose.

```sh
git clone https://github.com/fssrepository/myscoutee-template.git
cd myscoutee-template
tools/bootstrap-submodules.sh
npm ci --prefix frontend
npm --prefix frontend run start:local
```

Open `http://localhost:4200`. Local mode uses seeded browser data; live messaging
and calls require an HTTP backend. Use one compiler/stack at a time.

## Documentation

| Document | Link |
| --- | --- |
| Architecture, setup, extension, testing, deployment and recovery | [Guide PDF](guides/manuals/MyScoutee_Template_Getting_Started_EN.pdf) |
| Editable guide and regeneration | [HTML](guides/manuals/template-getting-started-en.html) · [Instructions](guides/manuals/README.md) |
| Shared frontend components, catalogue and integration | [Components PDF](https://github.com/fssrepository/myscoutee-components/blob/master/guides/manuals/MyScoutee_Components_Guide_EN.pdf) |
| Release tooling | [Build and signing](packaging/release/README.md) |
| Project and roadmap | [GitHub Project](https://github.com/users/fssrepository/projects/6) · [Roadmap](https://github.com/fssrepository/myscoutee-roadmap) |
| Delivery progress | [Numbered checklist](guides/plans/MSC-122_TEMPLATE_IMPLEMENTATION_PLAN.md) |

## QA and measured usage

Template regression **1–32 PASS / CLOSED** on October 17, including call/notification
deltas and cleanup. [Evidence](guides/qa/evidence/general/MSC115-TEMPLATE-REGRESSION.json)
retains the tested scope. [Usage and effort](https://github.com/users/fssrepository/projects/6)
records **1,304,324,997 tokens** through October 17, 07:51:53 UTC. The published
1.4.0 installer verification remains historical; no new installer is claimed.

## License and credit

Original code and documentation: [CC BY 4.0](LICENSE). When sharing, credit
**MyScoutee (fssrepository)**, link to the source and license, retain notices
and indicate changes. Copyright © 2026, fssrepository.
Use your own product name and logo; MyScoutee artwork follows [BRANDING.md](BRANDING.md).
See [Contributing](CONTRIBUTING.md) for contributions.
