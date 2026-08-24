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

## Documentation Layout

Documentation is split by **access pattern**, not by topic. Read accordingly.

### Read every session (~600 lines total)

| File | What |
|---|---|
| `docs/INDEX.md` | Task → spec file routing map |
| `docs/STATUS.md` | Where both repos are |
| `docs/handoff/to-backend.md` | Open items from frontend — **handle these first** |
| `docs/notes/current.md` | Active-stage build notes for this repo |

### Read on demand — never in full

`docs/spec/**` holds the full specification, split into 18 files.
**Never read a spec file end to end.** Consult `docs/INDEX.md` for the right file,
then search before reading:

```bash
rg -n "<term>" docs/spec/<file>.md      # locate
# then read only the matching range
```

Full spec is ~8,500 lines. The right file is 200-1,100. Reading everything wastes
15-40× the tokens and buries the relevant part.

### Never read routinely

- `docs/notes/archive/**` — closed stages, archaeology only
- `docs/handoff/resolved/**` — settled cross-repo items

### Ownership

| Path | Owner | Synced |
|---|---|---|
| `docs/spec/**`, `docs/INDEX.md` | **this repo** | → frontend via `scripts/sync-spec.sh` |
| `docs/STATUS.md` | shared | both ways |
| `docs/handoff/**` | shared | both ways — the real channel |
| `docs/notes/**` | this repo | never synced |

## Recording Deviations

When implementation departs from the spec, record it in `docs/notes/current.md`:

- **Sapma** — spec says X, we do Y, with reason
- **Ekleme** — spec was silent, we decided
- **Düzeltme** — spec is wrong, here is the correct statement

Rules:
- `notes/current.md` **must stay under 200 lines.** When a stage closes, move it to
  `notes/archive/stage-<n>.md` and start empty.
- If a deviation is **permanent**, write it into the relevant `docs/spec/` file and
  delete it from notes. Notes are a rolling log, not a second specification.
- If it affects the frontend, also add an item to `docs/handoff/to-frontend.md`.

## Cross-Repo Communication

`docs/handoff/to-frontend.md` is the channel. Each item:

```markdown
### B-nnn · Short title
**Since:** commit <sha> · Step <n> · **Spec:** spec/<file>.md § <section>
**Action:** what the frontend must do
```

- IDs are never reused.
- Move items to `## ACK` when the other side confirms; archive to `handoff/resolved/`
  when the file approaches 100 lines.
- **OpenAPI schema is authoritative for API shape.** The handoff file carries *why it
  changed and what to do*, not the shape itself.

## Where the Standing Answers Live

These are specified, not summarised here — a second copy would drift.

| Question | File |
|---|---|
| The eight design principles every decision traces back to | `spec/01-foundations.md` § 4 |
| Why this technology and not that one | `spec/02-tech-stack.md` |
| Module layout, package boundaries | `spec/03-architecture.md` § 10 |
| Test strategy, the tests that matter most | `spec/12-quality.md` § 51 |
| Anything else | `docs/INDEX.md` routes it |

Java 21, Spring Boot 3.x, PostgreSQL 17 + pgvector, Flyway, Redis, XeLaTeX in
an isolated container, BGE-M3 self-hosted. **No Lombok** — records for value
objects, plain constructors elsewhere.

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
make golden-costs # re-measure the golden set's render costs (after a fixture changes)
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
- **`make dev-full` rebuilds the LaTeX image on purpose (`--build`).** Compose
  reuses the image it built last time otherwise, and a stale one answers
  without `X-Page-Count`, which the client refuses as `UNAVAILABLE` — a
  generation that fails with the container running and healthy.
- **`make dev-full` does not run the backend**, only the containers. Run it
  first, then `make dev`.
- **`gradlew latexTest` builds the LaTeX image and compiles through it.** It is
  excluded from `integrationTest` because the image takes minutes; run it when
  `docker/latex` changes. Two things it already caught are in
  `docs/notes/archive/stage-1.md` § D.8.1.
