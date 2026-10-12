# Template release 1.0.0

The existing production builder and Debian lifecycle are selected from MyScoutee.
Project choices are data or small project-owned checks/recovery extensions:

- `scripts/release-project.json`: package identity and five managed images.
- `templates/install-policy.json`: selected defaults, seed list and generated secrets.
- `debian/purge-policy.json`: no product-owned external volumes.
- `release/runtime-scripts.json`: no source dev/test scripts installed on the host.
- `scripts/release-system-seeds.json`: four selected system catalogs; no exchange rates.
- `scripts/recovery-project.sh`: no extra Registry identity/state.
- `scripts/host-project-checks.sh` and `release/audit-project-seeds.sh`: retained content.

From a prepared source checkout, after building the images:

```bash
BUILD_ARTIFACTS=false bash packaging/release/package-production.sh
python3 packaging/release/prepare-release.py \
  --deb packaging/dist/myscoutee-template_1.0.0_amd64.deb \
  --client-tools packaging/dist/myscoutee-template-client-tools_1.0.0.tar.gz \
  --private-key /private/path/signing-key.pem \
  --tag v1.0.0 --channel stable --repository fssrepository/myscoutee-template \
  --minimum-version 1.0.0 --maximum-version 1.0.0
```

The signer refuses existing signed output. Use a fresh output directory for a
new candidate, keep the key outside it, and never publish a changed artifact
with stale signatures. Upload every file listed by `RELEASE.json`; publish only
after the complete draft release has been verified. The fixed filename
`myscoutee-update.json` is the existing updater protocol, not a product dependency.

The checked-in `release-public.pem` has SPKI fingerprint
`sha256:74255e852bf3720b81b990d0568f5b567124d6fb12335063f0309ccb522c5c03`.
Pin it independently. A project derived from this template owns a different key.
No private key or CI signing secret is stored in this repository.

Local VMware installation/update/recovery is authorized and being qualified.
Do not use Netcup. Artifact and host-script checks alone do not prove installation.
Builds, tests, signing and publication run locally; GitHub Actions is not required.
Pages is published. GHCR images are private and require registry authentication.
