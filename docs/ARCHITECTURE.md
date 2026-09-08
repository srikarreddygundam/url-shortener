# Architecture

## Shape

A layered Spring Boot monolith — deliberately boring. The prototype's job is
to be correct, reviewable, and runnable anywhere; every scale-up path below
starts from this shape without rework.

```
            ┌────────────────────────────────────────────────┐
 Client ──► │ API layer                                      │
            │  LinkController      POST /api/links           │
            │                      GET  /api/links/{code}    │
            │                      GET  /api/links/{code}/stats
            │  RedirectController  GET  /{code}   (hot path) │
            │  ApiExceptionHandler → RFC 7807 problem+json   │
            └───────────────┬────────────────────────────────┘
                            ▼
            ┌────────────────────────────────────────────────┐
            │ Service layer                                  │
            │  LinkService         create / resolve / expiry │
            │  UrlValidator        parse + scheme allowlist  │
            │  CodeGenerator       random base62 (seam)      │
            │  ClickAnalyticsService  stats aggregation      │
            │  ClickEventRecorder  @Async click persistence  │
            └───────┬───────────────────────────▲────────────┘
                    │        LinkClickedEvent   │ (async, in-process;
                    ▼        published by ──────┘  queue slots in here)
            ┌────────────────────────────────────────────────┐
            │ Data                                           │
            │  LinkRepository / ClickEventRepository (JPA)   │
            │  H2 (PostgreSQL mode) · Flyway V1–V3           │
            │  links(code UNIQUE, expires_at NULL)           │
            │  click_events(link_id idx, occurred_at, referrer)
            └────────────────────────────────────────────────┘
```

## The two paths

**Create** (`POST /api/links`, low volume): validate URL (parse-based,
http/https only, length cap) → validate expiry (future only) → random
7-char base62 code from `SecureRandom` → insert; a unique-constraint
violation (collision) retries with a fresh code, max 5 attempts. No
check-then-insert — the constraint is the only guarantee that survives
concurrency. `create()` is deliberately not `@Transactional`: each
`saveAndFlush` commits alone so a violation doesn't poison an outer
transaction and break the retry.

**Resolve** (`GET /{code}`, read-heavy, latency-sensitive): one indexed
lookup by unique code → expiry check in memory (410 Gone if expired) → 302.
The only work added beyond the lookup is publishing an in-process event;
click persistence happens on another thread and its failure is logged, never
surfaced. 302 rather than 301 because permanent-redirect caching would let
browsers bypass the service — silently destroying analytics and the ability
to expire links.

## Data model

`links` — id, code (unique, base62), long_url (as submitted), created_at,
expires_at (NULL = never; nullable was the brownfield compatibility
decision). `click_events` — id, link_id (indexed FK), occurred_at, referrer
(nullable, truncated to 2048; no IP/user-agent by design).

## Key decisions (and the alternatives that lost)

| Decision | Alternative considered | Why it lost |
|---|---|---|
| Random base62 + unique constraint + bounded retry | Truncated URL hash | Collisions become correctness bugs; same-URL-same-code conflicts with per-link expiry |
| | Sequential ID encoded as base62 | Enumerable links (security), couples code to DB identity |
| `SecureRandom` | `ThreadLocalRandom` | Predictable codes → link enumeration |
| Constraint-backed collision retry | `existsByCode` pre-check | Check-then-insert races under concurrency |
| 302 redirect | 301 | Permanent caching kills analytics and expiry |
| Expiry checked in code after load | `findByCodeAndExpiresAtAfter` query | Collapses "expired" into "not found"; 410 needs the row |
| In-process async event for clicks | Synchronous insert in redirect | Write on the hot path; analytics outage would break redirects |
| | Kafka/queue now | Right at scale; overengineering for a prototype — the event is the seam where it plugs in |
| H2 (PG mode) + Flyway | Postgres via Docker | Evaluator must be able to run it with zero dependencies; Flyway keeps schema change management real either way |
| Layered monolith | Microservices | Nothing here justifies distribution; the layers are the future service boundaries |

## Security posture

Implemented in the prototype: parse-based URL validation with an http/https
allowlist (blocks `javascript:`/`file:`/`data:` laundering), input length
caps (URL 2048, referrer truncated), SQL injection prevented by JPA
parameter binding throughout, RFC 7807 errors with a generic 500 message
(internals only in server logs), no secrets in the repo (H2 in-memory,
nothing to protect — production credentials would come from the
platform's secret store), actuator limited to health/info, H2 console
disabled, log hygiene (short codes and link ids are logged; long URLs are
not, since query strings can carry tokens/PII).

Deliberately delegated to gateway/platform in production, not reimplemented
in-app: authentication/authorization, rate limiting and abuse throttling,
TLS termination, WAF, DDoS protection. Known residual risks: no malware/
phishing screening of destination URLs (production would integrate a
Safe-Browsing-type check) and open-redirect-by-design (that is what a
shortener is — mitigated by scheme allowlisting and expiry/disable
control).

## Reliability

Analytics failure is isolated from the user path twice over (guarded
publish + async listener that logs and swallows). Collision retry is
bounded with a typed failure. Constraint violations, not read-check races,
guarantee uniqueness. Flyway validates the schema on every startup;
Hibernate runs in `validate` so drift fails fast. Health endpoint for
orchestration. Accepted prototype risks: in-memory DB, in-process events
lost on crash (analytics-grade data, acceptable; billing-grade data would
need an outbox), no degradation path if the DB is down.

## Performance and scale evolution

Prototype reality: every redirect is one indexed unique-key lookup; stats
are a COUNT per request; no caching. No throughput claims are made — no
load test has been run.

The path to scale, in order, each step slotting into an existing seam:

1. **Postgres** (swap JDBC URL; SQL already PG-compatible; connection pool
   sizing).
2. **Stateless horizontal scaling** — the app holds no state; N instances
   behind a load balancer work today. Code generation stays correct across
   instances because uniqueness lives in the DB constraint, not in memory.
3. **Cache hot redirects** (Caffeine per instance, then Redis shared) —
   read-heavy traffic makes the redirect path cache-friendly; expiry must be
   respected in cache TTLs or by caching the expiry timestamp with the URL.
4. **Queue for click events** — replace the in-process event dispatch inside
   the existing `LinkClickedEvent` seam with Kafka/SQS; consumers write
   events and maintain rollup tables so stats stop COUNTing raw events.
5. **Code pre-allocation** if write volume ever makes collision retries
   measurable (allocate code ranges per instance) — not before.
