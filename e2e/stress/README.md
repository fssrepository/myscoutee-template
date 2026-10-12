# Minimal performance qualification

Use the existing backend load runner, resource sampler, ramp and comparison tools.
Only the product context and workload are selected here. No community/payment
models or fixtures are needed. The seven read cases reuse the same actors and
assertions as `e2e/functional/backend`; owned sessions are revoked at completion.

Start exactly one existing template runtime first. Never run dev and KVM together.
Run a 15-second warmup followed by the recorded workload on the same images/data:

```bash
export E2E_NATIVE_DEMO_SESSIONS=true E2E_SKIP_E2E_RESET=true
export E2E_STRESS_BASE_URL=https://localhost
# For a private local CA only: export NODE_EXTRA_CA_CERTS=/path/to/ca.pem
node e2e/stress/backend/run.mjs --scenario template-read --duration-ms 15000
node e2e/stress/backend/run.mjs --scenario template-read --record
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
node e2e/stress/backend/compare.mjs /path/to/baseline /path/to/candidate
node e2e/stress/backend/ramp.mjs --scenario template-read --users 2,4,6 --duration-ms 60000
```

An initial baseline or repeatability run is not evidence of improvement over
MyScoutee or an older template. Never compare different seed populations or
deployment limits as if they were matched revisions. Browser/WebSocket stress
selection remains a separate pending part of step 24; existing functional call
and UI passes are not stress measurements.
