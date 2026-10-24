# Template service shell

Shared runtime assets and host lifecycle tooling live in the sibling
`myscoutee-microservices` repository. This project's runtime/assembly inputs,
private state and generated outputs remain project-specific. Run shared lab commands from this application repository root (or set
`MYSCOUTEE_BACKEND_SOURCE` explicitly); `MYSCOUTEE_MICROSERVICES_SOURCE`
overrides its checkout path.
Project chart inputs are in `config/microservices/chart/`; the common renderer
assembles them with shared chart assets into ignored per-project build output.


This is the selected native service build and transport framework for template
**1.4.0**. The retained KVM flows were qualified on 2026-10-12; the exact source,
seed, image and Helm revisions are recorded in the evidence linked below.

| Assembly | Canonical domains |
| --- | --- |
| identity | accounts, access, administration, platform, integrations, showcase |
| communication | chat, notifications, email, localization |
| media | media |
| help | help |

Business implementations remain in `server/projects/profile`; this directory owns
composition and typed transport. `assemblies.json` selects implementations and
build settings; `transport/bindings.json` records cross-service contracts.
Community, Registry, payment and removed admin feature owners are not packaged.
The retained chat wire status describes existing conversations; it does not
install a support-chat creation provider.

With every application stack/build watcher stopped, verify serially:

```sh
../myscoutee-microservices/gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1024m verifyAssemblies
```

The source checkpoint passes four assembly ownership audits, 51 transport tests,
and 23 focused canonical tests. Selected wire evidence and qualification limits
are recorded in
[`MSC122_TEMPLATE_ASSEMBLY_SELECTION_20261012.json`](https://github.com/fssrepository/myscoutee-template/blob/9ae366ed68f93fcaad03e96b2010d07d0c1b1589/guides/qualification/evidence/MSC122_TEMPLATE_ASSEMBLY_SELECTION_20261012.json).
The current retained KVM qualification includes 17 backend contracts, profile
images, Discover, chat and working monitoring targets/panels; see
[`MSC122_TEMPLATE_KVM_RETAINED_UI_20261012.json`](https://github.com/fssrepository/myscoutee-template/blob/9ae366ed68f93fcaad03e96b2010d07d0c1b1589/guides/qualification/evidence/MSC122_TEMPLATE_KVM_RETAINED_UI_20261012.json).
This selected evidence does not close every performance, installed-release or
final acceptance requirement in the
[implementation plan](../../../tests/qa/QA_MATRIX.csv).

For an explicitly requested refresh of disposable KVM test data, use the native
seed owner after `python3 ../myscoutee-microservices/tools/workloads.py backends-stop`:

```sh
python3 ../myscoutee-microservices/tools/seed-mongo.py --reset-lab --from-fingerprint <recorded-hash>
python3 ../myscoutee-microservices/tools/seed-mongo.py --verify
python3 ../myscoutee-microservices/tools/workloads.py up
```

This verifies namespace, database names, previous seed and Mongo image, creates
and verifies a private Mongo backup, then replaces both owned lab databases from
the canonical production/DEMO seeds. It does not run automatically or target an
installed deployment. The same command resumes an interrupted reset; a completed
reset to the same seed is a no-op. Test expectations remain tied to the current
seed, not to stale lab data.
