# MyScoutee Template 1.4.5 — applicable MSC-126 changes

[MSC-126](https://github.com/fssrepository/myscoutee-roadmap/issues/164) applies the
loading, catalog and UI-caption improvements to the template's own application.

- Retain the template's 12 HTTP/local adapter pairs, own routes and EN/HU catalogs:
  1,816 messages per language, version `2026.10.21.2`. HTTP startup excludes local
  services/seeds; standalone startup excludes HTTP services. The full English
  catalog is not included by the 21-byte version metadata import.
- Add migration 019 with 52 applicable semantic-key renames/104 pinned rows. It uses
  the existing numbered runner with retry/customization/version-ordering checks.
  Deploy backend/data changes with the matching source's migration/seed inputs;
  image replacement alone does not update Mongo catalogs.
- Retain the existing 18 WebP variants behind six profile references. Main's
  article/room migration and lifecycle fixtures do not apply.
- Use existing fullscreen mobile setup presentation, shared Components 1.4.5,
  grouped startup output and filled icon-font discovery. GitHub Pages uses its
  own `/myscoutee-template-frontend/` base path and standalone build profile.
- Validation: 92 focused frontend cases, then 30 directly affected caption/metadata
  cases; 7 backend i18n cases; 8 bundle grouping cases; real Mongo migration checks,
  seed audits and both optimized builds passed. HTTP has 31 application chunks;
  standalone 27, with two startup files each.

This source/image release is compatible with the 1.4.1 template baseline. It
publishes the updated API/nginx images, standalone bundle and source tags. A new
full template DEB installation/runtime qualification is not claimed. The existing
full-package build includes the corresponding migration and seed inputs.

| Service | GHCR aliases | Immutable digest |
| --- | --- | --- |
| profilemanagement | `ghcr.io/fssrepository/myscoutee-template-api:1.4.5-prod`, `ghcr.io/fssrepository/myscoutee-template-api:1.4.5` | `sha256:68a0fc5bb6dd291a7817e6a00ded36b61ab957bc58aaef6719ed890a5d06d1d5` |
| nginx | `ghcr.io/fssrepository/myscoutee-template-nginx:1.4.5-prod`, `ghcr.io/fssrepository/myscoutee-template-nginx:1.4.5` | `sha256:77e8734b91605fa5620b2c10b99f965ecf48fbe44a0be81c17cec5707c495ccf` |

The task and measured effort are shared with the main Project and counted once in the canonical roadmap ledger.
