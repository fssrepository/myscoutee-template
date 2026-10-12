# MSC-122 — Minimal Template Checklist

Updated: 2026-10-12 · Version: **1.4.0**

**Current task: 29.4 (signed draft assets are ready; awaiting the matching user-uploaded DEBs).** Tasks 1–28 are DONE.
**28/33 main tasks DONE · 99/116 subtasks DONE.**

### 1. Source baseline — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 1.1 | Chat source qualification | **DONE** |
| 1.2 | account source qualification | **DONE** |

### 2. Dependency inventory — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 2.1 | File provenance | **DONE** |
| 2.2 | retained/omitted owners, imports, data, infrastructure and tooling inventory | **DONE** |

### 3. Repository and version — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 3.1 | Submodules/remotes/clean bootstrap | **DONE** |
| 3.2 | current application version | **DONE** |
| 3.3 | source metadata and existing build inputs aligned to 1.4.0 | **DONE** |

### 4. Minimal backend — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 4.1 | Native application composition | **DONE** |
| 4.2 | retained live routes/security/session checks | **DONE** |
| 4.3 | public-document fix | **DONE** |

### 5. Remove unwanted admin features — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 5.1 | Dependency audit | **DONE** |
| 5.2 | qualified native separation | **DONE** |
| 5.3 | template removal | **DONE** |
| 5.4 | KVM/monolith absence and retained-chat checks | **DONE** |

### 6. DEV/E2E/production runtimes — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 6.1 | DEV watchers/sync/gateway | **DONE** |
| 6.2 | isolated artifact E2E | **DONE** |
| 6.3 | production start/stop and separate persistent state | **DONE** |

### 7. Seeds and reset — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 7.1 | Minimal local/DEMO/E2E/production seeds and corrected avatars/cards | **DONE** |
| 7.2 | guide/i18n parity and data-preserving migration 017 | **DONE** |
| 7.3 | reset/reimport configuration/media preservation and reference/counter/domain audit | **DONE** |

### 8. Identity and profile — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 8.1 | Sessions, profile/onboarding and authorization | **DONE** |
| 8.2 | popup-history/login-return fix in both frontends | **DONE** |

### 9. Deletion and background jobs — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 9.1 | Durable purge selection | **DONE** |
| 9.2 | retained cleanup/notification/callback workers | **DONE** |
| 9.3 | warning email and local receiver | **DONE** |
| 9.4 | delete/reactivate UI | **DONE** |
| 9.5 | Provider deletion callback/status | **DONE** |
| 9.6 | deployed purge/retry/restart and REAL/DEMO isolation | **DONE** |
| 9.7 | template KVM warning email through the local test receiver | **DONE** |

### 10. Communication infrastructure — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 10.1 | Two-member messaging/read/reconnect | **DONE** |
| 10.2 | native audio/video and source fixes | **DONE** |
| 10.3 | completed call observation closed | **DONE** |

### 11. Notifications, help and consent — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 11.1 | Inbox/guide/initial consent persistence | **DONE** |
| 11.2 | current guide targets and preserved catalog upgrades | **DONE** |
| 11.3 | Required-consent revision rollover in REAL/DEMO | **DONE** |
| 11.4 | deployed push retry/collapse/idempotency with the local test provider | **DONE** |

### 12. Media, locales and location — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 12.1 | Avatar upload/replace/remove/reload | **DONE** |
| 12.2 | media policy and anonymous denial | **DONE** |
| 12.3 | EN/HU, coordinates and device settings | **DONE** |
| 12.4 | Remaining live private-media authorization, locale switching and disabled location-policy cases | **DONE** |

### 13. API/MCP — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 13.1 | Native transport/scopes/role catalog/revocation | **DONE** |
| 13.2 | local/HTTP credentials, consent and PKCE/login return | **DONE** |
| 13.3 | callback delivery and durable retry through the local test receiver | **DONE** |

