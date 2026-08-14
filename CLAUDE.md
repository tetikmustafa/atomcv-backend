# AtomCV Backend — Working Context

## What This Project Is

AtomCV lets a user build a structured "Master Profile" once, then generate
job-specific, ATS-optimized resumes and cover letters in seconds — with a
**mathematically guaranteed page limit** and **structural protection against
fabricated content**.

The core architectural insight: a person's professional history is not a CV
file — it is a **structured dataset**. A CV is a transient *view* rendered
from that data.

This repository contains **only the backend**. The frontend lives in a
separate repository (`atomcv-frontend`, Next.js). Never add frontend code here.

## Architecture Documents

Full specifications live in `docs/`. They are written in Turkish.

| Document | Contents |
|---|---|
| `docs/urun-konsept-dokumani-v2.md` | Product concept, user journeys, scenarios |
| `docs/teknik-mimari-dokumani.md` | All technical decisions, schema, algorithms |

**Do not read both documents in full every session.** Use this map to read
only what the current task needs:

| Task | Read section |
|---|---|
| Any task (first session) | Bölüm 4 (design principles) |
| Any task touching code already written | **EK D** — what the implementation decided where the body is silent, wrong, or now outdated |
| Module placement | Bölüm 10 |
| Database work | Bölüm 13, 14, 15, 16 |
| Pipeline phase A (job analysis) | Bölüm 18 |
| Pipeline phase B (scoring) | Bölüm 19 |
| Pipeline phase C (selection) | Bölüm 20 |
| Pipeline phase D (rewriting) | Bölüm 21 |
| Pipeline phase E (rendering) | Bölüm 22 |
| Pipeline phase F (verification) | Bölüm 23 |
| Pipeline phase G (editing loop) | Bölüm 24 |
| Result type / error hierarchy | Bölüm 25 |
| Render cost measurement | Bölüm 26 |
| LLM gateway | Bölüm 27 |
| Embeddings | Bölüm 28 |
| LaTeX container | Bölüm 29 |
| Job queue / SSE | Bölüm 30 |
| CV upload / profile extraction | Bölüm 31 |
| Multilingual atoms | Bölüm 32 |
| Templates / customization | Bölüm 33 |
| Cover letter | Bölüm 34 |
| API contract | Bölüm 35 |
| Auth / session | Bölüm 40 |
| Multi-tenant isolation | Bölüm 41 |
| Input security / injection | Bölüm 42, 43 |
| Quota / cost control | Bölüm 44 |
| Deployment / server | Bölüm 46 |
| CI/CD | Bölüm 47 |
| Observability | Bölüm 48 |
| Testing | Bölüm 51 |
| Performance budgets | Bölüm 52 |
| Prompt management | Bölüm 53 |
| Step-by-step build guide | Bölüm XI-A |
| Repo structure / prompts | Bölüm XI-B |

## The Eight Design Principles

Every decision in this codebase traces back to one of these. When facing a
design question, consult these first.

1. **Separate content from presentation.** No format-specific markup in the
   data model. Emphasis is semantic (`technology`, `metric`), converted to
   `\textbf{}` / `<strong>` / bold-run only at render time.
2. **Do not use an LLM where determinism is possible.** Scoring, selection,
   rendering and validation are pure code. LLMs only handle language
   understanding and language generation.
3. **Prevent fabrication structurally, not by asking.** Scope limit + task
   limit + automated validation.
4. **Never produce a silently bad result.** Explain the problem, state the
   cause, offer concrete options, let the user decide.
5. **Run checks before incurring cost.** All validation happens before any
   LLM call.
6. **Edits apply to selection state, never to rendered output.**
7. **Transparency.** Every selection decision is explainable to the user.
8. **Never silently overwrite user's own work.** Ask instead.

## Tech Stack

