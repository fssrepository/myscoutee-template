# MSC-126 template applicability and 1.4.5 delivery

1. Centralized HTTP/local adapter cache retains the template's own 12 pairs.
   Both modes compile; HTTP startup contains no local service/seed bytes and
   standalone startup contains no HTTP service bytes. There is no duplicate
   BaseRouteMode selector and no main-only activity/assets module import.
2. The template keeps EN/HU and its own product captions. Reviewed UI labels use
   semantic keys; data identities remain unchanged. Both catalogs contain 1,816
   messages, version `2026.10.21.2`, and all 3 Mongo copies pass parity. Backend
   full-version equality and preferred-browser-language cache startup match the
   main contract; HTTP mode has no local language-file fallback.
3. Migration 019 independently pins 52 applicable renames/104 rows. Static and real
   Mongo retry/customization/version-ordering tests pass. The template's 6 seeded
   profile references already have 18 matching WebP variants; main's article/room
   media migration and lifecycle fixtures do not apply and were not copied.
4. Startup consolidation, filled icon font discovery and worker startup caching
   use the existing build/stamp owners. Empty export-only modules no longer add
   an empty lazy chunk. HTTP: 31 application chunks/two startup files; standalone:
   27/two. Startup gzip estimates: 506,940/627,305 bytes. The common help-description
   metadata no longer imports the complete local revision seed; exact descriptions
   and deliberate builder overrides pass 9 focused cases.
5. Both main and template setup popups select the existing fullscreen mobile
   presentation. The shared component copy matches main at
   `e34d0f8abb008d5ce5a1a3621ae8a9aaf00c4f33`.
6. Validation: 92 focused frontend tests, then 30 directly affected caption/metadata
   tests; 7 backend i18n tests; 8 bundle grouping tests; both optimized builds and
   catalog/seed audits pass. No complete frontend suite rerun or new template runtime
   deployment is claimed. Host frontend-sync was initially stopped and remains
   stopped. No test/build owner remains. Source is consolidated into one MSC-126 commit per affected repository.
7. Final manifests, source state, logs and migration receipts are retained at
   `/home/raxim/.local/share/myscoutee-work/msc126-20261021-release/`.
   See the sibling main project's `guides/plans/MSC-126_BUNDLE_INVENTORY.md` for
   every chunk's size/loading roots and `MSC-126_LIGHTHOUSE_GAINS.md` for remaining
   Lighthouse priorities. Main 1.4.5 is verified on VMware and Netcup. The template API/nginx images, source tags and standalone artifact are published; see `guides/releases/v1.4.5.md`. This template batch is complete, not a claim of complete Lighthouse
   qualification or numerical performance parity.
