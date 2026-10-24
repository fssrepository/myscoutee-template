# Template release 1.4.5

The current compatible source/image release is **1.4.5**: updated API/nginx, own EN/HU catalogs, migration 019 and standalone build. [Release notes](release-1.4.5.md). The signed Debian baseline remains 1.4.1.

## Signed template release 1.4.1

The signed 1.4.1 candidate is installed and verified on VMware; its five images
are published. See [release notes](release-1.4.1.md) and
[artifact evidence](https://github.com/fssrepository/myscoutee-template/blob/9ae366ed68f93fcaad03e96b2010d07d0c1b1589/guides/qualification/evidence/RELEASE_1.4.1_20261017.json).
The owner’s DEB upload is published; signatures, all nine asset identities and
canonical public update discovery are verified. Use
`MYSCOUTEE_VERSION=1.4.1` and a fresh output directory with the existing builders.
The compatibility baseline is 1.4.0.

## Previous 1.4.0 release and tooling

The existing production builder and Debian lifecycle are selected from MyScoutee.
Project choices are data or small project-owned checks/recovery extensions:

- `scripts/release-project.json`: package identity and five managed images.
- `templates/install-policy.json`: selected defaults, seed list and generated secrets.
- `config/packaging/debian/purge-policy.json`: only the selected nginx cache volume; no backend-specific volumes.
- `config/packaging/release/runtime-scripts.json`: no source dev/test scripts installed on the host.
- `scripts/release-system-seeds.json`: four selected system catalogs; no exchange rates.
- `scripts/recovery-project.sh`: no extra Registry identity/state.
- `scripts/host-project-checks.sh` and `config/packaging/release/seed-audits/`: retained content policies.

From a prepared source checkout, build with `npm --prefix config/packaging run release -- build-production` once, then package the completed images. Reuse a running build owner; do not run a second compiler. For a new candidate, set `MYSCOUTEE_DIST_DIR` to a fresh directory and use its paths below:

```bash
BUILD_ARTIFACTS=false npm --prefix config/packaging run release -- package-production
python3 ../myscoutee-packaging/release/signing/prepare-release.py \
  --project-root "$PWD" \
  --deb build/packaging/dist/myscoutee-template_1.4.0_amd64.deb \
  --client-tools build/packaging/dist/myscoutee-template-client-tools_1.4.0.tar.gz \
  --private-key /private/path/signing-key.pem \
  --tag v1.4.0 --channel stable --repository fssrepository/myscoutee-template \
  --minimum-version 1.4.0-qa.1 --maximum-version 1.4.0
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

The [1.4.0 release](https://github.com/fssrepository/myscoutee-template/releases/tag/v1.4.0) is published. Its assets include source and image identities. Installed VMware qualification and the later UI refresh retain separate artifact hashes in the qualification records.

The compatibility range above is the tested template range, not a universal migration policy. Older 1.0.0 installations use the documented signed-client migration path in [migrations](../../../config/packaging/migrations/README.md).

Package builds, signing and image publication use local tooling. The frontend Pages workflow deploys committed `docs/`; it does not build or sign Debian packages. GHCR images require registry authentication. See the [operator guide](../../publication/manuals/MyScoutee_Template_Getting_Started_EN.pdf) for install and recovery commands.

The package-managed nginx IP blocklist and per-address response messages are
documented in [the operator blocklist guide](nginx-blocklist.md).

## Release configuration

`config/packaging/release/settings.json` overrides the common packaging dependency's
`release/lib/defaults.json`; environment overrides retain their existing precedence.
Runtime script selection is in `config/packaging/release/runtime-scripts.json`.
Product seed audit predicates and their selection are under the same configuration
directory. Image identity stays in `config/packaging/release-project.json`.
