# AtomCV Backend — Working Context

## What This Project Is

A user builds a structured "Master Profile" once, and job-specific, ATS-ready
resumes and cover letters are generated from it — with a **mathematically
guaranteed page limit** and **structural protection against fabricated
content**. A professional history is not a CV file, it is a **structured
dataset**, and a CV is a transient *view* rendered from it.

This repository is **the backend only**; the frontend lives in
`atomcv-frontend` (Next.js). Never add frontend code here.

### Documentation Access — Manual Only

Do NOT read anything under `docs/` — `INDEX.md`, `STATUS.md`, `spec/**`,
`notes/**`, `handoff/**` — at the start of a session or a task, or proactively
while working, **unless the user's current message explicitly asks you to.**

Explicit means one of: "check the spec for X" / "read INDEX.md" / "what's in
STATUS.md"; "check if there's anything from backend/frontend" (→ the handoff
file only); "update the notes" / "record this as a deviation"; or a section
named by number ("per Bölüm 20"). "Where are we / what's left" is **not** one —
it is the moment to ask.

If a doc would help and the user has not asked, **ask in one sentence instead
of reading it** and wait. Missing a fact a doc would give you? Ask for the
fact; do not read a 300-line file to answer a one-line question.

**Never write to `docs/notes/**` or `docs/STATUS.md` unless the user explicitly
asks you to record something** — this governs, and the closing ritual in *How
We Ship* is something you offer, not something you do quietly.

## Propose, Don't Run

**Never run these, and do not retry when they are blocked** — print the command
instead: `scripts/sync-spec.sh`, `scripts/sync-handoff.sh` or any repo-to-repo
sync; `docker-compose.prod.yml` and the deploy pipeline; a migration against
anything but the local dev database; anything that deletes data
(`git clean -fdx`, `docker volume rm`). `.claude/settings.json` denies them and
the PreToolUse hook checks the command string a second time.

**`git push`, `git merge`, `git rebase` and the `gh pr` verbs prompt instead**
(`ask`). The prompt is the developer's decision, not a formality: say what the
command does and why before calling it, and **never merge without an explicit
yes in the conversation** — *How We Ship* still holds, and `main` is pushed
before a PR is opened.

**`git commit` runs freely** — but `.githooks/post-commit` copies
`docs/handoff/**` and `docs/STATUS.md` into `../atomcv-frontend` and commits
there, so a commit touching those files writes to the other repository.

Run freely, without asking: local build, test, lint, typecheck, and any
`gradlew` script that only touches this repo's own working tree.

### Read on demand — never in full

`docs/spec/**` is 18 files, ~8,500 lines. **Never read one end to end.** Route
with `docs/INDEX.md`, `rg -n "<term>" docs/spec/<file>.md`, read the matching
range: a file is 200-1,100 lines, so reading it whole costs 15-40x the tokens
and buries the part you needed. **Never routinely:** `docs/notes/archive/**`
and `docs/handoff/resolved/**` — both are closed by definition.

### Ownership

`docs/spec/**` and `docs/INDEX.md` belong to this repo and go to the frontend
through `scripts/sync-spec.sh`. `docs/STATUS.md` and `docs/handoff/**` are
shared both ways — handoff is the real channel. `docs/notes/**` is this repo's
and is never synced.

## Recording Deviations

When implementation departs from the spec, record it in `docs/notes/current.md`
as **Sapma** (spec says X, we do Y — with the reason), **Ekleme** (spec was
silent, we decided) or **Düzeltme** (spec is wrong, here is the correct one).

- **Under 200 lines.** When a stage closes, move it to
  `notes/archive/stage-<n>.md` and start empty. Making room means checking a
  record closed, not trimming by eye.
- **A permanent deviation goes into `docs/spec/` and is deleted from notes** —
  a rolling log, not a second specification. Frontend-visible: `B-nnn` too.

## Cross-Repo Communication

