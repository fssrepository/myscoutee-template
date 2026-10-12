# Template release 1.4.0

The existing production builder and Debian lifecycle are selected from MyScoutee.
Project choices are data or small project-owned checks/recovery extensions:

- `scripts/release-project.json`: package identity and five managed images.
- `templates/install-policy.json`: selected defaults, seed list and generated secrets.
- `debian/purge-policy.json`: no product-owned external volumes.
- `release/runtime-scripts.json`: no source dev/test scripts installed on the host.
- `scripts/release-system-seeds.json`: four selected system catalogs; no exchange rates.
- `scripts/recovery-project.sh`: no extra Registry identity/state.
- `scripts/host-project-checks.sh` and `release/audit-project-seeds.sh`: retained content.

From a prepared source checkout, build with `bash packaging/release/build-production.sh` once, then package the completed images. Reuse a running build owner; do not run a second compiler. For a new candidate, set `MYSCOUTEE_DIST_DIR` to a fresh directory and use its paths below:

```bash
BUILD_ARTIFACTS=false bash packaging/release/package-production.sh
python3 packaging/release/prepare-release.py \
  --deb packaging/dist/myscoutee-template_1.4.0_amd64.deb \
  --client-tools packaging/dist/myscoutee-template-client-tools_1.4.0.tar.gz \
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

The compatibility range above is the tested template range, not a universal migration policy. Older 1.0.0 installations use the documented signed-client migration path in [migrations](../migrations/README.md).

Package builds, signing and image publication use local tooling. The frontend Pages workflow deploys committed `docs/`; it does not build or sign Debian packages. GHCR images require registry authentication. See the [operator guide](../../guides/manuals/MyScoutee_Template_Getting_Started_EN.pdf) for install and recovery commands.
