# Dependabot Gradle configuration

The inactive `dependabot.yml` example targets `/server`, where this repository's
Gradle settings include `projects:profile`, alongside the wrapper and version
catalog. Pointing Dependabot at `/` fails with `dependency_file_not_found` before
Gradle compilation begins.

The backend's old root-directory configuration was removed in MSC-127. The
failed October 23 run is historical. This corrected example stays under
`config/ci/` so publishing the MSC-129 configuration does not automatically
re-enable Dependabot. Enabling it is a separate explicit operation: copy it to
`.github/dependabot.yml` on the default branch. GitHub may immediately check for
updates when that active configuration is published.

This file is not a claim that a hosted Dependabot update passed. Local checks
verify the configured directory, included subproject and catalog in both the
backend and template. No application build is needed for this path correction.