- Java 21 (virtual threads, sealed interfaces, records, pattern matching)
- Spring Boot 3.x (Web MVC, Data JPA, Security, Actuator)
- PostgreSQL 17 + pgvector
- Flyway (schema migrations)
- Redis (session, cache, ephemeral profiles, rate limit counters)
- XeLaTeX in an isolated container (PDF rendering)
- BGE-M3 self-hosted (embeddings, via text-embeddings-inference)
- Testcontainers, JUnit 5, ArchUnit

No Lombok. Use records for value objects and plain constructors elsewhere.

## Module Map

```
identity/    auth, session, account
profile/     Master Profile: Section > Entry > Atom > AtomVariant
ingestion/   CV upload, extraction, structuring, GitHub import
generation/  pipeline (phases A-G), scoring, selection, validation
rendering/   LaTeX/HTML/DOCX renderers, measurement, templates
llm/         provider gateway, prompt registry, telemetry
embedding/   embedding provider abstraction
compilation/ LaTeX compiler client
jobs/        queue, workers, SSE
tracking/    application tracking
billing/     quota, cost tracking, anomaly detection, kill switch
email/       Resend client, templates, suppression list
shared/      user-scoped repository base, error presentation, config
```

Modules communicate only through public interfaces. No cyclic dependencies
(enforced by ArchUnit). `shared/` must not depend on any business module.

## Absolute Rules — Never Violate

1. **`spring.jpa.hibernate.ddl-auto` is always `validate`.** Never `update`,
   never `create`. Schema is owned solely by Flyway.
2. **Never modify an applied Flyway migration.** Write a new one.
3. **All data access goes through a scoped repository.** `UserScopedRepository`
   for tables with `user_id`, `ProfileScopedRepository` for tables with only
   `profile_id` — and a `ProfileRef` can only be produced by comparing the
   acting user against the profile's owner. Never call a raw `JpaRepository`
   from a controller or service that handles user data. This is the IDOR
   defense and it is enforced by ArchUnit across `..api..` and `..service..`,
   plus a per-module rule keeping raw repositories inside `..repository..`.
4. **Never log user content.** No `RichContent`, no atom text, no job
   description, no email body. Log `ContentShape` (statistics) instead.
5. **Never put secrets in code.** Environment variables only.
6. **The rendering module must never depend on the llm module.** Rendering is
   deterministic by design.
7. **Never call `String.toLowerCase()` / `toUpperCase()` without a locale**
   for identity or matching operations — use `Locale.ROOT`. Turkish locale
   turns "SQL" into "sqı" and silently breaks skill matching.
8. **LaTeX compilation always uses `-no-shell-escape`** and runs in the
   isolated container.
9. **Never let the LLM produce LaTeX.** Renderers produce LaTeX; LLMs produce
   plain text only.
10. **Page budget is measured in points (pt), never in lines.** Rounding to
    whole lines accumulates error.

## Development Commands

```bash
make dev        # core services (postgres, redis, mailpit) + backend with fake LLM
make dev-full   # also starts latex + embeddings containers
make db-reset   # wipe database and re-run migrations (LOCAL ONLY)
make record     # run with local-record profile to capture LLM fixtures
make test       # unit + architecture tests
make test-int   # integration tests (Testcontainers)
```

Spring profiles:
- `local,local-fake` — default for daily work; no real LLM calls, no cost
- `local,local-record` — real LLM calls, responses saved as fixtures
- `local,local-real` — real LLM calls, nothing saved (prompt work)
- `prod` — production

### What this machine needs you to know

Facts that are true here and nowhere in the architecture documents. Each one
cost a debugging round to find.

- **Run `make` from Git Bash.** The Makefile refuses to run under `cmd.exe` or
  PowerShell, and its recipes call `sh ./gradlew`: GNU Make runs a
  metacharacter-free recipe line straight through `CreateProcess`, and
  `./gradlew` is not a Windows executable.
- **The Makefile includes and exports `.env`.** Compose reads that file by
  itself, Spring does not — without the include, a changed `POSTGRES_PASSWORD`
  breaks `make dev` with an authentication failure that looks like a code bug.
- **`native.encoding` is `Cp1254` here and UTF-8 on the runner.** The source
  encoding is pinned in `build.gradle.kts`; do not remove it.
