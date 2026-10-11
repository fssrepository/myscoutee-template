# MSC-122 omitted admin feature dependency audit

Updated: 2026-10-12. Owner: implementation plan step 5.
Status: native batch qualified at `37f5f708e`; template backend/adapters removed and unit checks passed; artifact HTTP/local and template KVM qualification pending.

## Verified starting point

- Native backend: `5d71945a2`; frontend: `90289af0`.
- Template: `a05a110`; frontend: `87b9715`.
- Reports, admin Support chat and Feedback screens, member report/feedback controls,
  their UI stores and five popup guides are removed. 309 frontend tests and four
  local plus four artifact HTTP flows pass. Normal chat and admin API still work.
- Native warning cleanup passed 63 KVM and 43 monolith runtime checks, serially.
  The template UI change did not alter Java services, repositories or contracts.
- This audit precedes additional template service composition and packaging.
  The user's request authorizes source modularization followed by removal from
  the template; it supersedes the earlier deferred decision about retention.

## Current dependency map

Paths below are relative to `server/projects/profile/src/main/java/com/raxim/myscoutee/domain`.

| Entry / consumer | Existing owner and connection | Boundary that must be separated or preserved |
| --- | --- | --- |
| `accounts/controller/AuthController` | `/auth/me/feedback` and `/auth/me/report-user` call `HomeUserProfileService` | Submission routes and their feature service must be selectable separately from login, profile, consent and account lifecycle. Preserve full MyScoutee route/DTO/error behavior. |
| `accounts/service/HomeUserProfileService` | Constructor requires `content/api/UserFeedback`, `UserReports`, `chat/api/ChatReview`; submission methods also emit role notifications and operational telemetry | Remove the submission-only dependencies from the mandatory profile owner. `RoleNotifications.memberJoined` and profile telemetry are also used by retained registration; do not remove those owners wholesale. |
| Native report eligibility | Existing report method reads event and accepted membership contracts and verifies any referenced chat message | Native MyScoutee event/organizer/membership checks must remain byte-for-byte equivalent in behavior. The template's already adapted chat-only report endpoint will be omitted, not broadened into a new native report policy. |
| `administration/controller/AdminController` | Report dashboard/warn/block/unblock/resolve routes use `content/api/AdministrativeAccess` alongside help, notifications, statistics, affinity and ideas | Separate feature routes without moving or disabling unrelated admin capabilities in MyScoutee. The template controller contains only the selected moderation routes and can be omitted after dependency checks. |
| `content/service/AdminModerationService` | Owns report/feedback queues and mutations; uses account moderation, chat review/support, admin identity, notification delivery and telemetry | Omit this feature implementation from the template after inbound references are removed. `AdministrativeActors` already owns administrator identity independently; use it where only identity is needed. |
| `administration/service/AdminStatsService`, `AdminMonitoringService` | Depend on `AdministrativeAccess` only for `baseGroupId` and `baseProfileEvents` | These are administrator scope/telemetry responsibilities. Existing batching reads profiles once per event batch; preserve that and avoid a new per-event lookup. These statistics implementations are already absent from the template. |
| `content/api/ContentStatistics` | Mixes report/feedback queue counts with photo-feed counts | Do not remove native photo-feed behavior while making moderation counts optional. `accounts/service/UserActivityCountersService` still needs the feed side. |
| `integrations/repository/AdminClientRepository`, `service/AdminClientService` | Mandatory moderation count calls populate `Attention.unresolvedReports` and `unresolvedFeedback`; support counts come from the account snapshot | Keep admin authentication, keys, permissions and API/MCP connection flow. Define absent-feature response fields explicitly; do not fake successful zero-valued moderation data to satisfy a removed screen. Current native full-app responses must remain unchanged. |
| `chat/service/ChatService` | Implements generic `ChatQueries` and moderation `SupportMessages`; support creation also handles MyScoutee group moderation | Separate support creation from ordinary send/read/history where necessary. Keep generic access to existing conversations; do not filter or reject chats merely because a creator domain is omitted. Reuse existing persistence/read-receipt/idempotency paths. |
| `accounts/service/AccountChatCounterService` | Implements normal `ChatCounters` plus `SupportCaseCounters` | Keep normal atomic and bulk unread updates. The support-case projection is feature-specific and currently groups admin recalculation by base workspace. |
| `accounts/service/ProfileStatusWork` | Delivers all status observers, then durable support notices, then clears the guarded work marker | Retained account lifecycle must not require an omitted support sender. Preserve save/retry ordering and pending-work safety; do not discard an undelivered notice or clear its marker as a fallback. |
| `accounts/service/ProfileModerationService` | Account-owned block/unblock updates and durable support-notice intent | Feature commands can be separated; shared blocked/deleted/inactive status and authentication checks must remain valid. Do not change the Users document merely because a screen is absent. |
| `notifications/service/RoleNotificationService` | `moderationSubmitted` shares delivery with release and registration events | Remove only feature event production/bindings. Retain the outbox, notification inbox, delivery preferences, registration and update events. |
| Account purge / warning | Existing retained lifecycle and warning workers; generic chat/account cleanup | Preserve the four selected jobs, 30-day grace, warning delivery and purge retry semantics. Determine exclusive moderation cleanup separately; absence of a UI does not authorize deleting account lifecycle work. |
| Local frontend adapters | Users submission methods, admin workspace/moderation services and persistence are still present behind the removed UI | Remove exclusive local adapters/seed slices with the qualified backend removal, preserving shared users/chat/identity/API state and local/HTTP parity. |
| Independent services | `microservices/transport/bindings.json` exports the existing typed APIs; identity owns accounts/admin/integrations, community owns content, communication owns chat/notifications | Preserve authenticated actor, workspace and REAL/DEMO context. A service/controller move must include routing, transport and artifact ownership checks; a Java compile alone is insufficient. |