### 14. Frontend foundation — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 14.1 | Five build environments/shared owners | **DONE** |
| 14.2 | local/HTTP persistence | **DONE** |
| 14.3 | backend-off failover, role reload and native loading | **DONE** |
| 14.4 | domain folders/popup startup | **DONE** |

### 15. Landing and version — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 15.1 | Native branding/login/guide, Gothic title and responsive Explore | **DONE** |
| 15.2 | current production stamp and historical Pages verification | **DONE** |
| 15.3 | Corrected 1.4.0 Pages landing/assets/deep links and final DEV/production/Pages version agreement | **DONE** |

### 16. SmartList example — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 16.1 | Native components, neutral data and bounded paging | **DONE** |
| 16.2 | popup/views/grouping/reload in local and HTTP deployments | **DONE** |

### 17. Chat UI — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 17.1 | Native channel/list/header/message actions/calls/loading and persistence | **DONE** |
| 17.2 | Rename chat-only ActivitiesFeedFilters to ChatFeedFilters in source/template; preserve non-chat consumers and serialized queries; focused type/filter verification only | **DONE** |

### 18. Menu, profile and settings — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 18.1 | Native avatar/gallery/profile persistence | **DONE** |
| 18.2 | settings and confirmations | **DONE** |
| 18.3 | animated side menu, Basics, Name before About and visibility | **DONE** |

### 19. Guide/consent/notification UI — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 19.1 | Entry/tours/preferences | **DONE** |
| 19.2 | inbox/polling/navigation | **DONE** |
| 19.3 | corrected guide targets and image-editor context | **DONE** |

### 20. Admin/operator UI — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 20.1 | Empty admin menu and retained API | **DONE** |
| 20.2 | configuration order/save/security | **DONE** |
| 20.3 | native layout/colors/menu/profile | **DONE** |
| 20.4 | TLS/update UI | **DONE** |

### 21. Unit/component coverage — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 21.1 | Selected backend/frontend suites | **DONE** |
| 21.2 | domain/removal suites | **DONE** |
| 21.3 | focused correction checks with recorded revisions/counts | **DONE** |

### 22. Backend E2E — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 22.1 | Seventeen HTTP contracts, owned fixtures and retained mutation checks | **DONE** |
| 22.2 | Reconcile all retained endpoints with tests, including missing consent/private-media/provider-deletion/callback cases | **DONE** |

### 23. Frontend E2E — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 23.1 | Fifteen HTTP and fourteen local flows | **DONE** |
| 23.2 | incremental corrected-production checks | **DONE** |
| 23.3 | offline/loading/responsive parity | **DONE** |

### 24. Stress/performance — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 24.1 | Native read-stress tools | **DONE** |
| 24.2 | matched repeatability runs with latency/CPU/error evidence | **DONE** |
| 24.3 | Minimal browser/WebSocket stress using the existing engine and deterministic fixtures | **DONE** |

### 25. KVM — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 25.1 | Four service owners/transports | **DONE** |
| 25.2 | isolated VM/lifecycle | **DONE** |
| 25.3 | retained-flow checks | **DONE** |
| 25.4 | Prometheus/Grafana and serial monolith | **DONE** |
| 25.5 | focused migration 017 verification | **DONE** |

### 26. Production Docker — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 26.1 | Current 1.4.0 images/seven services | **DONE** |
| 26.2 | fifteen UI flows | **DONE** |
| 26.3 | builds/catalog/migration checks | **DONE** |
| 26.4 | serial stop/restore with retained state | **DONE** |

### 27. DEB and VMware — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 27.1 | Host helper selection/separate UID/data | **DONE** |
| 27.2 | package/input/environment checks | **DONE** |
| 27.3 | Final corrected packages | **DONE** |
| 27.4 | current native install first | **DONE** |
| 27.5 | separate fresh template install, operator first login, host controls/TLS/PWA/browser checks | **DONE** |
| 27.6 | backup/restore/data-preserving removal | **DONE** |

