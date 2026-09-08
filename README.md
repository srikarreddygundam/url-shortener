# URL Shortener

A URL shortener service built as a software engineering exercise, covering core shortening and redirect APIs, click analytics, link expiration, validation, testing, and reliability considerations.

The implementation is developed through three scenarios: greenfield development, an ambiguous analytics requirement, and a brownfield expiration change.

- Architecture and design decisions: `docs/ARCHITECTURE.md`
- API contract: `docs/openapi.yaml`
- Scenario walkthroughs: `docs/scenarios/`
- Engineering and AI usage notes: `docs/AI_USAGE.md`
- Final engineering summary, trade-offs, and limitations: `docs/ENGINEERING_SUMMARY.md`

## Prerequisites

- JDK 21+ (the only requirement — the DB is embedded, Maven comes via wrapper)

## Run

```bash
./mvnw spring-boot:run
```

Starts on `http://localhost:8080` with an in-memory H2 database
(PostgreSQL-compatibility mode); Flyway applies the schema on startup.
Data lives for the life of the process — a deliberate prototype choice.

## Build and test

```bash
./mvnw verify
```

Runs compile, the full test suite (unit, web-slice, and end-to-end
integration tests — the exact count is printed in the build output),
Checkstyle, and the JaCoCo coverage report
(`target/site/jacoco/index.html`).

## API

Create a short link (optionally expiring):

```bash
curl -s -X POST localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/docs","expiresAt":"2026-12-31T00:00:00Z"}'
# 201 → {"code":"Ab3xY9z","shortUrl":"http://localhost:8080/Ab3xY9z",
#        "longUrl":"https://example.com/docs","createdAt":"...","expiresAt":"..."}
```

Follow it / inspect it:

```bash
curl -i localhost:8080/Ab3xY9z              # 302 + Location; 410 once expired
curl -s localhost:8080/api/links/Ab3xY9z            # link details
curl -s localhost:8080/api/links/Ab3xY9z/stats      # {"totalClicks":n,"lastClickAt":...}
```

Errors are RFC 7807 `application/problem+json`: 400 (invalid URL, past
expiry, malformed body), 404 (unknown code), 410 (expired link),
500 (generic message; details stay in the server log).

Health: `GET /actuator/health`.

## Configuration

| Property             | Default                 | Meaning                                |
| -------------------- | ----------------------- | -------------------------------------- |
| `app.base-url`       | `http://localhost:8080` | Base used to build returned short URLs |
| `app.code-length`    | `7`                     | Short-code length (base62)             |
| `app.max-url-length` | `2048`                  | Max accepted long-URL length           |

## Quality gates

**Executed and passing** (Java 21.0.12, macOS, `./mvnw verify`): compile,
full test suite, Checkstyle (0 violations), JaCoCo report. The suite grew
per phase as gates were run — 31 tests (P1) → 45 (P2) → 62 (P4) → current
count in your build output. Every endpoint was additionally smoke-tested
over HTTP after each phase. `.github/workflows/ci.yml` runs the same
`./mvnw verify` gate on push and pull request.

**Recommended for a production pipeline, not run here:** OWASP
dependency-check, SpotBugs/Sonar static analysis, container image scanning,
load/performance testing, and coverage thresholds enforced in the build.

## Key assumptions

- Anonymous API; authentication, authorization, and rate limiting are API
  gateway / platform concerns in production (documented in
  `docs/ARCHITECTURE.md`), not implemented in the prototype.
- Each submission creates a new link (no dedup of identical URLs) so links
  can carry per-link settings such as expiry.
- 302 redirects (not 301) so analytics see every click and links stay
  controllable; a link expires the moment `now >= expiresAt`.
- Analytics stores per-click timestamp + referrer only — no IP or
  user-agent (privacy call, recorded in `docs/scenarios/ambiguous.md`).

## Known limitations

In-memory DB (data gone on restart — swap the JDBC URL for Postgres in
production); no auth or rate limiting in-app; click events grow unboundedly
(no retention job); stats computed by COUNT per request (fine at prototype
volume); async click events are lost if the process dies before the insert;
single-instance deployment assumed. Scale evolution:
`docs/ARCHITECTURE.md`.