- **`gradlew` must stay mode 100755.** Committed as 100644 it fails every
  Linux runner.
- Gradle directly, when a target does not fit: `sh ./gradlew test` (fast, no
  Docker), `sh ./gradlew integrationTest` (needs Docker Desktop running),
  `--tests '*SomeTest'` to narrow.
- A `pre-commit` gitleaks hook runs on every commit. It is installed and
  configured; a commit that prints nothing about secrets did not run it.

## Testing Requirements

Write these tests alongside the code they cover, not afterwards:

| Test | Guards |
|---|---|
| Page limit never exceeded | The product's core promise |
| Determinism (same input → same output, 50 runs) | Phase B and C purity |
| Multi-tenant isolation (every protected endpoint) | Data leakage |
| Locks and structural constraints respected | User control guarantees |
| Anonymous flow writes nothing to user data tables | Privacy claim |
| Profile load uses ≤6 queries | N+1 regression |

**A guard that has never failed is not known to work.** Every rule that exists
to catch something was confirmed against a deliberate violation before being
trusted: the ArchUnit rules against a planted dependency, schema validation
against a renamed column, the query counter against a lower bound, gitleaks
against a real token pattern. Do the same for the next one — and note that
gitleaks allowlists the AWS documentation example keys, so a probe using those
reports a false pass.

Report test counts, not "the suite is green". A suite that runs zero tests
also reports success.

## How We Ship

1. **One branch per slice**, named `feat/…`, `test/…` or `docs/…`. A slice is
   what fits in one review, not one step of the build guide.
2. **`main` is pushed before a PR is opened.** A rebase merge rewrote twelve
   unpushed local commits once; the fix was `git reset --hard origin/main`
   after verifying the trees matched.
3. **Split commits by logical unit**, not by file. Conventional Commits; the
   body says *why*, in English, and names the Bölüm it comes from.
4. **The developer decides when to merge.** Open the PR, wait for all five
   checks (`gh pr checks <n> --watch`), report, and ask. On approval:
   `gh pr merge <n> --rebase --delete-branch`, then `git checkout main` and
   `git reset --hard origin/main`. History stays linear.
5. **Deviations go into `EK D` in the same PR as the code**, and anything the
   frontend must act on goes into `EK D.9` and is said out loud in the
   conversation. The document is synced to the frontend repository with
   `scripts/sync-docs.sh` — deliberately, not automatically; the developer
   asked for it at the end of Stage 1.

## Code Style

- Code, comments, commit messages, and identifiers: **English**
- Conversation with the developer: **Turkish**
- Commit format: Conventional Commits (`feat(scope):`, `fix(scope):`, ...)
- Prefer records for value objects, sealed interfaces for closed hierarchies
- Prefer `Result<T>` over exceptions for expected failure paths

## Resolved Decisions

These resolve ambiguities or contradictions found in the architecture
documents. They are settled — do not re-open them without asking.

