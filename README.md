# URL Shortener

A URL shortener service built as an AI-assisted engineering exercise: core
shortening/redirect APIs, click analytics, and link expiration, developed
through three documented scenarios (greenfield, ambiguous requirement,
brownfield change request) with a full AI-usage traceability log.

- Architecture and design decisions: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Scenario walkthroughs: [`docs/scenarios/`](docs/scenarios/)
- AI traceability log (generated / edited / rejected): [`docs/AI_USAGE.md`](docs/AI_USAGE.md)
- Final summary, trade-offs, limitations: [`docs/ENGINEERING_SUMMARY.md`](docs/ENGINEERING_SUMMARY.md)

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

| Property | Default | Meaning |
|---|---|---|
| `app.base-url` | `http://localhost:8080` | Base used to build returned short URLs |
| `app.code-length` | `7` | Short-code length (base62) |
| `app.max-url-length` | `2048` | Max accepted long-URL length |

## Quality gates

Executed locally on every phase via `./mvnw verify` (compile, tests,
Checkstyle, JaCoCo), plus manual HTTP smoke tests of every new endpoint
after each phase. Verified runs during development: Phase 1 — 31 tests;
Phase 2 — 45 tests; Phases 3–4 grow the suite further (see the `Tests run:`
line of your build).

Recommended for a production pipeline but **not** run here: OWASP
dependency-check, SpotBugs/Sonar static analysis, container scanning, load
tests, and CI enforcement of all of the above per commit.

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
