# MyScoutee Template

A full-stack Angular and Java template extracted from MyScoutee, with authentication,
profiles, chat, calls, notifications and reusable UI components.

## Downloads

### Installer

The current installer release is **1.4.1**. Download the Debian package and
client tools from [MyScoutee Template Releases](https://github.com/fssrepository/myscoutee-template/releases/tag/v1.4.1).

### Documentation

| Document | Links |
| --- | --- |
| Template guide | [Getting Started (PDF)](guides/publication/manuals/MyScoutee_Template_Getting_Started_EN.pdf) |
| Guide sources | [HTML source](guides/publication/manuals/template-getting-started-en.html) · [Build instructions](guides/publication/manuals/README.md) |
| Shared components | [MyScoutee Components Guide (PDF)](https://github.com/fssrepository/myscoutee-components/blob/master/guides/publication/manuals/MyScoutee_Components_Guide_EN.pdf) |
| Frontend | [Frontend repository](https://github.com/fssrepository/myscoutee-template-frontend) · [Live demo](https://fssrepository.github.io/myscoutee-template-frontend/) |
| Native nginx | [Implementation and tests](https://github.com/fssrepository/myscoutee-nginx) · [Guide (PDF)](https://github.com/fssrepository/myscoutee-nginx/blob/master/output/pdf/MyScoutee_Nginx_Guide.pdf) |
| Releases | [Release notes](guides/development/reference/release-1.4.1.md) · [Build and signing guide](guides/development/reference/release-building.md) |
| Project | [GitHub Project](https://github.com/users/fssrepository/projects/6) · [Roadmap](https://github.com/fssrepository/myscoutee-roadmap) |
| Implementation | [Implementation checklist](tests/qa/QA_MATRIX.csv) |

## Source checkout

Initialize the recorded frontend commit with Git after cloning. Commit or preserve
any existing frontend changes before updating its checkout:

```sh
git submodule sync -- frontend
git submodule update --init --recursive --checkout -- frontend
npm --prefix frontend ci
```

The frontend uses the released components npm dependency. E2E helpers are also
versioned npm dependencies. nginx and MCP use released GHCR images, with no
source submodules or C/C++/Go compilation in this application. Project-specific
nginx files live in `config/nginx/`; MCP identity and API schemas stay in
`config/mcp/product.json` and `config/mcp/contract.json`. Common code and nginx
startup scripts are maintained in their separate source repositories.
The source revision and adaptation inventory is `config/upstream-sources.json`.

## Shared release dependencies

The shared Java modules, components, packaging helpers, E2E and QA helpers use
release **1.5.0**. Maven/npm versions and lockfiles are committed; nginx and MCP
base images are pinned to the released 1.5.0 digests. Application installer
releases have their own version history.

Java dependencies come from
[MyScoutee Base GitHub Packages](https://github.com/fssrepository/myscoutee-base/releases/tag/v1.5.0),
including the compatible `com.raxim.myscoutee.base:platform:1.5.0` BOM. Put `gpr.user` and
`gpr.key` in your private `~/.gradle/gradle.properties`; the token needs
`read:packages`. CI can instead provide `GITHUB_ACTOR` and `GITHUB_TOKEN`.
The optional `MYSCOUTEE_BASE_REPOSITORY` override selects an explicit Maven
repository for development.

The versioned `.npmrc` files select `https://npm.pkg.github.com` for
`@fssrepository`. Keep the registry token in your private `~/.npmrc`, then use
`npm ci` in each package directory. Credentials never belong in the repository.
Dev Compose mounts the private Gradle properties read-only. Docker builds use
the `github-packages` BuildKit secret; `MYSCOUTEE_GITHUB_PACKAGES_PROPERTIES`
selects a different private credentials file when needed.

## Optional local analysis

[SonarQube](https://github.com/fssrepository/myscoutee-sonarqube) scans backend,
frontend, components and microservices independently of application startup,
builds and tests. This repository retains only [analysis configuration](config/sonarqube/project.json);
shared tooling lives in the `myscoutee-sonarqube` sibling repository.

## License

[CC BY 4.0](LICENSE). Credit **MyScoutee (fssrepository)**, retain notices and
indicate changes. Copyright © 2026, fssrepository.
Use your own name and logo; see [Branding](BRANDING.md).