`docs/handoff/to-frontend.md` is the channel; an item is a `### B-nnn · title`
with **Since:** (commit, step, spec section) and **Action:** (what the frontend
must do). IDs are never reused. Items move to `## ACK` when the other side
confirms and to `handoff/resolved/` from there — **so an unACKed backlog cannot
be archived, and a file over its limit for that reason is a coordination
problem rather than a filing one.** The OpenAPI schema is authoritative for API
*shape*; this file carries why it changed and what to do.

## Where the Standing Answers Live

Specified, not summarised here — a second copy would drift. The eight design
principles are `spec/01-foundations.md` § 4; technology choices
`spec/02-tech-stack.md`; module boundaries `spec/03-architecture.md` § 10; test
strategy `spec/12-quality.md` § 51. **Anything else: `docs/INDEX.md` routes it.**

Java 21, Spring Boot 3.x, PostgreSQL 17 + pgvector, Flyway, Redis, XeLaTeX in an
isolated container, BGE-M3 self-hosted. **No Lombok** — records for value
objects, plain constructors elsewhere.

## Absolute Rules — Never Violate

1. **`spring.jpa.hibernate.ddl-auto` is always `validate`.** Never `update`,
   never `create`. Schema is owned solely by Flyway.
2. **Never modify an applied Flyway migration.** Write a new one.
3. **All data access goes through a scoped repository.** `UserScopedRepository`
   for tables with `user_id`, `ProfileScopedRepository` for those with only
   `profile_id`; a `ProfileRef` comes only from comparing the acting user
   against the profile's owner. Never a raw `JpaRepository` from a controller or
   a service handling user data — the IDOR defense, ArchUnit-enforced.
