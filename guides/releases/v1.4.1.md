# MyScoutee Template 1.4.1

Task: [MSC-123](https://github.com/fssrepository/myscoutee-roadmap/issues/161).

Port the shared MSC-123 security, dependency, session/cache, PWA and UI fixes to
the selected template implementation. The final signed package is installed and
verified on VMware. Docker images are published. The owner uploaded and published the exact signed DEB. All nine release assets
and canonical public update discovery are verified.

- Retain Firebase sessions across temporary configuration/auth network failures;
  an explicitly absent or invalid runtime still fails closed.
- Restore the real cached HTTP profile by its owning Firebase UID. Authorization
  failures are not hidden by offline data. Consent uses the hydrated server
  profile and waits for the current session's successful profile load.
- Activate waiting service workers when the network already supplied the current
  document, including workers installed after the initial bounded check.
- Keep verified Firebase/Playwright/DOMPurify updates, encrypted location storage,
  sanitization, shared controls and EN/HU translations aligned.
- Constrain profile image-editor widths and move the avatar badge outward/upward.
  Use the published `@fssrepository/myscoutee-components@1.4.1` owner for form,
  image-editor and avatar edit/completion controls.
- Ensure the retained media-deletion worker index in fresh/import/upgrade paths;
  add its existing canonical job definition to the E2E seed.

## Scope of the MSC-123 port

The complete main backend/frontend MSC-123 diffs were reviewed. Common Firebase
and PWA owners/tests are reused directly. Slim HTTP-profile, consent, editor and
Mongo owners are adapted in place. Template-selected translations remain
synchronized. Matchmaking, group invitations, Community service offerings,
events/Feedback, articles, payments, Registry and their admin signal/retry
bootstrap are omitted product domains in this template; their implementations
and data are not introduced. Main-app-only QA and CodeQL workflow metadata retain
their original repository ownership. No KVM or separate monolith campaign ran.

## Verification

- Focused template regressions: **137/137 PASS** across nine files.
- Shared Components FormFlow/rate regressions: **9/9 PASS**; production library
  build, registry publish and byte-identical registry download passed.
- Production frontend/backend compilation and all five image builds passed.
- Seed classification/parity: 15 baselines; EN/HU audit: 3392 messages.
- Fresh/import/upgrade index parity: 68 definitions. Migration018 is exercised
  twice; it creates the existing index without changing records.
- Package credential, source/image binding and actual DEB audits passed: 98 files
  plus five control files, exact bytes/modes/ownership/inventory.
- Both artifact signatures and the standalone update catalog signature verified.
- VMware: exact candidate installed with the existing signed installer; receipt
  SHA-256 matches. Seven services healthy, internal Mongo/content/Hungarian i18n
  checks passed; **13 external checks PASS** with TLS verification enabled.
- Headless UI smoke: two member logins, consent, session reload, four SmartList
  cards/images/day/week/month/reload, chat delivery/uniqueness/reload/unsend,
  logout and protected-route redirect passed. No runtime/console/resource errors.
  Test message and sessions were cleaned up. The browser test used the configured
  `https://localhost` origin mapped to VMware inside the test process; the private
  certificate exception was scoped to the isolated browser. Initial fixture
  origin/certificate attempts were corrected; application code was unchanged.

A complete pre-upgrade backup was verified. VMware is left running the template;
the main MyScoutee service is stopped. Existing performance measurements and
prior campaigns keep their own scope; this functional smoke is not a new load,
real-provider/device or rollback campaign.

## Sources and artifacts

Backend build source: `a9b7e69a337e92c0fef55620c45ee1e097c71b0b`. Frontend: `a28c76a0d73e2efedd570881b5b32291b64c3277`, UI build `1.4.1-a28c76a0d`. Components: `ce3a0c3e821f63b27a0c3d6408720c0e390b795e`. The backend tag additionally includes final release evidence.

| Service | Image | Immutable digest |
| --- | --- | --- |
| nginx-base | `ghcr.io/fssrepository/myscoutee-template-nginx:1.4.1-base` | `sha256:fb6a0855e824b2f33a35a7b97f4b70b49da2e34972abf3dd9e14dc9346b5bd3f` |
| nginx | `ghcr.io/fssrepository/myscoutee-template-nginx:1.4.1-prod` | `sha256:6c30fce1c87c9a5de979e86155c3bc43f23c15c265bce48e8faabeaabee5c036` |
| profilemanagement | `ghcr.io/fssrepository/myscoutee-template-api:1.4.1-prod` | `sha256:b78febf6dbdfa285c271f1a47e17480cbfaa1669082b8fc95a94f57b1f2caabb` |
| minio | `ghcr.io/fssrepository/myscoutee-template-minio:1.4.1-prod` | `sha256:43c9a6169408b8ffe1219ecfec6a7d65b9cf52d87e71df8c613bbafd6da8537a` |
| mcp | `ghcr.io/fssrepository/myscoutee-template-mcp:1.4.1-prod` | `sha256:7cef2dc38a988d9a1f767716f6bbb24525a0b87913822ad8d3d4b27592114268` |

GHCR retains its existing package access policy; authenticated digest pulls passed.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `myscoutee-template_1.4.1_amd64.deb` | 266066288 | `9459fcb8d5325500a6299107ddbdef3293eadd8dfe9faae2d4cc9facbf1250d3` |
| `myscoutee-template-client-tools_1.4.1.tar.gz` | 33440 | `34046b9313186f19c8433466b92a67e03d7c67e090d0dfd0fb507d139162552d` |

Signing key SPKI: `sha256:74255e852bf3720b81b990d0568f5b567124d6fb12335063f0309ccb522c5c03`. The signed update catalog targets the existing 1.4.0 baseline. The owner’s DEB upload/publication and canonical public update discovery are verified. Netcup remains open on the shared task.

Publication complete: [MyScoutee Template 1.4.1](https://github.com/fssrepository/myscoutee-template/releases/tag/v1.4.1) contains all nine assets. GitHub-computed sizes/digests match the exact signed candidate installed on VMware. Publicly downloaded metadata matches the verified local files; artifact and catalog signatures verify against the pinned template key. The canonical latest-release response selects v1.4.1 and the catalog’s actual DEB URL resolves publicly (HTTP 206 range check). Installed updater UI/installation was not replayed.
