# AtomCV Backend — Working Context

## What This Project Is

AtomCV lets a user build a structured "Master Profile" once, then generate
job-specific, ATS-optimized resumes and cover letters in seconds — with a
**mathematically guaranteed page limit** and **structural protection against
fabricated content**. The core insight: a person's professional history is not
a CV file, it is a **structured dataset**, and a CV is a transient *view*
rendered from it.

This repository contains **only the backend**. The frontend lives in
`atomcv-frontend` (Next.js). Never add frontend code here.

### Documentation Access — Manual Only

Do NOT read anything under `docs/` — `INDEX.md`, `STATUS.md`, `spec/**`,
`notes/**`, `handoff/**` — at the start of a session, at the start of a task,
or proactively while working, **unless the user's current message explicitly
asks you to.**

Explicit means one of:
- "check the spec for X" / "read INDEX.md" / "what's in STATUS.md"
- "check if there's anything from backend/frontend" (→ read the handoff file only)
- "update the notes" / "record this as a deviation"
- the user names a section by number ("per Bölüm 20")

If you think consulting a doc would help and the user hasn't asked for it,
**ask in one sentence instead of reading it**: "İlgili kararı `spec/05-...`'de
kontrol edeyim mi?" Wait for yes.

If you're missing a fact you'd normally get from a doc, ask the user for the
fact directly. Do not read a 300-line file to answer a one-line question.

**Never write to `docs/notes/**` or `docs/STATUS.md` unless the user
explicitly asks you to record something.** Silently updating these after
finishing a task is exactly the behavior being removed here.

## Propose, Don't Run

For the operations below, **never call Bash yourself.** Print the exact
command(s) in a fenced code block, say one line about what it does, and stop.
I will run it myself.

- `git push`, `git merge`, `git rebase`, anything touching a remote or moving
  `main`
- `gh pr create`, `gh pr merge`, `gh pr review`
- `scripts/sync-spec.sh`, `scripts/sync-handoff.sh`, or any script under
  `scripts/` whose job is repo-to-repo sync
- Anything under `docker-compose.prod.yml` or touching the deploy pipeline
- Database migrations against anything other than the local dev database
- Any command that deletes data (`git clean -fdx`, `docker volume rm`, etc.)

This is enforced at the tool-permission layer too (see `.claude/settings.json`
and the PreToolUse hook) — if a call is blocked, don't retry it. Print the
command and move on.

You may run freely, without asking: local build, test, lint, typecheck, and
any `gradlew`/`npm` script that only touches this repo's own working tree.


### Read on demand — never in full

`docs/spec/**` is the full specification in 18 files, ~8,500 lines. **Never read
one end to end.** Route with `docs/INDEX.md`, then `rg -n "<term>"
docs/spec/<file>.md` and read only the matching range — the right file is
200-1,100 lines, so reading it all costs 15-40x the tokens and buries the part
you needed. **Never routinely:** `docs/notes/archive/**` (closed stages) and
`docs/handoff/resolved/**` (settled cross-repo items).

### Ownership

| Path | Owner | Synced |
|---|---|---|
| `docs/spec/**`, `docs/INDEX.md` | **this repo** | → frontend via `scripts/sync-spec.sh` |
| `docs/STATUS.md` | shared | both ways |
| `docs/handoff/**` | shared | both ways — the real channel |
| `docs/notes/**` | this repo | never synced |

## Recording Deviations

When implementation departs from the spec, record it in `docs/notes/current.md`
as **Sapma** (spec says X, we do Y — with the reason), **Ekleme** (spec was
silent, we decided) or **Düzeltme** (spec is wrong, here is the correct
statement).

- `notes/current.md` **must stay under 200 lines.** When a stage closes, move it
  to `notes/archive/stage-<n>.md` and start empty.
- **A permanent deviation goes into `docs/spec/` and is deleted from notes.**
  Notes are a rolling log, not a second specification.
- If it affects the frontend, add a `B-nnn` item too.

## Cross-Repo Communication

`docs/handoff/to-frontend.md` is the channel. Each item:

```markdown
### B-nnn · Short title
**Since:** commit <sha> · Step <n> · **Spec:** spec/<file>.md § <section>
**Action:** what the frontend must do
```

IDs are never reused. Items move to `## ACK` when the other side confirms and
to `handoff/resolved/` from there — **so an unACKed backlog cannot be archived,
and a file over its limit for that reason is a coordination problem rather than
a filing one.** The OpenAPI schema is authoritative for API *shape*; this file
carries why it changed and what to do.

## Where the Standing Answers Live

Specified, not summarised here — a second copy would drift.

The eight design principles are `spec/01-foundations.md` § 4; technology
choices `spec/02-tech-stack.md`; module boundaries `spec/03-architecture.md`
§ 10; test strategy `spec/12-quality.md` § 51. **Anything else:
`docs/INDEX.md` routes it.**