4. **Never log user content** — no `RichContent`, atom text, job description
   or email body. Log statistics instead (`ContentShape`, or a stage's own).
5. **Never put secrets in code.** Environment variables only.
6. **The rendering module must never depend on the llm module.** Rendering is
   deterministic by design.
7. **Never call `String.toLowerCase()` / `toUpperCase()` without a locale**
   for identity or matching operations — use `Locale.ROOT`. Turkish locale
   turns "SQL" into "sqı" and silently breaks skill matching.
8. **LaTeX compilation always uses `-no-shell-escape`**, in the isolated container.
9. **Never let the LLM produce LaTeX** — renderers produce it, LLMs plain text.
10. **Page budget is measured in points (pt), never in lines.** Rounding to
    whole lines accumulates error.

## Development Commands

```bash
make dev        # core services (postgres, redis, mailpit) + backend, fake LLM
make dev-full   # also starts latex + embeddings containers
make db-reset   # wipe database and re-run migrations (LOCAL ONLY)
make record     # local-record profile, to capture LLM fixtures
make test       # unit + architecture tests
make test-int   # integration tests (Testcontainers)
make golden-costs # re-measure the golden set's render costs, after a fixture changes
```

Spring profiles: `local,local-fake` (daily work, no real LLM calls),
`local,local-record` (real calls saved as fixtures), `local,local-real` (real
calls, nothing saved — prompt work), `prod`.

### What this machine needs you to know

True here and nowhere in the architecture documents; each cost a debugging round.

- **Run `make` from Git Bash** — the Makefile refuses `cmd.exe` and PowerShell,
  and its recipes call `sh ./gradlew` (GNU Make hands a metacharacter-free line
  to `CreateProcess`, and `./gradlew` is not a Windows executable).
  **PowerShell's `curl` is `Invoke-WebRequest`** and takes none of curl's flags,
  so `notes/manual-test-*.md` is Git Bash too; no `jq` here, `python` yes.
- **`scripts/dev-signin.sh` and `scripts/dev-record.sh`** drive the sign-in and
  fixture-recording tours end to end; neither has to be typed by hand.
- **The Makefile includes and `export`s `.env`, so Spring sees it too** — not
  only compose. Without it a changed `POSTGRES_PASSWORD` reads as a code bug;
  with it, a production key in `.env` changes local behaviour silently.
  `RESEND_API_KEY` sent sign-in mail to the internet instead of Mailpit and
  `TURNSTILE_SECRET_KEY` made every `POST /auth/magic-link` a 403;
  `application-local.yml` reads both from `LOCAL_*` names now, and **add any new
  production secret there the same way.**
- **`.env`'s `LLM_CHAIN_*` does *not* make `make dev` spend money** — this file
  said it did, wrongly twice over. `LLM_CHAIN_CHEAP` binds to `llm.chain.cheap`,
  the property is `atomcv.llm.chain.cheap`, so the variable only feeds the
  placeholder in the base document — which `application-local-fake.yml` outranks.
  Measured, and pinned by `LocalProfileConfigTest`. The 127-second OpenRouter
  call it cited was a `make record` run: all 141 real calls in the local database
  wrote a fixture, and writing one is what `local-record` does. **The profile is
  what spends money**, and `local-record`/`local-real` are supposed to.
- **`native.encoding` is `Cp1254` here and UTF-8 on the runner**; the source
  encoding is pinned in `build.gradle.kts`, do not remove it. The same console
  makes **`print()` of a non-ASCII string raise `UnicodeEncodeError`** — a
  script whose edits are fine still dies on its own output, so keep it ASCII.
- **`Set.copyOf` / `Map.copyOf` iterate in an order salted per JVM run** — three
  runs of one three-element set gave three orders. Order reaching a JSON column,
  a response or an assertion needs `Collections.unmodifiable*` over a `Linked*`.
  Passed here, failed on the runner; reads as a flake, is not one.
- **`gradlew` must stay mode 100755**, or every Linux runner fails. Directly:
  `sh ./gradlew test` (fast, no Docker), `sh ./gradlew integrationTest` (needs
  Docker Desktop); `--tests '*SomeTest'` narrows either.
- **`make dev-full` rebuilds the LaTeX image on purpose (`--build`)** and runs
  the containers only, not the backend: compose reuses the last image, and a
  stale one answers without `X-Page-Count`.
- **`gradlew latexTest` compiles through a real LaTeX image**, is excluded from
  `integrationTest` (minutes), and is the only lane with a real compiler and a
  real profile round trip — three bugs the others could not see. `latex.yml`
  runs it on the paths it exercises.
- A `pre-commit` gitleaks hook runs on every commit; one that printed nothing about secrets did not run it.
- **`spotlessCheck` fails locally on CRLF where CI passes** — the editor tool
  and python's text mode write CRLF, git normalises on commit, so the runner
  sees LF. **Run `sh ./gradlew spotlessApply` after writing a new file.**
- **The tests run on a JDK and the product ships on a JRE, and that gap hides
  faults.** `RandomGenerator.getDefault()` needs `jdk.random`, absent from every
  JRE image: the application started locally and died on the first container
  built from it. Prefer `java.base`, and **`docker build` plus one `docker run`
  is the only thing that sees this class of fault** — do it when a dependency
  or a base image moves.
- **`@SpringBootTest(properties = …)` on a subclass replaces the parent's
  attributes rather than adding to them.** In an IT extending
  `AbstractIntegrationTest` it silently drops the worker, anomaly and retention
  switches; the worker then claims rows another class asserts on, and that
  class is where the failure appears. Re-declare all three by hand.
- **A shell heredoc here halves backslashes**, quoted delimiter or not, so a
  Java regex written with four arrives with two and `"\\s+"` arrives as
  `"\s+"`. **Write any file containing a backslash with the editor tool, not
  `cat > X <<'EOF'`** — a heredoc'd Python script carrying them included.
  **And when the halving lands on an escape, what arrives is a control
  character**: `\resumeItem` becomes CR + `esumeItem`, `\vspace` becomes VT +
  `space`, `\addvspace` becomes BEL + `ddvspace`. In a comment the compiler
  says nothing, a diff shows nothing, and a terminal re-draws the line — six
  of these sat in committed javadoc and archived notes before anything noticed.
  Two greps find them: any control character in a file, and any line inside a
  block comment that does not start with a star (a CR that already became a
  line break leaves no control character behind, so the first grep misses it).

## Testing Requirements

`spec/12-quality.md` § 51 lists what to write and § 51.2 names the four tests
worth the most. **§ 51.7 holds the four rules about testing itself** — a guard
is not known to work until it has been seen to fail; a component the whole
suite switches off has unverified wiring; report counts, not "green"; nothing
on `AbstractIntegrationTest`'s `MockMvc` proves anything about CSRF.

## How We Ship

1. **One branch per slice**, named `feat/…`, `test/…` or `docs/…`. A slice is
   what fits in one review, not one step of the build guide.
2. **`main` is pushed before a PR is opened.** A rebase merge once rewrote
   twelve unpushed local commits; the fix was `git reset --hard origin/main`
   after verifying the trees matched.
3. **Split commits by logical unit**, not by file. Conventional Commits
   (`feat(scope):`, ...); the body says *why*, in English, and names its Bölüm.
4. **The developer decides when to merge.** Open the PR, wait for the checks
   (`gh pr checks <n> --watch`), report, and ask. There are six now — format,
   build, integration, CodeQL, misconfiguration, secrets — plus **LaTeX**, which
   only runs when a path it exercises changed. **Deploy is hand-run only** until
   there is a VPS; its `push` trigger is commented out in the file. On approval:
   `gh pr merge <n> --rebase --delete-branch`, then `git checkout main` and
   `git reset --hard origin/main`. History stays linear — and **a branch
   carrying a merge commit cannot be rebase-merged**: GitHub answers "This
   branch can't be rebased", and a `git pull` that merged is how one gets there.
5. **Deviations and `B-nnn` items ship in the PR with the code**, said out loud
   in conversation too. They reach the frontend repository through the
   `post-commit` hook (see *Propose, Don't Run*); `scripts/sync-handoff.sh push`
   is the manual route.

## Code Style

- Code, comments, commit messages, and identifiers: **English**; conversation
  with the developer: **Turkish**
- Prefer records for value objects, sealed interfaces for closed hierarchies
- Prefer `Result<T>` over exceptions for expected failure paths

## How We Work Together

1. **Apply the documented decisions as written.** Disagreement, a gap or a
   contradiction is raised *before* implementing — never a silent deviation.
2. **Work in small steps.** State what you are about to do, wait, then do it.
3. **Ask when the documents are ambiguous.** A wrong assumption baked into an
   early layer is expensive to remove.
4. **Update this file** on a decision future sessions need to know.
5. **Say so when something changes the frontend's work**, in conversation and
   as a `B-nnn` item — *Cross-Repo Communication* has the shape.
6. **A closing slice wants three things** — a record in `docs/notes/current.md`
   (*Recording Deviations*), that `B-nnn` if the frontend must act, and
   `STATUS.md` marked with the step and the test counts, the only place the
   frontend reads this repo's state. **Offer them and wait**: writing them
   unasked is what the docs rule forbids.

## Current Stage — and How to Resume

**Stages 0-3 are closed as built**, audited end to end on 2026-08-28
(`docs/notes/kapanis-denetimi.md`). Where the work stands is `docs/STATUS.md`;
a second copy here would drift from it. **When the user asks you to catch up**
— and only then, per *Documentation Access* — read these four in order:

1. **`docs/STATUS.md`** — where both repos are, the open decisions, the next
   sync point.
2. **`docs/handoff/to-backend.md`** — open `F-nnn` items. **Handle first.**
3. **`docs/notes/current.md`** — deliberate gaps that must not be "fixed"
   without asking, and the carry-overs.
4. **The step's plan** — `docs/INDEX.md` routes it; the build guide is
   `spec/14-build-guide.md`, its reasoning `spec/13-development.md` § 55.
   **A build-guide step is not a slice**, so split it before starting.

**When a stage closes** (or a rolling file outgrows its limit —
`scripts/check-doc-sizes.sh`): move the closed step's records to
`docs/notes/archive/`, keep the live indexes, and write the permanent decisions
into `docs/spec/`.