| Question | Decision |
|---|---|
| Repo layout: XI-A.1 shows one monorepo, XI-B.2 shows two repos | **XI-B.2 wins.** This repo is backend-only with `src/` at the root. Wherever XI-A.2 assumes a `backend/` subfolder or a frontend job, adapt it to this layout. |
| Scope of the first Flyway migration (XI-A.2 Adım 0.3 says "identity + profile core", Bölüm 13 gives one full file) | **`V1__initial_schema.sql` contains all of Bölüm 13.** Empty tables cost nothing; splitting would mean revisiting the same tables in V2/V3 under a rule that forbids editing applied migrations. |
| ArchUnit `noRawRepositoryInApi` covers only `..api..`, but absolute rule 3 says "controller or service" | **Widen the rule to cover `..service..` as well.** IDOR is rated a very high impact risk. |
| Lombok listed in Adım 0.1 but absent from the sample `build.gradle.kts` | **Not used.** |
| Makefile vs. scripts on Windows | **Makefile**, as documented. `make record` and `make test-int` are part of it even though Adım 0.5 omits them. |
| CI scope: Adım 0.7 shows build + gitleaks, XI-B.2 says "build + test + security" | **Widest version from the start** — build, test, gitleaks, Trivy, CodeQL, dependency scanning. |
| Nothing guarantees the denormalized `profile_id` on entries/atoms/atom_variants agrees with the parent row's profile | **Composite foreign keys added in V1** (`UNIQUE (id, profile_id)` on parents). A mismatch would otherwise be a silent cross-tenant leak. Not enforced when `atoms.entry_id IS NULL`, which is the intended case for section-level atoms. |
| `llm_invocations.user_id` has no FK, contradicting the "one DELETE removes everything" promise in Bölüm 13.1 | **FK with `ON DELETE SET NULL`** on both `user_id` and `job_id`. Aggregate cost history survives account deletion; the personal link does not. |
| Bölüm 51.6 asserts an anonymous run changes no row count in *any* table, but the queue (`jobs.anon_session_id`) and `llm_invocations` are Postgres-backed | **The test narrows to user data tables.** Open for Stage 3: decide whether the anonymous path uses the queue at all. |
| ArchUnit rules fail with "failed to check any classes" while the module packages hold only `package-info.java` | `src/test/resources/archunit.properties` sets `archRule.failOnEmptyShould=false`. **Remove it in Stage 1** once the modules carry real classes: while it is on, renaming a package makes the affected rule match nothing and pass silently. |
| Which resources can carry an ETag (frontend gap 6) | Only the six tables V1 gives a `version` column: `profiles`, `sections`, `entries`, `atoms`, `atom_variants`, `applications`. **`generations` has none**, so generation resources get no optimistic locking. Emit `ETag: "7"` on single-resource GETs and a `version` field on every item in collection responses. |
| Anonymous session TTL: absolute two hours (Bölüm 9) or sliding (frontend gap 8) | **Sliding — the TTL refreshes on activity.** Cutting off a user mid-review would destroy work they just invested effort in, which is what P8 exists to prevent. Note this widens the "deleted after 2 hours" wording in Bölüm 9 to "two hours after the last activity"; the product copy must say so. |
| What happens when an anonymous profile is claimed by an account that already has one (frontend gap 2) | Offer **replace or keep** only. `merge` needs atom-level deduplication (Jaro-Winkler + embedding, Bölüm 7), which is Stage 4 work. Do not promise it in the API before then. |
| `title` in error bodies: displayed or not (frontend gap 5) | **Developer-facing English, never displayed.** RFC 7807 wants it stable across occurrences, and Bölüm 35.4's own rule says the server sends translation keys rather than text. The Turkish example in that section is misleading. |
| How `GET /profile/export` selects its format (frontend gap 15) | `?format=json\|markdown`, matching the download endpoint. |
| Is `/api/v1/warmup` part of the public API (frontend gap 16) | **No.** Operational endpoint (Bölüm 52.5), excluded from the OpenAPI schema and not routed through nginx. |
| Does the frontend call the API during server rendering (frontend gap 14) | **No.** All authenticated fetching happens in the browser; server components render shell and static content only. Revisit deliberately if it ever changes — it needs an internal base URL and a cookie-forwarding decision. |
| Bölüm 41.2's `UserScopedRepository` filters on `ownerId()`, but `sections`, `entries`, `atoms` and `atom_variants` carry no `user_id` | **Two bases.** `UserScopedRepository` for tables with `user_id`; `ProfileScopedRepository` for the four that hang off a profile. The ownership check happens once, when a `ProfileRef` is resolved — `ProfileRef.persistent` compares the acting user against the profile row's owner, its constructor is private, and it is not a record precisely so that no unchecked way to build one exists. Rejected: adding `user_id` to the child tables (a second denormalization to keep consistent) and a `profile_id IN (SELECT ...)` subquery on every read (hot in the measurement and selection paths). |
| What a scoped repository does with a row belonging to someone else | **Reads return empty, writes throw.** A foreign row that read as forbidden would confirm it exists; a foreign row being *written* means the code built an object with the wrong owner, which is a defect and not a request to answer politely. |

