# MSC115-TEMPLATE-REGRESSION

Stable checklist for **only the unverified template effects of collected
MSC-115/116/117/118/119 fixes**. Prepared 2026-10-15; execution remains NOT RUN.
This document expands the existing checkpoint, not the completed MSC-122 baseline.
The [regression record](../../evidence/general/MSC115-TEMPLATE-REGRESSION.json)
owns per-item revisions, applicability, results and evidence. The template's
[qualification ledger](../../matrices/TEMPLATE_QA_MATRIX.csv)
also contains newer MSC-116 propagation notes collected below.

## Evidence credit — no repeated baseline

1. Before executing any block, compare its changed owner/revision and boundary
   with the existing template evidence. Credit an already-proven case by linking
   its evidence; execute only the uncovered delta. A historical NOT RUN note or
   a later unrelated commit is not a reason to replay a passed case. Source-only
   proof does not establish template runtime success.
2. Keep MSC-122 closed. Credit the October 12 two-party RTP, repeated-call and
   KVM continuity results in
   [chat parity](../../../qualification/evidence/MSC122_TEMPLATE_CHAT_PARITY_20261012.json)
   and [KVM evidence](../../../qualification/evidence/MSC122_TEMPLATE_KVM_20261012.json).
   CALL-01 stays removed. Do not repeat baseline login, chat/call, full guide,
   integration, packaging, install/update, recovery, publication or stress suites.
   New source-campaign call/role tests enter here only after an actual relevant fix.
3. Record a concrete N/A where the template omits a consumer. Retaining a shared
   component does not establish a product UI using every component option.
   Component-level proof may cover an unused shared option; do not add a product
   screen or enable a new option just to obtain runtime coverage.

## Preparation — remaining checks only

4. Reconcile the record with the template ledger's new fix notes before execution.
   For each selected block record source and template backend/frontend/components
   revisions, dirty-file hashes where relevant, changed owner, credited evidence
   and exact missing checks. Confirm propagation; older working-tree notes are
   not proof of the currently deployed revision.
5. Use existing template fixtures and runners from its
   [QA instructions](../../README.md).
   Verify the baseline before a fixture-backed browser launch. Managed sessions
   use the template's versioned `guides/qa/tools/open-checkpoint-browser-sessions.mjs`
   and configured checkpoint for the selected deployment. The regression ID is
   not currently a launcher configuration. Preparation ends at launcher handoff;
   checklist execution requires its own applicable delegation.
6. Run only one application stack/build owner at a time. Use local and HTTP for
   changed adapter/UI boundaries; UI-only checks do not acquire an extra KVM run.
   Verify a changed backend batch on monolith and KVM serially, using focused
   checks and crediting equivalent existing evidence. Record initial fixture and
   process ownership for cleanup. This checklist preparation starts no runtime.

## TPL-CALENDAR — showcase palette and layout

Record item: `calendar`. Consumer: template
`frontend/src/app/member/components/showcase/showcase.component.ts` and `.html`.

7. In the retained showcase, compare the existing blue/violet/orange/green sample
   palettes in week/month views; day/list card appearance remains as configured.
   Check long badge labels, week two-line ellipsis and month cell containment on
   desktop and narrow mobile. Reuse the same sample records across views.
8. Respect current applicability: showcase density is commented out and its
   template binds no item/cell selection handler. Do not require threshold counts,
   navigation into details or new clickable cells. Check that inactive cells do
   not advertise a click. If another existing consumer actually enables density
   or selection, record that owner and test only its affected configured behavior;
   otherwise record these subchecks as N/A, with the configuration evidence.

## TPL-ERROR — one combined empty/error/chat journey

Record items: `empty-error-card`, `smart-list-empty-read-error`,
`chat-lazy-load-errors`. Reuse the same chat session and evidence for all three.

9. Open retained chat in applicable local/HTTP modes: popup opens immediately,
   lazy loading stays inside it, and configured local delay stays local. Compare
   an ordinary empty state with the centered read-error state. No false empty
   header or additional reload on an error-only update. Task-column placement is
   not a template UI requirement where the business task board is absent.
10. For HTTP timeout, leave the actual message request unanswered until the
    configured timeout. The localized centered error appears without an extra
    delay and the red header failure indicator persists after request completion.
    A synthetic 500/503 is not timeout proof. Reuse that same failed state for
    EN/HU text and verify no source-list error row or separate notice dialog.
11. While failed, offline shows grey; returning online restores red. Record
    actual connectivity and computed colour. A late successful response must
    neither clear the timed-out error nor reopen a closed popup. A new successful
    open/retry restores normal state. Closing/aborting alone is not a failure.
12. Only if the changed shared behavior is not already covered by matching
    component or retained-consumer evidence, use one other existing SmartList
    consumer to check persistent failure and retry. Do not repeat a full list suite.

## TPL-PALETTES — affected shared rendering only

Record items: `frequency-badge-colours`, `notification-contextual-row-palettes`,
`infocard-contextual-palettes`. Template ledger: `T-SMARTLIST-001`.

13. Establish whether a retained UI actually uses schedule frequency badges.
    The product Cases schedule popup is absent. Credit applicable existing shared
    palette/component proof; if a retained consumer exists, compare its schedule
    choice and row badge (Monthly amber, Quarterly violet, Yearly gold). Otherwise
    record UI N/A and the shared revision/proof separately, not a fabricated flow.
14. On retained notification/SingleRow consumers affected by the shared change,
    sample existing icon, contextual avatar and unread/read appearance. Reuse
    prior proof for unchanged behavior; do not replay notification delivery,
    read/mute persistence or product Feedback navigation.
