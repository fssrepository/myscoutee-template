# Verify an already running template deployment

Run explicitly against the local VMware target after installation; the installer
does not invoke these external checks. No default remote target is selected.

```bash
node packaging/verify-deployment/run.mjs --list
node packaging/verify-deployment/run.mjs --url https://deployment.example --expected-version 1.0.0
```

The shared read-only verifier checks transport/TLS, headers, SPA/PWA/version and
asset consistency, backend health, public configuration, localization and denied
anonymous operator access. `project-checks.mjs` selects the template's four public
document kinds. MyScoutee landing ideas/slides are deliberately not selected.

For a private CA use `NODE_EXTRA_CA_CERTS=/path/to/ca.pem`. TLS verification stays
enabled. `--demo-session` selects the packaged Explore data route when appropriate.
For a custom HTTPS port, supply `--http-url` to test the corresponding redirect.

The independent `browser-smoke.mjs` opens its own headless profile for a read-only
render check. It performs no clicks or forms and does not reuse a user's browser.
Its help documents the explicit private-certificate fixture option.

These commands qualify the supplied runtime only. Local production-image results
do not prove Debian install, update, backup, restore or VMware behavior.
