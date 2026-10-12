# Template service shell

This is the selected native service build and transport framework. It is not yet
an independently qualified KVM deployment. Version: **1.0.0**.

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
microservices/gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1024m verifyAssemblies
```

The source checkpoint passes four assembly ownership audits, 51 transport tests,
and 23 focused canonical tests. Selected wire evidence and qualification limits
are recorded in
[`MSC122_TEMPLATE_ASSEMBLY_SELECTION_20261012.json`](../guides/qualification/evidence/MSC122_TEMPLATE_ASSEMBLY_SELECTION_20261012.json).
KVM, monitoring, deployment flows and runtime performance remain pending under
step 25 of the [implementation plan](../guides/plans/MSC-122_TEMPLATE_IMPLEMENTATION_PLAN.md).
