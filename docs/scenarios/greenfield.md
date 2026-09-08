# Greenfield Scenario — Core Shortening and Redirect

## Requirement

> "Users can submit a long URL and receive a short link; visiting the short
> link takes them to the original URL."

## Requirement interpretation

Two capabilities with very different traffic profiles:

1. **Create** — `POST /api/links`: accept a long URL, validate it, persist it
   under a unique short code, return the short URL. Write path, low volume.
2. **Resolve** — `GET /{code}`: look up the code and redirect. Read path,
   high volume, latency-sensitive — this is the product. Everything else can
   be slower; this cannot.

Normalized engineering problem: a keyed lookup service with a
uniqueness-constrained write side and a read side optimized for one indexed
query per request.

## Assumptions and ambiguities

- **Duplicate long URLs** → each submission gets its own link. Deduplication
  would prevent per-link settings later (expiry, ownership) and requires
  indexing a 2 KB column. Recorded as a product question.
- **Redirect status** → 302, not 301. A 301 is cached permanently by browsers,
  so repeat visits never reach the service again — that silently destroys
  click analytics and removes any ability to disable a link later.
- **Code format** → 7-char base62 (62^7 ≈ 3.5 × 10^12 combinations), no custom
  aliases (extension, not in scope).
- **Anonymous API** — no auth requirement stated; authn/z is a gateway concern
  in production (documented in the security notes).

## Task decomposition

1. Schema: `links` table with unique constraint on `code` (Flyway V1) — blocks everything else
2. Domain + repository: `Link`, `LinkRepository.findByCode`
3. Code generation: `CodeGenerator` seam + random base62 implementation
4. URL validation: parse-based validation with scheme allowlist
5. Service: create-with-collision-retry, lookup
6. API: create + details endpoints, redirect endpoint, central error handling
7. Tests: unit (generator, validator, service), web slice, end-to-end flow

## Technical approach and key decisions

| Decision | Chosen | Alternative | Why not the alternative |
|---|---|---|---|
| Code generation | Random base62 + DB unique constraint + bounded retry (5) | Truncated hash of URL | Truncation makes collisions a correctness bug; same URL → same code conflicts with per-link settings |
| | | Base62-encoded sequential ID | Codes become enumerable — anyone can walk every link in the system; couples code to DB identity |
| Collision handling | Rely on unique constraint, catch violation, retry | `existsByCode` pre-check | Check-then-insert races under concurrency; the constraint is the only real guarantee anyway |
| Randomness | `SecureRandom` | `ThreadLocalRandom` | Predictable sequences make codes guessable; SecureRandom cost is irrelevant on the low-volume write path |
| Validation | `java.net.URI` parsing + scheme allowlist | URL regex | Regexes for URLs are famously wrong in both directions; the parser is the spec |
| Errors | RFC 7807 `ProblemDetail` via `@RestControllerAdvice` | Ad-hoc error JSON per controller | One format for clients, no error-shaping in controllers |

Collision math for the record: at 1M stored links, the chance a random 7-char
code collides is ~1M / 62^7 ≈ 0.00003 per attempt — the retry loop is a
correctness guard, not a hot path.

## Failure scenarios considered

- Two concurrent requests generate the same code → second insert violates the
  constraint, retries with a fresh code (tested at service level).
- Generator exhausts retries (pathological) → 500 with a generic message; the
  real cause is logged server-side, not leaked to the client.
- Unknown/malformed code on redirect → 404 problem detail; paths with
  characters outside `[0-9A-Za-z]` never reach the database (mapping regex).
- DB down → requests fail with 500s from the framework; acceptable for the
  prototype, noted in limitations (no degradation path without a cache).

## Validation

- Unit: generator (length, alphabet, distribution sanity), validator (schemes,
  host, length, malformed input), service (happy path, collision retry,
  retry exhaustion, invalid input short-circuits before the DB).
- Web slice: status codes, `Location` headers, problem-detail bodies,
  bean-validation 400s, regex mapping rejects bad paths without a lookup.
- Integration: full create → redirect → details flow against the real schema;
  duplicate submissions produce distinct codes.

Executed evidence lives in the README quality-gates section.

## Result

`POST /api/links` and `GET /{code}` implemented as designed; all decisions
above traceable to code and tests. AI assistance and review trail:
`docs/AI_USAGE.md` entries 2–4.
