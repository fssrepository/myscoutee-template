# Minimal performance qualification

Use the existing backend load runner, resource sampler, ramp and comparison tools.
Only the product context and workload are selected here. No community/payment
models or fixtures are needed. The seven read cases reuse the same actors and
assertions as `tests/e2e/functional/backend`; owned sessions are revoked at completion.

Start exactly one existing template runtime first. Never run dev and KVM together.
Run a 15-second warmup followed by the recorded workload on the same images/data:

```bash
export E2E_NATIVE_DEMO_SESSIONS=true E2E_SKIP_E2E_RESET=true
export E2E_STRESS_BASE_URL=https://localhost
# For a private local CA only: export NODE_EXTRA_CA_CERTS=/path/to/ca.pem
node tests/e2e/stress/backend/run.mjs --scenario template-read --duration-ms 15000
node tests/e2e/stress/backend/run.mjs --scenario template-read --record
```

For KVM set the base URL to `http://localhost:18080`. Host Docker samples do not
measure guest service CPU: also use the existing microservices Prometheus/VM
measurement tools. The runner never starts a runtime. Reset is disabled for this
read-only scenario; do not enable native full-database E2E reset against production.

Results include step wall times, errors, throughput, Docker CPU/memory and host
memory/load. A collection case can include supporting reads, so its throughput
is completed **case iterations per second**, not raw HTTP requests per second.
Keep image IDs, source revisions, data, warmup, duration, concurrency and limits
beside the result. Compare matched runs with:

```bash
node tests/e2e/stress/backend/compare.mjs /path/to/baseline /path/to/candidate
node tests/e2e/stress/backend/ramp.mjs --scenario template-read --users 2,4,6 --duration-ms 60000
```

An initial baseline or repeatability run is not evidence of improvement over
MyScoutee or an older template. Never compare different seed populations or
deployment limits as if they were matched revisions. The selected browser workload reuses `tests/e2e/stress/ui/run.mjs` with
`--scenario template-shell --record`. Prepare the existing MONOLITH checkpoint
with the versioned browser launcher, supply its `E2E_MANAGED_BROWSER_MANIFEST`,
and keep `E2E_SKIP_E2E_RESET=true`. It uses three serial mobile entry/SmartList flows.

For socket connection stress use `node tests/e2e/stress/backend/run-template-websocket.mjs`
with the selected template Mongo container/database and the same TLS CA as the
HTTP tests. The wrapper owns and removes its two actors and conversation, uses
six clients, warms for three seconds and records fifteen seconds. It does not
send messages or replay video. Results include connection latency and CPU/memory;
these runs do not claim native/template performance parity.