Java 21, Spring Boot 3.x, PostgreSQL 17 + pgvector, Flyway, Redis, XeLaTeX in an
isolated container, BGE-M3 self-hosted. **No Lombok** — records for value
objects, plain constructors elsewhere.

## Absolute Rules — Never Violate

1. **`spring.jpa.hibernate.ddl-auto` is always `validate`.** Never `update`,
   never `create`. Schema is owned solely by Flyway.
2. **Never modify an applied Flyway migration.** Write a new one.
3. **All data access goes through a scoped repository.** `UserScopedRepository`
   for tables with `user_id`, `ProfileScopedRepository` for those with only
   `profile_id`; a `ProfileRef` is producible only by comparing the acting user
   against the profile's owner. Never call a raw `JpaRepository` from a
   controller or service handling user data. This is the IDOR defense, enforced
   by ArchUnit per module.
4. **Never log user content** — no `RichContent`, atom text, job description
   or email body. Log statistics instead (`ContentShape`, or a stage's own).
5. **Never put secrets in code.** Environment variables only.
6. **The rendering module must never depend on the llm module.** Rendering is
   deterministic by design.
7. **Never call `String.toLowerCase()` / `toUpperCase()` without a locale**
   for identity or matching operations — use `Locale.ROOT`. Turkish locale
   turns "SQL" into "sqı" and silently breaks skill matching.
8. **LaTeX compilation always uses `-no-shell-escape`**, in the isolated
   container.
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

Spring profiles: `local,local-fake` (daily work, no real LLM calls),
`local,local-record` (real calls saved as fixtures), `local,local-real`
(real calls, nothing saved — prompt work), `prod`.

### What this machine needs you to know

True here and nowhere in the architecture documents; each cost a debugging round.

- **Run `make` from Git Bash** — the Makefile refuses `cmd.exe` and PowerShell,
  and its recipes call `sh ./gradlew` because GNU Make runs a
  metacharacter-free line straight through `CreateProcess` and `./gradlew` is
  not a Windows executable. **PowerShell's `curl` is `Invoke-WebRequest`** and
  takes none of curl's flags, so every recipe in `notes/manual-test-*.md` is
  Git Bash too; `jq` is not installed here, `python` is.
- **`scripts/dev-signin.sh` and `scripts/dev-record.sh`** drive the sign-in and
  fixture-recording tours end to end, so neither has to be typed by hand.
- **The Makefile includes and `export`s `.env`, so Spring sees it too** — not
  only compose. Without the include a changed `POSTGRES_PASSWORD` breaks
  `make dev` with what looks like a code bug; *with* it, a production key left
  in `.env` changes local behaviour silently. Two did: `RESEND_API_KEY` sent
  sign-in mail to the internet instead of Mailpit, and `TURNSTILE_SECRET_KEY`
  made every `POST /auth/magic-link` a 403. `application-local.yml` now reads
  both from `LOCAL_*` names; **add any new production secret there the same
  way.**
- **`native.encoding` is `Cp1254` here and UTF-8 on the runner.** The source
  encoding is pinned in `build.gradle.kts`; do not remove it.
- **`Set.copyOf` / `Map.copyOf` iterate in an order salted per JVM run** — three
  runs of one three-element set gave three orders. Order reaching a JSON column,
  a response or an assertion needs `Collections.unmodifiable*` over a `Linked*`.
  Passed here, failed on the runner; reads as a flake, is not one.
- **`gradlew` must stay mode 100755**, or every Linux runner fails. Directly:
  `sh ./gradlew test` (fast, no Docker), `sh ./gradlew integrationTest` (needs
  Docker Desktop), `--tests '*SomeTest'` to narrow.
- **`make dev-full` rebuilds the LaTeX image on purpose (`--build`)** and runs
  only the containers, not the backend. Compose reuses the last image
  otherwise, and a stale one answers without `X-Page-Count`.
- **`gradlew latexTest` compiles through a real LaTeX image**, is excluded from
  `integrationTest` (minutes), and is the only lane with a real compiler and a
  real profile round trip — it caught three bugs the others could not. Run it
  when anything it exercises changes; `latex.yml` does the same on those paths.
- A `pre-commit` gitleaks hook runs on every commit; a commit that printed
  nothing about secrets did not run it.
- **`spotlessCheck` fails locally on CRLF where CI passes.** The editor tool
  and python's text mode both write CRLF here, and git normalises on commit —
  so the runner sees LF and the local check sees CRLF. **Run `sh ./gradlew
  spotlessApply` after writing a new file.**
- **The tests run on a JDK and the product ships on a JRE, and that gap hides
  faults.** `RandomGenerator.getDefault()` needs the `jdk.random` module, which
  is not in `java.se` and is absent from every JRE image: the application
  started fine locally and died on the first container built from it. Prefer
  `java.base`. More generally, **`docker build` plus one `docker run` is the
  only thing that sees this class of fault** — worth doing when a dependency or
  a base image moves.
- **`@SpringBootTest(properties = …)` on a subclass replaces the parent's
  attributes, it does not add to them.** Declaring `properties` in an IT that
  extends `AbstractIntegrationTest` silently drops the worker, anomaly and
  retention switches; the worker then claims rows another class asserts on, and
  that other class is where the failure appears. Re-declare all three by hand.
- **A shell heredoc here halves backslashes**, quoted delimiter or not, so a
  Java regex written with four arrives with two and `"\\s+"` arrives as
  `"\s+"`. **Write any file containing a backslash with the editor tool, not
  `cat > X <<'EOF'`** — a heredoc'd Python script carrying them included.

## Testing Requirements

`spec/12-quality.md` § 51 lists what to write, § 51.2 names the four tests worth
the most, and **§ 51.7 holds the four rules about testing itself** — a guard is
not known to work until it has been seen to fail, a component the whole suite
switches off has unverified wiring, report counts rather than "green", and
nothing built on `AbstractIntegrationTest`'s `MockMvc` proves anything about
CSRF. Read § 51.7 before writing a guard.

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
   only runs when a path it exercises changed, and **Deploy**, which runs on
   `main` and not on a PR at all. On approval:
   `gh pr merge <n> --rebase --delete-branch`, then `git checkout main` and
   `git reset --hard origin/main`. History stays linear.
5. **Deviations go into `docs/notes/current.md` in the same PR as the code**,
   and anything the frontend must act on becomes a `B-nnn` item in
   `docs/handoff/to-frontend.md`, said out loud in the conversation too. Both
   reach the other repository through `scripts/sync-handoff.sh push` —
   deliberately, not automatically.

## Code Style

- Code, comments, commit messages, and identifiers: **English**; conversation
  with the developer: **Turkish**
- Prefer records for value objects, sealed interfaces for closed hierarchies
- Prefer `Result<T>` over exceptions for expected failure paths

## How We Work Together

1. **Apply the documented decisions as written.** If you disagree, or find
   something missing or contradictory in the documents, raise it *before*
   implementing — never silently deviate.
2. **Work in small steps.** State what you are about to do, wait for
   approval, then do it. Do not create twenty files in one turn.
3. **Ask when the documents are ambiguous.** A wrong assumption baked into an
   early layer is expensive to remove.
4. **Update this file** on a decision future sessions need to know.
5. **Record deviations, additions and corrections in `docs/notes/current.md`**,
   in the same commit as the code they describe — see *Recording Deviations*
   above for the three record types and the promotion rule. `docs/spec/**` is
   the source of truth; notes are the rolling log in front of it.
6. **Say so when something changes the frontend's work.** Write the `B-nnn`
   item as *Cross-Repo Communication* describes, and name it in conversation.
7. **Update `docs/STATUS.md` at the end of every slice**: mark the step, adjust
   the test counts, keep the open decisions current. It is the only place the
   frontend can read this repo's state — `CLAUDE.md` is not synced.

## Current Stage — and How to Resume

**Stages 0-3 are closed as built, and were audited end to end on 2026-08-28.**
The audit's record — every gap found, thirteen decisions with their reasons,
and what is left for the developer — is `docs/notes/kapanis-denetimi.md`. All
eight slices are down; **no code item is outstanding**, and what remains is the
developer's own list in that file (price table, VPS, restore test).

A session resuming reads these, in order. None of it is summarised here; a
second copy would drift from the real one.

1. **`docs/STATUS.md`** — where both repos are, the open decisions, the next
   sync point.
2. **`docs/handoff/to-backend.md`** — open `F-nnn` items. **Handle first.**
3. **`docs/notes/current.md`** — deliberate gaps that must not be "fixed"
   without asking, and the carry-overs.
4. **The step's plan** — `docs/INDEX.md` routes it; the build guide is
   `spec/14-build-guide.md`, its reasoning `spec/13-development.md` § 55. Search
   the file, read the range. **A build-guide step is not a slice** — see *How We
   Ship* — so split it before starting, not halfway through.

Close a step the way *Recording Deviations*, *Cross-Repo Communication* and
*How We Ship* describe: a record in `docs/notes/current.md`, a `B-nnn` item if
the frontend must act, `STATUS.md` marked, all in the PR with the code.

**When a stage closes** (or a rolling file outgrows its limit — see
`scripts/check-doc-sizes.sh`): move the closed step's records to
`docs/notes/archive/`, keep the live indexes, and write the permanent decisions
into `docs/spec/`.