### 28. Signed release and updater — **DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 28.1 | Shared signer/updater and tamper checks | **DONE** |
| 28.2 | private signing authority outside Git | **DONE** |
| 28.3 | Final matching 1.4.0 signed package/client/update set | **DONE** |
| 28.4 | installed update, rejection, restart/retry and rollback/recovery | **DONE** |

### 29. Publication — **NOT DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 29.1 | Historical Pages/image publication | **DONE** |
| 29.2 | registry/MinIO source and removal of unrequested Actions | **DONE** |
| 29.3 | Publish/verify current 1.4.0 images and a freshly rebuilt template GitHub Pages site from frontend master/docs | **DONE** |
| 29.4 | native/template draft assets and user-uploaded DEB verification | **NOT DONE** |
| 29.5 | Qualified source snapshot and artifact revision mapping; final history/tag publication is task 32.1 | **NOT DONE** |

### 30. Documentation/PDF — **NOT DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 30.1 | One English manual source and stable PDF | **DONE** |
| 30.2 | Final delivered commands for setup/modes/seeds/tests/extension/deployment/recovery/release; concise template README with the published GitHub Pages link and the usual documentation table; minimal counterpart of the native architecture documentation, including a diagram and important operating/design decisions rather than code-level details; move the detailed shared-components documentation into a PDF, shorten the myscoutee-components README and link that same PDF from both the components and template READMEs | **NOT DONE** |
| 30.3 | regenerate and inspect the same PDF | **NOT DONE** |

### 31. Final acceptance — **NOT DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 31.1 | Complete artifact/dependency audit | **NOT DONE** |
| 31.2 | clean checkout/build/install and exact-revision evidence | **NOT DONE** |
| 31.3 | final release/resource/blocker reconciliation | **NOT DONE** |
| 31.4 | prepare member login/SmartList/chat smoke checks on serial VMware/DEV/KVM | **NOT DONE** |


### 32. Git history, project and usage closeout — **NOT DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 32.1 | Consolidate MSC-122 backup commits, preserve unrelated work, keep a separate MSC-122 commit in myscoutee-template-frontend, verify exact remote gitlinks and publish with force-with-lease and qualified version tags; version and publish changed myscoutee-components as a new npm release to GitHub Packages using its existing release workflow | **NOT DONE** |
| 32.1a | Separate template frontend repository: one `[MSC-122]` commit (`9e3f7a3`), concise README, CC BY 4.0, remote components and master/docs Pages with automatic publication | **DONE** |
| 32.2 | Close the verified template task and update its GitHub Project status | **NOT DONE** |
| 32.3 | Update roadmap progress and measured token-usage records | **NOT DONE** |
| 32.4 | Check the current ChatGPT account usage shown by the available account interface | **NOT DONE** |
| 32.5 | Refresh the README heatmap from the completed project evidence | **NOT DONE** |


### 33. LinkedIn article and image — **NOT DONE**

| Subtask | Task | Status |
| --- | --- | --- |
| 33.1 | Write a short promotional article positioning MyScoutee-template as a substantial foundation for demanding projects, using verified capabilities | **NOT DONE** |
| 33.2 | Write a very short LinkedIn post introducing and sharing the article | **NOT DONE** |
| 33.3 | Create one accompanying image | **NOT DONE** |
| 33.4 | Save and commit the article, sharing post and image under the private myscoutee-backend/guides tree | **NOT DONE** |

Rules: reuse native MyScoutee owners and behavior; no custom replacement UI or branding fallback. Preserve separate local/HTTP, REAL/DEMO/E2E data. One stack/compiler at a time; visible managed QA browsers. Do not repeat unchanged completed tests or video checks. Codex may start VMware when its numbered task is reached (user authorized on 2026-10-12); native installation first, then separate template data. No Netcup. GitHub Actions is authorized for the frontend master/docs Pages publication only. The user uploads DEBs.
