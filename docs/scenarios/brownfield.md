# Brownfield Scenario — Change Request: Link Expiration

## The change request

> "Marketing wants short links that stop working after a chosen date — e.g.
> campaign links that go dead when the promotion ends."

This arrives against an existing, tested, running system (v1 links + v2
analytics). The job is the smallest change that satisfies it without breaking
anything that exists today.

## Existing behavior analysis

- `POST /api/links` stores `{code, long_url, created_at}`; links live forever.
- `GET /{code}` resolves via `LinkService.resolveForRedirect` → 302, and
  publishes a click event.
- `GET /api/links/{code}` and `/stats` read link metadata and analytics.
- Tests pin all of this down — they are the regression net for this change.

## Impact analysis (what the change touches, and what it must not)

| Component | Impact |
|---|---|
| Schema | New nullable `expires_at` column (Flyway V3). Nullable is the compatibility decision: existing rows get NULL = never expires, no backfill, no behavior change for existing links. |
| `Link` entity | New optional field + `isExpired(now)` domain method. Existing 3-arg constructor retained and delegating, so every existing call site and test compiles unchanged. |
| `CreateLinkRequest` / `LinkResponse` | Optional `expiresAt` in, echoed back out. Absent field = null = old behavior — the API change is additive, existing clients unaffected. |
| `LinkService` | `create` gains an expiration parameter (old signature kept as a delegate); creation rejects a past `expiresAt`; `resolveForRedirect` refuses expired links **before** publishing a click event. |
| Error handling | New `410 Gone` for expired links in the central handler. |
| Analytics | Deliberately untouched except that expired redirects record no click — no redirect happened. |
| Redirect performance | Unchanged: the expiry check is an in-memory comparison on the already-loaded row; no extra query. |

## Key decisions

- **410 Gone, not 404.** The resource existed and is intentionally gone —
  that is precisely what 410 means, and it gives link owners a diagnosable
  signal ("your link expired") instead of "never heard of it." Trade-off
  noted: 410 confirms the code once existed; acceptable for this product.
- **Check expiry in code, not in the query.** The alternative
  (`findByCodeAndExpiresAtAfter(...)`) returns empty for expired links,
  collapsing "expired" into "not found" and forcing a 404. Loading the row
  and checking `isExpired` keeps the two cases distinct for correct status
  codes, at zero extra query cost.
- **Boundary:** a link whose `expires_at` equals "now" is expired
  (`!now.isBefore(expiresAt)`), and creation requires `expiresAt` strictly in
  the future. One rule, tested at the boundary.
- **No cleanup job.** Expired rows stay (their analytics remain queryable and
  the row must exist to serve 410). A retention job is a separate future
  requirement, listed in limitations — not smuggled into this change.

## Regression risks and how they're covered

- *Existing links (NULL expiry) must behave exactly as before* → nullable
  column + delegating constructor/method; the entire pre-existing test suite
  runs unmodified against the changed code.
- *Existing API clients must not break* → new request field optional, new
  response field additive; existing integration tests unchanged.
- *Redirect latency must not regress* → no additional query on the hot path.
- *Rollback*: the column is additive and ignored by prior code — reverting
  the app code alone is a safe rollback; the column can stay.

## Validation

- Entity: `isExpired` boundary tests (before / exactly at / after).
- Service: create stores future expiry; rejects past and exactly-now expiry
  before touching the repository; expired redirect throws and publishes no
  click event; unexpired link with an expiry still redirects.
- Web: expired redirect → 410 problem detail.
- Integration (real clock): past expiry → 400; future expiry → echoed in
  response and redirect still works; links without expiry unchanged.
