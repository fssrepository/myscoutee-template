# Template production migrations

The shared ordered runner records each migration and its pinned input checksum.
Keep recorded scripts and inputs immutable. Installation, repeat installation and
update run this path before starting the API; never repair an installed database
by hand to bypass a failed deployment.

- `012-integration-indexes`: retained API/MCP indexes.
- `015-runtime-index-parity`: the complete selected 67-index template contract.
- `016-release-catalog-parity`: the selected help/guide production inputs. The
  native catalog selection is empty: no affinity, email campaign, article or
  landing-slide domain is imported into the template.

Migration016 reuses the native default/custom/inactive policy. New guide contexts
receive their packaged active revision. Existing operator content, deliberately
inactive choices, conflicting revision identities and newer active revisions are
preserved. Only unchanged older defaults advance automatically. Guide-field
metadata is package-owned; unrelated custom field IDs survive. Users, consents,
configuration and uploads are not migration targets.

`packaging/tests/release-catalog-parity-test.mjs --print-script` emits the real
Mongo regression for a unique disposable test database on the running runtime.
Its ordinary mode also checks pinned inputs against the selected current seed.
The actual installed artifact still requires deployment qualification.
