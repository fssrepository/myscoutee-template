# Template operator client tools

These are the shared MyScoutee release clients with the template's trusted
`release-project.json`. The policy fixes the expected package to
`myscoutee-template`; the installer rejects other packages before SSH access.
The archive excludes local credentials and product-specific target files.

Build the client archive without starting an application or building images:

```bash
MYSCOUTEE_VERSION=1.4.0 bash packaging/release/package-client-tools.sh
```

For an extracted, independently verified release bundle:

```bash
cp install.env.example install.env
chmod 0600 install.env
# Set the local VMware Debian target and independently trusted release inputs.
./install.sh ./myscoutee-template_1.4.0_amd64.deb
```

Use the matching signed package and client bundle from the same qualified release.

`prepare-debian13-minimal.sh` is an explicit host preparation command. It checks
Debian 13/amd64 and preserves an existing runtime instead of removing conflicts.
The installer never invokes it implicitly. `forward-localhost.sh` uses the same
protected SSH configuration and supports `LOCAL_FORWARD_PORTS_OVERRIDE=18080:80,18443:443`.
`purge.sh` explicitly removes the selected package; use ordinary package removal
when deployment state must be retained.

Release signatures, hashes and the independent key fingerprint are checked by
`verify-release.sh`. A downloaded public key alone is not a trust anchor. The
explicit unsigned override is for local development packages only.

The archive includes the read-only `verify-deployment` commands. They require an
explicit target URL and do not perform SSH, install, seed or reset operations.
Temporary partial-image patch helpers are not selected for the first minimal
release; the signed package updater is the supported upgrade path.

## Upgrading the initial 1.0.0 template

Use the signed client installer for the one-time move to 1.4.0. It stops the old
stack before replacing its files, migrates the template runtime account from
10001 to 10002, and preserves file contents, modes, secrets and data. A conflicting
foreign account is rejected. After this host migration, use the normal in-app
updater. The old worker cannot safely change its own running numeric identity.