## How We Work Together

1. **Apply the documented decisions as written.** If you disagree with a
   decision, or find something missing or contradictory in the documents,
   raise it *before* implementing — never silently deviate.
2. **Work in small steps.** State what you are about to do, wait for
   approval, then do it. Do not create twenty files in one turn.
3. **Ask when the documents are ambiguous.** A wrong assumption baked into an
   early layer is expensive to remove.
4. **Update this file** when we make a decision that future sessions need to
   know.
5. **Record every deviation, addition and correction in `EK D` of
   `docs/teknik-mimari-dokumani.md`**, in the same commit as the code it
   describes. The architecture document is the source of truth and says so
   itself; this file is session context. Where the document body would now
   mislead a reader, leave a one-line pointer there too — as Bölüm 41.2 and
   14.1 carry.
6. **Say so when something changes the frontend's work.** Collect it in
   `EK D.9`, which is the only place the other repository needs to read, and
   name it in the conversation as well. The document reaches that repository
   through `scripts/sync-docs.sh`.

## Current Stage

<!-- Update this section as work progresses -->
**Stage 0 — Skeleton: complete.** Package tree, Gradle build, Docker Compose
core profile, Flyway baseline (all of Bölüm 13), health endpoint, ArchUnit
rules, Testcontainers integration tests, CI with CodeQL/Trivy/gitleaks,
Makefile, repository documentation.

**Stage 1 — Walking Skeleton: in progress. Adım 1.1 is complete.**

### What exists

| Package | Classes |
|---|---|
| `profile.domain.content` | `RichContent`, `Run`, `Mark`, `ContentMigrator`, `RichContentConverter` |
| `profile.domain` | `Section`, `Entry`, `Atom`, `AtomVariant`, `ProfileTree`, and six enums (`SectionKind`, `SectionLayout`, `AtomKind`, `AtomSource`, `VariantAuthor`, `Tone`) |
| `profile.repository` | Four package-private Spring Data interfaces, four public scoped facades |
| `profile.service` | `ProfileAssembler` (`load(ProfileRef)` and the static, pure `assemble(...)`) |
| `shared.security` | `UserContext`, `UserRole`, `UserOwned`, `ProfileOwned`, `ProfileRef`, `UserScopedRepository`, `ProfileScopedRepository`, `CrossTenantAccessException` |
| `shared.util` | `LowercaseEnumConverter` |

70 unit tests, 20 integration tests. Every decision behind these is in `EK D`
of the architecture document — read D.2 through D.5 before touching them.

### Deliberately absent — do not "fix" without asking

- **No `Profile` entity yet.** It lands with Adım 1.2, and brings `contact`
  and `preferences` (Bölüm 14.2, 14.3) as typed records rather than maps.
  `ProfileTree` therefore carries no profile head, and
  `UserScopedRepository` has no subclass yet — `Profile` will be the first.
- **`atoms.embedding` is unmapped.** Stage 2. `vector(1024)` has no Hibernate
  type and nothing computes an embedding yet.
- **`ProfileRef.Scope` has only `PERSISTENT`.** `EPHEMERAL` arrives with the
  anonymous flow in Stage 3; adding the constant earlier, with no checked way
  to produce one, would be the way around the ownership check.
- **`tags` and `atom_tags` have no entities.** Nothing reads them before
  Stage 2 scoring.
- **No `api` package, no controller, no springdoc.** First endpoint is Adım
  1.2, and the contract below comes first.
- `UserScopedRepository` has no `findAll`: the Bölüm 41.2 snippet calls
  `findByUserId`, which is not on `JpaRepository`. Subclasses add their own
  narrowed finders, as the profile repositories do.

### Resume here

**Adım 1.2 — manual profile CRUD.** The API contract is already settled and
lives in **`EK D.6`** of the architecture document: the two closed vocabularies,
ETag scope and format, pagination, download, and the Stage 2/3 items. Build
against it rather than re-deciding it. In order:

1. Type the error catalogue: every code's `params` keys **and their types**,
   derived from the `PipelineError` records in Bölüm 25.2. That is the one piece
   `EK D.6` still lists as open, and the frontend's ICU messages cannot be
   written without it.
2. Add springdoc-openapi with the first endpoint, carrying the
   `resolutions[].action` enum, the error `code` enum and the ETag/pagination
   headers.
3. `Profile` entity + `ProfileRepository` over `UserScopedRepository`, and a
   resolver that turns a `UserContext` into a `ProfileRef` — the only place
   that mapping is allowed to happen.
4. Section/entry/atom CRUD, completeness percentage, and the ≤6 query test
   extended to the full load once the profile head is part of it.

Then Adım 1.3 (LaTeX container), 1.4 (renderer), 1.5 (measurement), 1.6
(selection), 1.7 (Faz E/F + PDF), 1.8 (general CV mode), 1.9 (golden set).

Still open in Stage 1:
- **Where does the first `UserContext` come from?** Identity is Stage 3
  (XI-A.6), but Adım 1.2's endpoints need an acting user, and `ProfileRef`
  cannot be produced without one. Decide the stand-in before the first
  controller — likely a fixed user resolved under the `local` profile only,
  wired so that it cannot exist under `prod` and disappears when identity
  lands. Do not let it become an untyped "current user" helper.
- Decide how production runs migrations. Bölüm 47 shows a pre-deploy step
  using `--spring.flyway.migrate-only=true`, which is not a real Spring Boot
  property, so Flyway currently runs at startup in prod too.
- Add springdoc-openapi with the first real endpoint, and make the published
  schema carry the `resolutions[].action` enum, the error `code` enum and the
  ETag/pagination headers. Six of the frontend's sixteen contract gaps close
  by themselves once that schema exists — but only if it carries more than
  happy-path payloads.
- Register the LaTeX container under `profiles: [full]` in `docker-compose.yml`.
  The `full` profile currently matches no service, so `make dev-full` starts
  the core services and silently does nothing else.
- The CI `scan` job finds nothing until a Dockerfile exists. When the LaTeX
  image lands, add an image scan next to the filesystem one.
- Decide whether to add Spotless. Bölüm 47.1 runs `spotlessCheck`, but no
  formatter is configured, so CI has no formatting gate at all today.

Carry into Stage 2:
- Quota reset needs a time zone. `usage_counters.period` is a `DATE`, so the
  daily counter rolls over at a day boundary that nothing defines yet.
- Create the `application-local-fake.yml`, `-local-record` and `-local-real`
  profile files with the LLM gateway. They do not exist yet, and Spring
  ignores an unknown profile silently — so `make dev` looks like it works
  today while `local-fake` contributes nothing.

Carry into Stage 3:
- `CREATE UNIQUE INDEX ON jobs (user_id, idempotency_key)` does not dedupe
  anonymous requests: `user_id` is NULL there and Postgres treats NULLs as
  distinct, so the same key creates a second job. Needs a migration keying on
  `COALESCE(user_id::text, anon_session_id)` — deferred because it presumes
  the anonymous path uses the queue at all, which is still open above.
- The anonymous TTL slides on activity, so the user-facing copy must say
  "two hours after your last activity", not "two hours". Bölüm 9 and the
  product document still carry the absolute wording; both need updating, and
  the frontend owns the string.

Open, no stage assigned:
- V1 applies `CHECK` constraints to some enum-like columns (`sections.layout`,
  `applications.status`, `jobs.status`) and leaves others as comments
  (`sections.kind`, `atoms.kind`, `generations.status`, `jobs.type`,
  `llm_invocations.outcome`). This mirrors Bölüm 13 deliberately rather than
  inventing constraints. Adding the missing ones later is a cheap migration,
  since a `CHECK` does not rewrite the table.

Next: Stage 1 (Walking Skeleton) — domain model, manual profile CRUD,
LaTeX container, measurement system, selection algorithm, PDF output.
No LLM in Stage 1.
