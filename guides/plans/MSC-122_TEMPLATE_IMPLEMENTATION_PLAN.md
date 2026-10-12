# MSC-122 — Minimal Template Checklist

Updated: 2026-10-12 · Version: **1.4.0**

**Current task: 7.3.** Tasks 1–6 are DONE. Complete unfinished work in order; do not jump to VMware.
**16/31 main tasks DONE · 79/108 subtasks DONE.** Counts describe completion, not time estimates.

Rules: reuse native MyScoutee owners and behavior; no custom replacement UI or branding fallback. Preserve separate local/HTTP, REAL/DEMO/E2E data. One stack/compiler at a time; visible managed QA browsers. Do not repeat unchanged completed tests or video checks. VMware starts only by the user after earlier executable work; native installation first, then separate template data. No Netcup or GitHub Actions. The user uploads DEBs.

A main task is **DONE** only when its unfinished column is empty. Every subtask appears once. Build, install and publication checks belong to their actual stages; final Git publication is 29.5. Test details/revisions remain in existing qualification evidence, not repeated here.

| Task | Status | DONE subtasks | NOT DONE subtasks |
| --- | --- | --- | --- |
| 1. Source baseline | **DONE** | 1.1 Chat source qualification; 1.2 account source qualification. | — |
| 2. Dependency inventory | **DONE** | 2.1 File provenance; 2.2 retained/omitted owners, imports, data, infrastructure and tooling inventory. | — |
| 3. Repository and version | **DONE** | 3.1 Submodules/remotes/clean bootstrap; 3.2 current application version; 3.3 source metadata and existing build inputs aligned to 1.4.0. | — |
| 4. Minimal backend | **DONE** | 4.1 Native application composition; 4.2 retained live routes/security/session checks; 4.3 public-document fix. | — |
| 5. Remove unwanted admin features | **DONE** | 5.1 Dependency audit; 5.2 qualified native separation; 5.3 template removal; 5.4 KVM/monolith absence and retained-chat checks. | — |
| 6. DEV/E2E/production runtimes | **DONE** | 6.1 DEV watchers/sync/gateway; 6.2 isolated artifact E2E; 6.3 production start/stop and separate persistent state. | — |
| 7. Seeds and reset | **NOT DONE** | 7.1 Minimal local/DEMO/E2E/production seeds and corrected avatars/cards; 7.2 guide/i18n parity and data-preserving migration 017. | 7.3 Verify reset/reimport preserves operator configuration and uploaded media; audit references, counters and omitted-domain IDs. |
| 8. Identity and profile | **NOT DONE** | 8.1 Sessions, profile/onboarding and authorization; 8.2 popup-history/login-return fix in both frontends. | 8.3 Configured provider login, REAL roles and operator bootstrap. |
| 9. Deletion and background jobs | **NOT DONE** | 9.1 Durable purge selection; 9.2 retained cleanup/notification/callback workers; 9.3 warning email and local receiver; 9.4 delete/reactivate UI. | 9.5 Provider deletion callback/status; 9.6 deployed purge/retry/restart and REAL/DEMO isolation; 9.7 template KVM/external warning email with configured public URL/TLS. |
| 10. Communication infrastructure | **DONE** | 10.1 Two-member messaging/read/reconnect; 10.2 native audio/video and source fixes; 10.3 completed call observation closed. | — |
| 11. Notifications, help and consent | **NOT DONE** | 11.1 Inbox/guide/initial consent persistence; 11.2 current guide targets and preserved catalog upgrades. | 11.3 Required-consent revision rollover in REAL/DEMO; 11.4 configured push and deployed retry/collapse/idempotency. |
| 12. Media, locales and location | **NOT DONE** | 12.1 Avatar upload/replace/remove/reload; 12.2 media policy and anonymous denial; 12.3 EN/HU, coordinates and device settings. | 12.4 Remaining live private-media authorization, locale switching and disabled location-policy cases. |
| 13. API/MCP | **NOT DONE** | 13.1 Native transport/scopes/role catalog/revocation; 13.2 local/HTTP credentials, consent and PKCE/login return. | 13.3 External ChatGPT/provider round trip; 13.4 public HTTPS callbacks and durable retry. |
| 14. Frontend foundation | **DONE** | 14.1 Five build environments/shared owners; 14.2 local/HTTP persistence; 14.3 backend-off failover, role reload and native loading; 14.4 domain folders/popup startup. | — |
| 15. Landing and version | **NOT DONE** | 15.1 Native branding/login/guide, Gothic title and responsive Explore; 15.2 current production stamp and historical Pages verification. | 15.3 Corrected 1.4.0 Pages landing/assets/deep links and final DEV/production/Pages version agreement. |
| 16. SmartList example | **DONE** | 16.1 Native components, neutral data and bounded paging; 16.2 popup/views/grouping/reload in local and HTTP deployments. | — |
| 17. Chat UI | **NOT DONE** | 17.1 Native channel/list/header/message actions/calls/loading and persistence. | 17.2 Rename chat-only ActivitiesFeedFilters to ChatFeedFilters in source/template; preserve non-chat consumers and serialized queries; focused type/filter verification only. |
| 18. Menu, profile and settings | **DONE** | 18.1 Native avatar/gallery/profile persistence; 18.2 settings and confirmations; 18.3 animated side menu, Basics, Name before About and visibility. | — |
| 19. Guide/consent/notification UI | **DONE** | 19.1 Entry/tours/preferences; 19.2 inbox/polling/navigation; 19.3 corrected guide targets and image-editor context. | — |
| 20. Admin/operator UI | **DONE** | 20.1 Empty admin menu and retained API; 20.2 configuration order/save/security; 20.3 native layout/colors/menu/profile; 20.4 TLS/update UI. | — |
| 21. Unit/component coverage | **DONE** | 21.1 Selected backend/frontend suites; 21.2 domain/removal suites; 21.3 focused correction checks with recorded revisions/counts. | — |
| 22. Backend E2E | **NOT DONE** | 22.1 Seventeen HTTP contracts, owned fixtures and retained mutation checks. | 22.2 Reconcile all retained endpoints with tests, including missing consent/private-media/provider-deletion/callback cases. |
| 23. Frontend E2E | **DONE** | 23.1 Fifteen HTTP and fourteen local flows; 23.2 incremental corrected-production checks; 23.3 offline/loading/responsive parity. | — |
| 24. Stress/performance | **NOT DONE** | 24.1 Native read-stress tools; 24.2 matched repeatability runs with latency/CPU/error evidence. | 24.3 Minimal browser/WebSocket stress using the existing engine and deterministic fixtures. |
| 25. KVM | **DONE** | 25.1 Four service owners/transports; 25.2 isolated VM/lifecycle; 25.3 retained-flow checks; 25.4 Prometheus/Grafana and serial monolith; 25.5 focused migration 017 verification. | — |
| 26. Production Docker | **DONE** | 26.1 Current 1.4.0 images/seven services; 26.2 fifteen UI flows; 26.3 builds/catalog/migration checks; 26.4 serial stop/restore with retained state. | — |
| 27. DEB and VMware | **NOT DONE** | 27.1 Host helper selection/separate UID/data; 27.2 package/input/environment checks. | 27.3 Final corrected packages; 27.4 current native install first; 27.5 separate fresh template install, host controls/TLS/PWA/browser checks; 27.6 backup/restore/data-preserving removal. |
| 28. Signed release and updater | **NOT DONE** | 28.1 Shared signer/updater and tamper checks; 28.2 private signing authority outside Git. | 28.3 Final matching 1.4.0 signed package/client/update set; 28.4 installed update, rejection, restart/retry and rollback/recovery. |
| 29. Publication | **NOT DONE** | 29.1 Historical Pages/image publication; 29.2 registry/MinIO source and removal of unrequested Actions. | 29.3 Publish/verify current 1.4.0 images/Pages; 29.4 native/template draft assets and user-uploaded DEB verification; 29.5 qualified [MSC-122] squash, exact gitlinks/bundle, force-with-lease and v1.4.0 tags. |
| 30. Documentation/PDF | **NOT DONE** | 30.1 One English manual source and stable PDF. | 30.2 Final delivered commands for setup/modes/seeds/tests/extension/deployment/recovery/release; 30.3 regenerate and inspect the same PDF. |
| 31. Final acceptance | **NOT DONE** | — | 31.1 Complete artifact/dependency audit; 31.2 clean checkout/build/install and exact-revision evidence; 31.3 final release/resource/blocker reconciliation; 31.4 prepare member login/SmartList/chat smoke checks on serial VMware/DEV/KVM. |

Native source QA (CM-LOCAL-001, MSC-115/116/117/119) remains separate; only proven shared-owner defects apply to the template. The waived native email-verification registration check is not a template provider-login result. Installed/external-provider work is never inferred from component or local-simulation results.
