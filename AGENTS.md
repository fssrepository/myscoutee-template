# Agent instructions

Before project work, load and apply
[the myscoutee-template instructions](../myscoutee-skills/instructions/projects/myscoutee-template.md)
and the shared documents they require. Resolve project paths from this repository
root, not from the shared checkout. Current user instructions take precedence.

Use the existing sibling `../myscoutee-skills` checkout. If it is absent, clone it
from this repository root:

```sh
git clone -- https://github.com/fssrepository/myscoutee-skills.git ../myscoutee-skills
```

If that path already exists but the required instructions are missing, report the
problem; do not overwrite it. Do not automatically pull, reset or replace an
existing checkout. A clone failure is a missing-instructions blocker; report it.

## Keep backend fixes reflected here

When a fix is made in `../myscoutee-backend`, inspect and update the equivalent
template implementation in the same task, including affected entry components,
tests, build scripts and packaging. Preserve this project's own behavior and
deployment configuration. Generate image locks from this project's own built
images; do not copy backend image identities. Verify both implementations before
reporting the shared fix complete.
