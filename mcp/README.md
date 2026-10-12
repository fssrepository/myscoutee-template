# Template MCP

Selected native Go MCP transport, OAuth isolation, active help reads and role
Events. The application backend owns identity and permissions; this process
contains no database, member model, affiliate or community business logic.

Project identity, version and assistant guidance live in
`internal/server/product.go`. `internal/api/contract.json` contains only the five
retained native input contracts: `listChats`, `chatDetails`, `chatMessages`,
`listNotifications` and `adminOverview`. Extend this catalog when adding a backend
API operation; discovery still intersects it with each connection's current
backend permissions. No product API schemas or plugin assets are included.

Use `make test`, `make build` or `make image` with Go 1.26/Docker. Dev, E2E and
production Compose own the HTTP deployment. Anonymous `/mcp` reads the current
English/Hungarian help; `/mcp/private` requires per-user backend OAuth. Local
stdio uses `MYSCOUTEE_API_BASE_URL`, `MYSCOUTEE_API_TOKEN` and
`MYSCOUTEE_CLIENT_ID` outside chat. The established environment names and protocol
scope are retained for client compatibility. Never configure a process-wide
private key on HTTP. Runtime images contain only the binary and CA certificates.

A local protocol test does not prove acceptance by a remote ChatGPT account.
