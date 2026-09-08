# AI Usage Log

Traceability log for AI-assisted work on this project. One entry per significant
task. Every AI suggestion was reviewed before merge; nothing landed without
engineer sign-off. Entries record what was generated, what was edited, and what
was rejected, with rationale.

Secure usage note: no secrets, credentials, personal data, or proprietary code
were provided to the AI assistant at any point. Context given to the AI was
limited to this repository and the assignment text.

Entry format:

- **Task** — the engineering problem being solved
- **Context provided to AI** — what the engineer supplied
- **AI suggestion** — summary of what the AI proposed
- **Engineer review** — what was agreed with
- **Engineer modification** — what was changed manually
- **Rejected** — anything rejected, and why
- **Validation** — how the final result was verified
- **Decision** — what was approved and by whom

---

## Entry 1 — Project scaffold and quality gates (Phase 0)

- **Task:** Stand up the Spring Boot project skeleton with build-time quality
  gates before any feature code exists, so every later change passes through
  the same checks.
- **Context provided to AI:** Assignment requirements; chosen stack (Java 21,
  Spring Boot 3.x, Maven, H2 in PostgreSQL mode, Flyway); instruction to keep
  the prototype zero-dependency to run.
- **AI suggestion:** Standard Boot parent POM with web/data-jpa/validation/
  actuator/flyway/h2 starters; Checkstyle bound to `verify` with a pragmatic
  ruleset; JaCoCo coverage report; `ddl-auto: validate` so Flyway owns the
  schema; `open-in-view: false`.
- **Engineer review:** Accepted the plugin set and the Flyway-owns-schema
  arrangement — matches how my current team manages schema change.
- **Engineer modification:** Trimmed the suggested Checkstyle ruleset to rules
  that catch real defects (unused imports, missing braces, empty catch blocks,
  60-line method cap) rather than formatting taste; set method length and
  parameter count limits explicitly.
- **Rejected:** Adding springdoc/OpenAPI UI and Testcontainers in the scaffold.
  Neither is needed yet; dependencies get added when a phase requires them.
- **Validation:** `./mvnw verify` — compile, context-load test, Checkstyle, and
  JaCoCo. Executed on the engineer's machine; run evidence in the README
  quality-gates section.
- **Decision:** Scaffold approved as the baseline for all later phases.

---

## Entry 2 — Short-code generation strategy (Phase 1, greenfield)

- **Task:** Generate unique short codes safely under concurrent requests.
- **Context provided to AI:** Schema (unique constraint on `code`), scale
  assumption (prototype, single instance), requirement that links must not be
  enumerable.
- **AI suggestion:** Initially proposed an MD5 hash of the long URL truncated
  to 7 chars, with the argument that identical URLs map to identical codes.
- **Engineer review:** Rejected the hash approach. Truncating a hash turns
  collisions into correctness bugs (two different URLs, same code), and
  same-URL-same-code conflicts with per-link settings we expect later
  (expiration was already on the product horizon). Asked the AI to redo it as
  random base62 with the DB constraint as the uniqueness guarantee.
- **Engineer modification:** The revised draft used `ThreadLocalRandom`;
  changed to `SecureRandom` — predictable codes would let outsiders enumerate
  links. Also bounded the retry loop at 5 attempts with a typed exception
  instead of the suggested unbounded `while (true)`.
- **Rejected:** MD5-truncation design; unbounded retry loop;
  `ThreadLocalRandom`.
- **Validation:** Unit tests for length/alphabet/distribution; service tests
  for collision retry and retry exhaustion.
- **Decision:** Random base62 + constraint + bounded retry approved.

---

## Entry 3 — Collision handling and transactions (Phase 1, greenfield)

- **Task:** Make create() correct when two requests draw the same code.
- **Context provided to AI:** LinkService skeleton, JPA repository, the
  unique constraint from V1.
- **AI suggestion:** Add an `existsByCode()` check before insert, and annotate
  `create()` with `@Transactional`.
- **Engineer review:** Rejected the pre-check as the mechanism — check-then-
  insert races between the check and the insert; the constraint is the only
  real guarantee. Rejected `@Transactional` on `create()` specifically because
  a constraint violation inside a transaction marks it rollback-only, which
  would break the retry loop; each `saveAndFlush` running in its own
  transaction is what makes retry work. This is the kind of detail that looks
  wrong at first glance, so it's documented in a comment on the method.
- **Engineer modification:** Catch `DataIntegrityViolationException` around
  `saveAndFlush`, retry with a fresh code, log the collision at WARN.
- **Rejected:** `existsByCode` pre-check as the uniqueness mechanism;
  transaction wrapper around the retry loop.
- **Validation:** `createRetriesWithFreshCodeWhenCodeCollides` and
  `createGivesUpAfterExhaustingCollisionRetries` service tests.
- **Decision:** Constraint-plus-retry approved; rationale captured in code
  comment and greenfield doc.

---

## Entry 4 — Redirect semantics and URL validation (Phase 1, greenfield)

- **Task:** Decide redirect status code; validate submitted URLs.
- **Context provided to AI:** Product context (analytics planned), controller
  skeletons.
- **AI suggestion:** 301 Moved Permanently "for SEO and browser caching", and
  regex-based URL validation accepting any scheme.
- **Engineer review:** Rejected 301 — permanent caching means repeat visits
  bypass the service entirely, which silently kills click analytics and makes
  links impossible to disable later. Chose 302. Rejected regex validation and
  the any-scheme policy: `javascript:` or `file:` targets would make our
  redirect a link-laundering vector.
- **Engineer modification:** Parse with `java.net.URI`; allowlist http/https;
  require absolute URL with host; cap length at a configured maximum; store
  the URL as submitted (no canonicalization — changing case or slashes can
  change meaning on the target server).
- **Rejected:** 301 redirect; regex validation; unrestricted schemes.
- **Validation:** Parameterized validator tests including `javascript:`,
  `file:`, `data:` schemes; web test asserting 302 + Location header.
- **Decision:** 302 + parse-based allowlist validation approved.