- A `pre-commit` gitleaks hook runs on every commit. It is installed and
  configured; a commit that prints nothing about secrets did not run it.

## Testing Requirements

`spec/12-quality.md` § 51 lists what to write and Bölüm 51.2 names the four
tests worth the most. Two rules live here because no spec file enforces them:

**A guard that has never failed is not known to work.** Every rule that exists
to catch something was confirmed against a deliberate violation before being
trusted — the ArchUnit rules against a planted dependency, schema validation
against a renamed column, the query counter against a lower bound, gitleaks
against a real token pattern. Do the same for the next one. Four probes that
report a **false pass**: the AWS documentation example keys (gitleaks allowlists
them); an ArchUnit probe reading a compile-time constant (javac inlines it —
plant a method call); `@Array(length)` against `vector(1024)` (DDL generation
only, so 512 validates clean — needs a real round trip); and a test asserting
**no duplicate claim**, which passes with `SKIP LOCKED` removed — that clause
buys **liveness**, so hold a lock and assert the rival claim returns *at once*.

**Report test counts, not "the suite is green".** A suite that runs zero tests
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
5. **Deviations go into `docs/notes/current.md` in the same PR as the code**,
   and anything the frontend must act on becomes a `B-nnn` item in
   `docs/handoff/to-frontend.md` and is said out loud in the conversation.
   Both reach the other repository through `scripts/sync-handoff.sh push` —
   deliberately, not automatically.

## Code Style

- Code, comments, commit messages, and identifiers: **English**
- Conversation with the developer: **Turkish**
- Commit format: Conventional Commits (`feat(scope):`, `fix(scope):`, ...)
- Prefer records for value objects, sealed interfaces for closed hierarchies
- Prefer `Result<T>` over exceptions for expected failure paths

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
5. **Record deviations, additions and corrections in `docs/notes/current.md`**,
   in the same commit as the code they describe — see *Recording Deviations*
   above for the three record types and the promotion rule. `docs/spec/**` is
   the source of truth; notes are the rolling log in front of it.
6. **Say so when something changes the frontend's work.** Write the `B-nnn`
   item in `docs/handoff/to-frontend.md` as *Cross-Repo Communication* above
   describes, and name it in the conversation as well.
7. **Update `docs/STATUS.md` at the end of every slice**: mark the step, adjust
   the test counts, keep the open decisions current. It is the only place the
   frontend can read this repo's state — `CLAUDE.md` is not synced.

## Current Stage — and How to Resume

**Stage 2 — job-specific generation.** Stage 0 and Stage 1 are closed.

A session that opens with "let's continue with Stage 2" starts here, in this
order. Nothing below is summarised in this file: it is not synced to the
frontend, and a second copy of the state would drift from the one that is.

1. **`docs/STATUS.md`** — which step both repos are on, the open decisions,
   and the next cross-repo sync point.
2. **`docs/handoff/to-backend.md`** — open `F-nnn` items from the frontend.
   **Handle these before starting new work.**
3. **`docs/notes/current.md`** — what the previous stage handed over: the
   deliberate gaps that must not be "fixed" without asking, the findings worth
   acting on, and the per-stage carry-overs. Clear the carry-overs the step
   depends on before writing code that assumes them away.
4. **The step's plan** — `docs/INDEX.md` routes it. Stage 2 is
   `spec/14-build-guide.md` § XI-A.5, steps 2.1-2.7; the reasoning behind the
   stage is `spec/13-development.md` § 55. Search the file, read the range.

Then work the step, and close it the way *Recording Deviations*, *Cross-Repo
Communication* and *How We Ship* above describe: a record in
`docs/notes/current.md`, a `B-nnn` item if the frontend must act, `STATUS.md`
marked, all in the same PR as the code.

**When a stage closes:** move `docs/notes/current.md` to
`docs/notes/archive/stage-<n>.md`, open a fresh one carrying only what the
next stage still needs, and write the permanent decisions into the
`docs/spec/` files they belong to. `scripts/check-doc-sizes.sh` says when a
rolling file has outgrown its limit.
