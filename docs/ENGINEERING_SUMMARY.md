# Engineering Summary

## 1. What was built

I built a runnable URL shortener with short-link creation, redirect handling, link details, click analytics, and optional expiration.

The implementation was completed in several phases, with tests and quality checks added as the functionality evolved. The repository also includes architecture notes, scenario walkthroughs, API documentation, and the engineering decisions made during development.

## 2. Architecture

Layered Spring Boot monolith (Java 21, Maven, JPA, H2 in PostgreSQL mode,
Flyway V1–V3). Controllers → services → repositories; DTOs at the API edge;
RFC 7807 errors centralized in one advice. Full detail and diagram:
ARCHITECTURE.md.

## 3. Requirement interpretation

The assignment's scenario ("core APIs, analytics, and reliability
features") was normalized into: a uniqueness-constrained write path, a
latency-sensitive read path, analytics decoupled from the user path, and
consistent error semantics. Undefined terms were surfaced rather than
guessed: "analytics" became the documented ambiguous scenario;
"reliability" was interpreted as validation, failure isolation, collision
safety, and a health endpoint — not HA, which is out of prototype scope.

## 4. Task decomposition

Phased, each phase gated before the next: P0 scaffold + quality gates →
P1 greenfield core → P2 analytics (ambiguous) → P3 expiration (brownfield)
→ P4 hardening → P5 documentation. Within phases: schema → domain →
service → API → tests. Dependencies honored (schema first; the brownfield
scenario deliberately last of the three so a real codebase existed to
change).

## 5. Greenfield scenario

Core shorten + redirect, from requirement to result: scenarios/greenfield.md.
Headline decisions: random base62 via SecureRandom with constraint-backed
collision retry (hash and sequential-ID designs rejected), parse-based URL
validation with scheme allowlist, 302 semantics, non-transactional create
to keep the retry loop correct.

## 6. Brownfield scenario

"Links must expire" as a change request against the existing system:
scenarios/brownfield.md. Nullable `expires_at` (V3) for zero-impact
compatibility; 410 Gone distinct from 404; no click recorded for expired
visits; old signatures kept as delegates so the pre-existing suite ran
unmodified as the regression net. Rejected: query-level filtering, cleanup
job, NOT NULL sentinel.

## 7. Ambiguous requirement scenario

"Provide analytics" — undefined by design: scenarios/ambiguous.md documents
the seven questions a PO would get, the prototype assumption (per-click
events, timestamp + referrer only, no IP/UA), and the isolation: an
in-process `LinkClickedEvent` and one async listener are the only places a
different product answer would touch.

## 8. AI usage during development

I used AI selectively during development for things like reviewing design options, suggesting test cases, checking edge cases, and helping identify potential gaps.
I did not treat generated suggestions as final output. I reviewed the suggestions against the existing design, modified or rejected them where necessary, and ran the implementation and tests locally before accepting changes.

Examples of those decisions are documented in `docs/AI_USAGE.md`.

## 9. Where engineer judgment changed or rejected AI output

Recorded per entry in AI_USAGE.md; highlights: MD5-truncation code
generation rejected (collision semantics); `ThreadLocalRandom` replaced
with `SecureRandom` (enumerable links); `existsByCode` pre-check and
`@Transactional` around the retry rejected (race; rollback-only breaks
retry); 301 rejected (kills analytics); synchronous click insert storing
IP/user-agent rejected (hot-path coupling; privacy); query-level expiry
filtering rejected (wrong status code); naive `Exception` catch-all
corrected to preserve framework status mappings; default Checkstyle
verdicts overridden with documented conventions rather than code renames.

## 10. Testing and validation performed

Unit (generator, validator, service logic, recorder failure isolation,
entity boundary rules), web-slice (status codes, headers, problem details,
error contract including leak prevention), and end-to-end integration
(create→redirect→stats through the real schema, async analytics via
Awaitility, expiration flows, duplicate-URL behavior). `./mvnw verify`
(compile, tests, Checkstyle, JaCoCo) executed on the engineer's machine
each phase — verified runs grew from 31 (P1) to 45 (P2) to 62 (P4) to the
current suite — plus manual HTTP smoke tests of every endpoint after every
phase. Concurrency is proven by a real multi-threaded creation test, not
only by mocked collisions. CI (`.github/workflows/ci.yml`) runs the same
gate per push/PR. Not run (recommended for production pipeline): OWASP
dependency-check, SpotBugs/Sonar, load tests, coverage thresholds.

## 11. Security considerations

Scheme allowlist and parse-based validation, input caps, JPA parameter
binding, generic 500s with details only in logs, no secrets in repo,
actuator minimized, log hygiene (no long URLs in logs). Gateway-delegated:
authn/z, rate limiting, TLS, WAF. Residual: no destination-URL reputation
screening. Detail: ARCHITECTURE.md security posture.

## 12. Reliability considerations

Analytics doubly isolated from redirects (guarded publish + async
swallow-and-log listener); bounded collision retry with typed failure;
DB-constraint uniqueness; Flyway + `ddl-auto: validate` fail fast on schema
drift; health endpoint. Accepted: in-memory DB, event loss on crash
(analytics-grade only), no DB-down degradation path.

## 13. Performance / scalability considerations

Redirect = one indexed unique-key lookup; no unsupported throughput claims
(no load test run). Documented evolution: Postgres → stateless horizontal
scaling (already safe: uniqueness lives in the DB) → hot-redirect caching →
queue + rollups for clicks → code pre-allocation only if write volume ever
demands it.

## 14. Major design decisions

Random codes over hash/sequential; constraint-plus-retry over pre-check;
non-transactional create; 302 over 301; event-decoupled analytics; nullable
expires_at with in-code expiry check and 410; H2-PG-mode + Flyway for
zero-dependency runnability; layered monolith. Each with its alternative
recorded in ARCHITECTURE.md.

## 15. Trade-offs

Zero-dependency runnability over production-identical infra (H2 vs
Postgres); event-level analytics storage (flexible, grows unboundedly) over
counters (cheap, freezes questions); losing a click beats blocking a
redirect; 410 clarity over hiding link existence; new-link-per-submission
over dedup; boring stack over impressive stack.

## 16. Assumptions

Anonymous API (auth at gateway); no custom aliases; no dedup; expiry
boundary `now >= expiresAt`; URLs stored as submitted (no
canonicalization); single instance for the prototype; submission evaluated
by running `./mvnw spring-boot:run` with no external services.

## 17. Known limitations

Data lost on restart (in-memory DB); unbounded click_events growth, no
retention; COUNT-per-request stats; async click loss on crash; no
in-app auth/rate limiting; no destination-URL screening; JaCoCo report
generated but no coverage threshold enforced; `docs/openapi.yaml` is
hand-maintained and can drift from the code (springdoc generation is the
production answer).

## 18. What I would improve before production

Postgres with pooling and backups; gateway with authn/z + rate limits;
CI pipeline running the full gate set plus dependency and static-analysis
scans on every commit; queue-backed click ingestion with rollup tables and
retention; hot-path caching with expiry-aware TTLs; destination-URL
reputation checks; structured JSON logging with request correlation ids,
metrics (redirect latency, collision rate, event-queue lag) and alerting;
load testing to replace assumptions with numbers; coverage and mutation
thresholds in the build.
