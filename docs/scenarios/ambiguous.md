# Ambiguous Requirement Scenario — "The service should provide analytics"

## Why this requirement is ambiguous

The assignment names analytics as core scope but never defines it. An
experienced engineer does not fill that vacuum with guesses baked deep into
the system; the questions below would go to the product owner, and the
prototype decision is isolated so any answer can be adopted later cheaply.

## Questions I would ask the product owner

1. **Which metrics matter?** Total clicks? Unique visitors? Clicks over time?
   Geography, device, referrer breakdown?
2. **Do we need per-click events or just counters?** Events can answer any
   future question but grow unboundedly; counters are cheap but freeze the
   set of answerable questions.
3. **May we store visitor identifiers?** IP addresses and full user-agents
   are personal data in most privacy regimes (GDPR et al.). Storing them is a
   legal/compliance decision, not an engineering one.
4. **How fresh must numbers be?** Real-time dashboards and nightly batch
   reports imply very different architectures.
5. **What's the retention policy?** Forever is not a policy.
6. **Who may see stats?** Public per-link stats leak traffic information
   about the link owner's audience.
7. **If analytics storage is down, may we still redirect?** (I assume yes —
   losing a data point beats failing a user.)

## Prototype assumption (and why it is reasonable)

Record one **click event** per redirect with `occurred_at` and `referrer`
only — **no IP, no user-agent** — and expose
`GET /api/links/{code}/stats` returning total clicks and last-click time.

- Event-level data is the safe default: every aggregate the PO might later
  choose can be derived from events; the reverse is not true.
- Omitting IP/UA keeps the prototype clear of personal-data obligations; a
  privacy-approved identifier can be added to the event later.
- Total + last-click is the minimal read model that proves the pipeline
  end-to-end.

## How the decision is isolated

The redirect path knows nothing about analytics storage. It publishes a
`LinkClickedEvent` (Spring application event); an `@Async` listener persists
it. Consequences:

- **Failure isolation:** a broken analytics write can neither slow down nor
  fail a redirect — the listener runs on a separate executor and swallows
  (logs) its own errors. Losing a click beats losing a user.
- **Cheap to change:** if the PO chooses Kafka, a warehouse, or different
  fields, the listener is the only component that changes. The event is the
  seam — it is exactly where a message broker would slot in at scale.
- **Metrics choice contained:** aggregation lives in `ClickAnalyticsService`;
  changing what stats mean touches one class and its tests.

## Decomposition

1. `click_events` table (Flyway V2), indexed by `link_id`
2. `LinkClickedEvent` + publish from the redirect path
3. `ClickEventRecorder` — async listener, error-isolated
4. `ClickAnalyticsService` + stats endpoint
5. Tests: recorder failure isolation, stats aggregation, async end-to-end

## Validation

- Unit: recorder persists events and swallows storage failures; publisher
  failures don't break `resolveForRedirect`; stats aggregate correctly and
  404 on unknown codes.
- Integration: create → redirect twice → stats endpoint reports 2 clicks
  (Awaitility waits out the async hop — the test exercises the real
  asynchronous path rather than forcing synchronous execution).

## Known limitations (accepted for the prototype)

- Unbounded event growth; no retention job. At production scale click
  inserts move behind a queue and aggregates become materialized/rolled-up.
- Stats reads hit the event table directly (COUNT per request) — fine at
  prototype volume, a rollup table or cache at scale.
- Async events are lost on process crash before the insert commits —
  acceptable for analytics, would not be acceptable for billing-grade data
  (that distinction is why the PO questions matter).