15. For the newer MSC-116 InfoCard change, verify green/amber/slate/violet badges
    against the shared menu tokens, including static/interactive variants and
    slate menus where retained consumers expose them. Use affected card/row/gallery
    views on desktop/mobile and local/HTTP only where that rendering is present.
    If an option has no template UI consumer, record its component-level coverage
    and UI N/A. Do not require showcase cards to expose unconfigured badges.

## TPL-POPUP — changed mobile action sizing

Record item: `popup-content-toolbar-alignment`.

16. Identify an existing popup whose end group uses inline content-sized actions.
    At narrow mobile and desktop sizes verify right alignment, visible labels and
    no overflow/wrapping. On existing configurations, check fill-sized actions
    still use available mobile width and a start-side date/filter retains its
    separate alignment. Do not treat every `actionSizing=content` menu as a popup
    toolbar; record unavailable configuration variants as UI N/A with shared proof.

## TPL-GUIDE — final owner/placement delta

Record item: `guide-context-and-continuity`. Earlier six template tests predate
final repeated-menu-owner and short-viewport changes; credit their covered scope.

17. On one affected retained local/HTTP guide, open and make three transitions,
    including an opened menu. Launcher, popup and shade remain visible and owned
    by the foreground surface. Use the existing guide runner/fixture; do not run
    every previously accepted tour or repeat the source workspace matrix.
18. Verify the changed only-Close/no-guide branch and meaningful retained image
    targets. Where existing data exposes repeated conditional menu owners, each
    target belongs to the correct visible owner; guide navigation invokes no
    business action. Record N/A if that variant is absent.
19. Check the final compact placement on a short mobile viewport with visible
    controls and readable EN/HU text. Credit existing wider-layout evidence unless
    this change invalidates it.

## TPL-MEDIA — retained deletion queue

Record item: `media-deletion-queue`. Use the existing media worker, job configuration
and backend history; require a jobs UI only if the template actually exposes one.

20. Verify propagated root job configuration and EN/HU labels. Queue disposable
    exact managed keys, including image variants, through existing supported
    entry points. Duplicate enqueue is idempotent and does not scan buckets.
21. Verify configured bounded batch processing, successful-entry removal and
    failed-key-only retry; already-missing objects count as success. Check retry
    classification, attempt count and terminal error after at most three total
    attempts, including an expired final lease.
22. Verify retained media transport ownership, REAL/DEMO isolation and contract
    validation; no hidden SDK/transport retry or duplicate processing across claims.
    Job history reflects actual deleted/failed work. Credit focused existing
    template tests; run only missing runtime boundaries in the serial backend batch.

## TPL-OAUTH — new role consent routing

Record item: `role-oauth-consent-routing`. Template ledger: `T-INTEGRATION-009`.

23. Through the existing authorization fixture, check the new local/HTTP route
    for authenticated member, admin and operator: OAuth query remains intact at
    consent and after required login return. Keep role home redirects and HTTP
    privacy-consent gating intact. These checks concern the changed routing,
    not a full external MCP onboarding replay.
24. Check the affected refusal gates: signed-out, blocked/inactive/deleted/onboarding
    profiles stay gated and bootstrap operator cannot authorize. Credit equivalent
    current route/guard tests and execute only the missing browser boundaries.

## TPL-EVENTS — new realm, limit and callback-validation fixes

Record items: `events-delivery-realm`, `subscription-admission-limit`,
`callback-ipv4-validation`. Template ledger: `T-INTEGRATION-008`.

25. Use the existing retained `operator.attention` fixture and a controlled callback
    receiver. Verify signed delivery when REAL/DEMO alias the same database and
    isolation when they use distinct databases; request realm checks and revoked
    grants remain denied. This does not require a live provider or external host.
26. Credit/recheck the template's actual retry owner: its dispatcher owns all RETRY
    rows. The source-only scheduler-test change is N/A; do not copy the product's
    throttled-RETRY ownership rule into the template. No full callback retry suite
    is required without a changed or unproven relevant boundary.
27. Check the propagated post-verification serialized subscription admission:
    concurrent requests at 99 subscriptions admit only the remaining slot up to
    100, and refreshing an existing subscription at capacity remains supported.
    Include the propagated callback-limiter regression only where template proof
    is missing; reuse current focused tests rather than a new load campaign.
28. Check strict canonical numeric IPv4 validation: reject short, integer,
    leading-zero and hexadecimal address forms. Credit matching current template
    tests for this changed validator; do not rerun the whole SSRF/security matrix.

## TPL-RELEASE-NOTICE — new template-aware replay tool

Record item: `operator-release-discovery`. Template ledger: `T-OPERATOR-003`.

29. Verify template `guides/qa/tools/operator-update-release-server.py`
    `prepare-notification` uses `packaging/scripts/release-project.json` packageName
    and existing signing/client-tools owners. Use a protected external QA key and
    disposable signed inert QA artifact through the existing release fixture.
30. Check only template release discovery and operator notification for that
    artifact, then clean up the owned fixture. **Never install the inert artifact.**
    Accepted signed install/update/rollback and publication checks stay credited;
    this change does not reopen them.

## Results and cleanup

31. For each selected delta record PASS/FAIL/NOT RUN or reasoned N/A, credited vs
    newly executed evidence, revisions, environment/mode, fixture, actual result
    and untested boundary in the existing regression record and corresponding
    template ledger row. Keep the three existing product-only N/A items closed:
    command/version business audit, payment receipt presentation and subevent
    selector. The absent operator leaderboard is also N/A for its source fix.
32. Restore only owned fixture data, sessions, callbacks, queue objects and temporary
    configuration; close owned launcher sessions and restore prior process owners.
    Close this checkpoint only when every applicable collected delta is resolved.
    Source campaign closure, historical template PASS and checklist creation alone
    do not close a new template delta. Physical devices, external networks and live
    providers remain in their separate qualification scope.