## Implementation order and acceptance

1. Finish the consumer/owner closure and record the concrete source changes before
   editing. Reuse existing administrator identity and feature interface patterns.
   Account for internal transport consumers and API/MCP response readers, not only
   controller imports.
2. In MyScoutee, separate mandatory account/admin/integration responsibilities from
   report, feedback and support feature implementations. Keep URLs, authorization,
   query shapes, batching, idempotency and durable work unchanged when enabled.
   Do not introduce a new deployment/JVM merely to organize a feature.
3. Validate one coherent source batch: focused JUnit, boundary/assembly checks,
   actual KVM and monolith checks serially, CPU/latency and matched performance
   where the changed path warrants it. Commit the verified source batch.
4. Apply the qualified selection to the template. Remove only exclusive feature
   implementations, routes, adapter wiring, data selection and now-obsolete tests.
   Keep required model shapes and shared infrastructure; omit feature-only JSON
   sections explicitly where supported by the retained contract.
5. Prove clean template startup, identity/account lifecycle, normal two-member
   chat, notifications/help/consent, admin API/MCP and operator behavior. Verify
   local and HTTP, then monolith and the minimal KVM composition at their actual
   boundaries. Update provenance and the QA matrix; do not claim unrun installed
   or release checks.

## Evidence boundary

This is an inventory, not proof that backend removal is complete. No empty
provider, compatibility fallback, report-policy rewrite, chat filtering or
extra service process has been introduced by this audit.

## First source extraction — 2026-10-12

Move the existing two submission methods and their report-only helper methods
from `accounts/service/HomeUserProfileService` to the selectable
`accounts/service/ModerationSubmissionService`. Reuse `SelectedProfileService`
directly: the old profile method already delegated actor resolution to that
owner. Move the same two HTTP mappings into
`accounts/controller/ModerationSubmissionController`. Keep both controllers in
the identity assembly, so no ingress/transport route changes or added remote hop
are needed. Remove only the three submission-only constructor dependencies from
the mandatory profile service (`UserFeedback`, `UserReports`, `ChatReview`).
Event/member reads needed by other profile functions remain. Existing report
eligibility, evidence validation, duplicate handling, notification and telemetry
sequence move unchanged. Preserve the existing regression cases and add focused
submission/controller checks. This extraction alone does not close step 5 or
authorize skipping the other dependency rows above.

## Next source boundaries in the same batch

- Split the six report/feedback moderation routes out of `AdminController`,
  retaining their exact mappings in the administration assembly.
- Use `AdministrativeActors` for administrator identity and the existing
  `AdministratorClassification` stateless policy for base-profile classification.
  Content moderation retains its original single `ProfileSummaries` lookup.
  Classification must not become an administrator-authorized RPC: on the base
  group route a non-admin classification must still reach existing group-role
  authorization, exactly as before.
- Move telemetry batch scoping out of `AdminModerationService` into an
  administration-local `AdminTelemetryScopeService`. Statistics and monitoring
  already live in that assembly. Keep the original distinct actor IDs and one
  bulk profile lookup per batch; this removes the identity-to-community detour.
  Preserve independent-workspace exclusion and job-tag behavior. Remove the
  unused generic identity/telemetry operations from the moderation transport.
  No new administrator transport operation is needed.
