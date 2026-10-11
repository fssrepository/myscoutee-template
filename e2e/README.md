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
`SENDGRID_ENDPOINT=http://172.17.0.1:18991/v3/mail/send` and, for this focused
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
