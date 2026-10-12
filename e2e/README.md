# Template functional qualification

The selected native HTTP, session, fixture and Chrome helpers exercise the real
application. The UI suite runs the existing fixture-owning flow scripts serially;
each closes its browser and removes its own test records. It writes a JSON/CSV
summary after each flow. `--case`, `--group`, `--qa-case`, `--list` and
`--fail-fast` use the established collection arguments.

## Disposable E2E stack

Stop dev and KVM first. This stack uses built runtime images, its own named
volumes and `e2e_db`, without watchers or source bind mounts. Its committed
credentials and local certificate are only for this disposable test deployment.
REAL/DEMO both address the isolated E2E database, following the native E2E mode.

```sh
docker compose --env-file server/docker-dev.env -f server/docker-compose-dev.yml stop
for service in nginx profilemanagement mongodb minio mcp; do
  docker compose --parallel 1 -f server/docker-compose.e2e.yml build "$service" || exit
done
docker compose -f server/docker-compose.e2e.yml up -d --no-build --wait
docker compose -f server/docker-compose.e2e.yml \
  cp nginx:/etc/nginx/keys/nginx.crt /tmp/myscoutee-template-e2e-nginx-ca.pem

export NODE_EXTRA_CA_CERTS=/tmp/myscoutee-template-e2e-nginx-ca.pem
export E2E_SERVER_BASE_URL=https://127.0.0.1
export E2E_SERVER_MODE=e2e
export E2E_DATABASE=e2e_db
export E2E_MONGO_CONTAINER=myscoutee-template-e2e-mongodb
export E2E_MINIO_CONTAINER=myscoutee-template-e2e-minio
node e2e/functional/backend/run.mjs
node e2e/functional/backend/admin/run.mjs
node e2e/functional/ui/run.mjs --fail-fast
```

The certificate is copied again after recreating nginx. HTTP redirects to HTTPS;
the tests trust this certificate explicitly. Headless Chrome uses the native
loopback-certificate allowance. No global TLS verification bypass is required.

To stop, use `docker compose -f server/docker-compose.e2e.yml stop`. For a fresh
baseline, `docker compose -f server/docker-compose.e2e.yml down --volumes` removes
only this disposable Compose project's containers and named volumes; the next
start runs the canonical Mongo/MinIO importers and index definitions. This does
not reset dev or production data.

## Local and dev HTTP UI

For an already-running dev stack, select its database/container explicitly:

```sh
E2E_SERVER_BASE_URL=http://localhost E2E_DATABASE=demo_db \
E2E_MONGO_CONTAINER=myscoutee-template-dev-mongodb \
  node e2e/functional/ui/run.mjs --case chat
```

For browser-local tests, stop the existing frontend compiler before starting
`npm run start:local` in `frontend`, then run:

```sh
node e2e/functional/ui/run.mjs --local --fail-fast
```

`E2E_LOCAL_BASE_URL` overrides the default `http://127.0.0.1:4200`.
Browser OAuth is HTTP-only; all other selected flows have local and HTTP variants.
`E2E_UI_ARTIFACTS_DIR` overrides `.e2e-artifacts/functional/ui/{local,http}`.
Each flow directory contains its log and available screenshots. The source
`suite.json` maps flows to the versioned QA matrix without redefining acceptance.

External provider login, public callback delivery, KVM, installed packages and
matched load measurements have separate gates in the numbered implementation
plan. Passing these functional cases does not close those gates.

## Account-warning email (local provider only)

Reuse `guides/qa/tools/email-provider-stub.py`; it records disposable fixture
mail and never forwards it. Bind it to the host's Docker bridge address (for
example `172.17.0.1`) on port `18991`, with its `--output` set to
`.e2e-artifacts/functional/email/received.jsonl`. Start E2E with
`EMAIL_SENDGRID_ENDPOINT=http://172.17.0.1:18991/v3/mail/send` and, for this focused
scheduler test, `EMAIL_ACCOUNT_WARNING_CHECK_DELAY_MS=1000`. Keep the normal
60000ms tick outside this test. Use the usual isolated E2E variables above.

With the receiver running, execute serially:

```sh
E2E_EMAIL_PROVIDER_STUB=true node e2e/functional/ui/operator/configuration.mjs
E2E_EMAIL_PROVIDER_STUB=true node e2e/functional/ui/operator/email-warning.mjs
```

The second test restores its owned actors, warning schedule, email configuration
and suppression rows. Captured email bodies and opaque unsubscribe links stay in
ignored private files. Stop the receiver and E2E stack afterward. This proves
the configured protocol path with the local receiver, not external email delivery.

## Visible installed qualification

After explicit user delegation of the full flow, use the versioned
`guides/qa/tools/open-checkpoint-browser-sessions.mjs` launcher and set
`E2E_MANAGED_BROWSER_MANIFEST` to its manifest in
`/tmp/myscoutee-qa-browser-sessions/`. The existing UI scripts then attach to
those visible windows. Two-user chat uses two configured isolated sessions;
call sessions require the configured fake media device. Teardown closes test
tabs but leaves the launcher-owned windows and profiles intact.
For local VMware qualification use the existing SSH fixture transport only on
`demo_db`; preserve the native and template installations' separate data.
For the freshly installed REAL operator, run `e2e/functional/ui/operator/installed.mjs`
with `E2E_SERVER_BASE_URL` and `E2E_INSTALL_LOG` pointing to the private (0600)
installer log. It uses the generated credential through the normal UI, checks
configuration/TLS controls, version, mobile layout and confirmed logout, and
never prints the credential.

Shut down the VMware VM before starting the E2E stack. The same memory boundary
applies to DEV and KVM: only one application deployment runs at a time.

## Focused consent and outbox qualification

`tools/run-backend-junit.sh embedded --tests '*ConsentRevisionRolloverPersistenceTest'`
exercises the existing session-routed Mongo consent owners. The matching frontend
entry cases are in `entry-page-consent.spec.ts`.

With only the owned template KVM running, use
`MYSCOUTEE_TEST_MONGO_TARGET=kvm python3 packaging/tests/push-outbox-runtime-test.py`.
It uses the existing typed provider transport and a local receiver, verifies
collapse, operation IDs and failure/restart/retry in REAL and DEMO, then restores
the worker environment and rules. It refuses unowned pending outbox work and
never sends to Firebase. Its private recovery snapshot remains under
`.e2e-artifacts/fixtures` until cleanup completes.

`E2E_EVIDENCE_FILE=/tmp/media-location.json python3 packaging/tests/media-location-runtime-test.py`
qualifies owned private media, its chat visibility and session revocation, live
EN/HU bundles and the existing disabled region-security flag on KVM. It restores
the identity environment and removes its actors, records and object. The HTTP
case `e2e/functional/backend/media/private-authorization/run.mjs` also runs on the
selected production Docker containers; set `E2E_EXPECT_REGION_SECURITY=false`
only when that existing flag is intentionally disabled for the test.