- The closest existing owners are `AdministrativeActorService` (identity/scope)
  and `AdminTelemetryService` (event recording). The latter does not perform read
  projection, so batch scoping gets one local service reused by both statistics
  consumers; no new deployment, document, queue, or per-record query.

## Support mutation boundary in the same batch

Use the existing `ResourceConversationService` pattern: a chat-local feature
service reuses package-local `ChatService` mutation/mapping helpers. Extract
support creation, stable-message retries and support-case state commands into
`SupportConversationService`; move the support-case HTTP mapping, with its exact
`RequiresPolicy`, into `SupportChatsController`. The same `SupportMessages` API
and communication assembly remain. Ordinary chat queries, access checks, message
persistence, stream delivery and existing-conversation visibility stay unchanged.
Separate `SupportCaseCounters` implementation from atomic/bulk `ChatCounters`,
preserving one support read per workspace. Do not create another conversation
store, publisher, write executor, counter implementation or a new remote hop.
Make status work's support sender optional only when there is no support work.
If persisted support intent exists without its owner, retain both intent and
work marker; never acknowledge or drop it. Native installed delivery order and
stable message IDs stay unchanged.

## API/MCP attention selection

Reuse the integrations package's `Optional<IntegrationProductCapabilities>`
composition pattern for a local `AdminModerationAttention` provider. Its installed
implementation reads the same two `ContentStatistics` counts and the already
loaded support-case snapshot. No new remote protocol or count query is required.
`AdminClientService` retains authentication, unread version, normal unread count
and failed jobs. With the provider installed, all existing numeric moderation
fields remain unchanged. Without it, omit only `unresolvedReports`,
`unresolvedFeedback` and `supportCases` from JSON; do not represent a missing
feature as zero. Qualify both serialized shapes and denied-admin behavior.
`ContentStatistics` remains the native content owner's API; the feed consumer
continues to use it. The template needs neither photo-feed nor moderation counts,
so it omits that owner and API rather than inventing a replacement provider.
The two MyScoutee count queries remain reachable only through the selected
moderation attention implementation.

## Native source checkpoint

`dc97521d4` preserves the full MyScoutee feature set while separating these
owners. The coherent 297-test standard group passed, followed by 60 focused
checks after the final classification change (includes the existing boundary
suite), and one embedded Mongo chat/counter persistence check. 25 moved method
bodies match their originals after owner qualification. These figures are not
an aggregate count of distinct tests. Native runtime CPU/latency, KVM and monolith
checks remain pending. Evidence:
`myscoutee-backend/guides/qualification/evidence/MSC122_ADMIN_FEATURE_SEPARATION_20261012.json`.
No template Java owner has been removed yet.

## Template selection checkpoint — 2026-10-12

Native source qualification is complete: eight assemblies, focused JUnit, KVM and
monolith checks passed, serially. The bounded KVM admin read profile measured
+3.93% throughput, -3.81% p50, -7.98% p95 and -0.84% VM CPU under load; this is
not a claim about every flow. Evidence is committed in MyScoutee at `37f5f708e`.

The template now omits the report/feedback content owner, submission and admin
moderation routes, support mutations and their counters, ChatReview and account
moderation commands. Local/HTTP adapters and the exclusive local seed/store are
also omitted. Shared chat reads and stored conversation shapes remain unchanged.
The native `ProfileStatusWork` and observer contract are reused: ordinary status
work needs no support provider, while old undelivered support intent is retained.
The already-curated purge service omits only the two absent content cleanup calls;
all retained purge stages, the 30-day grace, retry and missing-product-owner guard
remain. The native source already has `AccountPurgeActions` for that selection.

Admin API keeps authentication, keys and the overview. Optional moderation fields
are absent from JSON. The template MCP catalog offers only the retained operator
release event; it does not advertise report/feedback events with no producer.
Its subscription/callback/revocation tests now exercise that retained event.
Identity's existing demo support-session context is a separate login protocol,
not the removed support chat creator; it is preserved with the identity model.

398 standard Java tests, the embedded Mongo account-purge check, 293 frontend
tests and the production frontend build passed. Two existing stylesheet-budget
warnings remain. Runtime absence checks, retained flows and minimal KVM
composition are still required; step 5 is not yet closed.
