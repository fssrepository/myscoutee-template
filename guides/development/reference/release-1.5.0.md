# MyScoutee Template 1.5.0

This patch aligns the template with the released shared 1.5.0 Java, components,
packaging, nginx, MCP, E2E and QA dependencies. Their versions remain independent
of the application version. The application release stays at 1.5.0; MSC-128 source commits are consolidated in each repository.

- Account login responses cannot override the authenticated session kind or cause
  account tokens to enter plaintext demo storage. A pending encrypted account
  write cannot replace a newly selected demo session.
- Remove obsolete authentication implementation aliases and the removed bootstrap
  record from the class contract test to support the released Base 1.5.0 JARs.
- Build both production HTTP and standalone GitHub Pages frontend profiles. The
  standalone profile uses `/myscoutee-template-frontend/` and its local adapters.
- Build and sign the template's own five-image Debian package with the existing
  installer, backup and update mechanisms. Shared dependency images keep their
  pinned release identities.

The application-code candidate passed 440 backend tests and 33 session tests;
27 directly affected backend tests also passed in the main project. The preceding
1.5.0 package was installed from the signed 1.4.1 baseline on VMware with a complete
backup and passed the package host qualification. The final 1.5.0 package is rebuilt from the consolidated source commits so its
embedded production frontend contains the latest frontend build. Exact hashes,
source identities and artifact verification are recorded in the release assets.
The previous VMware qualification applies to the earlier candidate; no additional
VMware installation is performed for the final rebuild, as requested.

The shared Packaging 1.5.0 verifier correction is published from
`01b0e75fb101acddfceb55c018823c6e19bf7f5c` and installed through the registry lock.
It now checks the current shell `no-cache` policy and anonymous Operator HTTP 401
contract, while retaining strict update-file caching and sensitive-data checks.
All 11 regression cases pass with this project's configuration, including from
the packed dependency; the complete Packaging suite also passes.

The separately signed replacement candidate is
`build/packaging/verifier-correction/1.5.0/myscoutee-template_1.5.0_amd64.deb`,
SHA-256 `5498fa131f8728ce81db5163f456bdb0c0b9ab893a0cb607b0337fcddeb05fb1`.
Only its bundled client-tools archive differs from the preceding final DEB;
all other payload files, including this project's own images, are byte-identical.
Package/client signatures and the signed update catalog are verified. This
replacement has not been installed on VMware. KVM runtime qualification was not
performed because the local libvirt service was unavailable. No manual browser
checklist completion is claimed.

The backend release remains a draft pending the owner's DEB upload. Upload the
exact file identified by the signed manifest; publish only after all files listed
in `RELEASE.json` have been verified. Signing keys are kept outside the repository.
