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
  JaCoCo all executed locally (see README quality gates section).
- **Decision:** Scaffold approved as the baseline for all later phases.
