# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Stages 0-3 are closed and were audited end to end on 2026-08-28. Stage 4
(maturity) is most of the way through: the edit loop, three templates with
customization, application tracking, DOCX download, an LLM evaluation lane,
performance budgets and lifecycle emails have all landed. Not deployed.

Since then the specification has been read against the code six times. Each
round is recorded below under the stage it corrected rather than as a release
of its own: an audit is not a feature, it is the distance between what was
written down and what was built.

### Added — Stage 4

- The edit loop (Faz G): a sentence in the person's own words becomes a
  numbered change set against that generation's own selection, applied as Faz C
  constraints and re-run — so twenty edits keep the page guarantee. Manual
  include and exclude toggles take the same path.
- Three templates (classic, modern, compact) with Layer A and Layer B
  customization, each geometry's capacity measured once against the real
  compiler and stored under the geometry rather than under the template.
- DOCX and single-file HTML downloads, and the LaTeX source the PDF was
  compiled from. Format selection moved behind `DocumentWriter`, which is what
  finally stopped `generation` naming formats it has no business knowing.
- Application tracking, and an archive flag on a generation.
- GitHub import: suggestions from public data only, no token stored, merged
  into a matching project or offered as a new one.
- Lifecycle emails — welcome and deletion confirmation, a closed list — with
  the preference on the account and an unsubscribe link that needs no session.
- An evaluation lane for prompts that costs money and runs only when a prompt
  changes, with a suite per prompt and a coverage test that refuses a prompt
  nobody has decided about.
- Performance budgets in a file the tests read, holding a query count and a
  scaling ratio rather than milliseconds a CI machine cannot promise.

### Added — Stage 3

- Identity: OAuth with Google and GitHub, magic links on a selector/verifier
  pair verified by POST, account-enumeration resistance, three layers of rate
  limiting over a Redis sliding window, and Turnstile in front of the one layer
  a stranger could otherwise use to lock somebody out of their own account.
- Email through Resend with an SMTP fallback for local work, a suppression list
  written by a signature-verified webhook, and Mailpit in the dev compose.
- CV ingestion: a validation ladder from extension to magic bytes to size to
  extracted length, PDFBox/POI/TeX readers, LLM structuring that produces the
  English wording in the same call, code-side normalisation of skills, dates
  and runs, and a mandatory review screen fed by warnings that carry their
  position in the profile.
- Multilingual atoms: staleness marked in the same transaction as the edit,
  only what the person did not write queued for translation, and a
  language-aware Faz C that optimises against the target language's own
  measured costs.
- Anonymous mode: a profile row with an expiry and no owner, addressed through
  a `ProfileRef` only the session module can mint, quotas counted per address,
  and an upgrade that happens inside signing in because the anonymous session
  id is readable for exactly that one request.
- Faz D: alternative-wording selection with no model call at all, a three-tier
  intervention gate, per-atom parallel rewriting, five validators with zero
  tolerance for an unsupported claim, and About synthesis written only when
  selection kept an About on the page.
- Cover letters derived from the selected atoms, with a cliche filter in two
  languages, a career-span check rather than a sum of overlapping jobs, and a
  quantity reader that understands digits and written numbers alike.
- The legal layer: privacy policy and terms, cascade account deletion checked
  against `information_schema` rather than a hand-written table list, JSON and
  Markdown export, and feedback with a 48-hour support grant that records when
  it was used and can be withdrawn.

### Added — Stage 2

- The LLM gateway: a provider interface with five adapters, an env-driven
  fallback chain that distinguishes "try the next vendor" from "retry here", a
  circuit breaker so a dark provider stops costing every generation thirty
  seconds, and invocation telemetry that stores cost and outcome but never
  content.
- Faz A: preflight checks that cost nothing, a structured analysis, a
  plausibility gate in front of the expensive phases, three layers of prompt
  injection defence, and a Redis cache keyed by prompt version.
- Embeddings: a self-hosted BGE-M3 container, an interface with a degraded
  fallback, and invalidation keyed to the content hash.
- Faz B: hybrid scoring over embedding, tags, skills and keywords, an
  importance multiplier, and a bucketed tie-break that makes the same input
  produce the same ranking on every run.
- The queue: `SELECT FOR UPDATE SKIP LOCKED`, heartbeats, a zombie collector
  that does not refund an attempt, exponential backoff with jitter and a
  ceiling, SSE progress that sends the current state on connect, and
  idempotency keys.
- Quotas, usage counters, anomaly detection and a one-way kill switch.
- Faz F's fit report: countable coverage, never a percentage.

### Added — Stage 1

- Rich content run model: `Run`, `Mark`, `RichContent`, with the content hash
  taken over the plain text so that re-marking a sentence invalidates neither
  its embedding nor its measured render cost.
- `ContentMigrator` for the versioned JSONB structure, refusing content written
  by a newer build rather than reading it best-effort.
- `Section`, `Entry`, `Atom` and `AtomVariant` entities, checked against the
  Flyway baseline by schema validation and by integration tests covering the
  enum vocabulary, the JSONB shape and the skill arrays.
- User- and profile-scoped repository bases, with a `ProfileRef` that cannot be
  constructed without comparing the acting user against the profile's owner.
- The build notes in the architecture document: every deviation, addition and correction
  made while building, with the frontend-facing ones collected together.

### Added — Stage 0

- Spring Boot 3.5 application on Java 21, built with Gradle 9.7 whose
  distribution checksum is pinned and verified.
- The thirteen-module package tree of the modular monolith, each package
  documented with a `package-info`.
- Baseline database schema as a single Flyway migration: identity, profile,
  tags, template customization, generation, application tracking, queue and
  telemetry.
- Composite foreign keys binding the denormalized `profile_id` on `entries`,
  `atoms` and `atom_variants` to the parent row, so a mismatch cannot become a
  silent cross-tenant leak.
- Local environment through Docker Compose: PostgreSQL 17 with pgvector,
  Redis, Mailpit, each with a health check.
- Spring profiles for local and production, with `ddl-auto: validate`
  everywhere so the schema stays owned by Flyway alone.
- Integration tests covering the schema, the required extensions, the
  embedding dimension and each tenant integrity constraint, run against the
  same pgvector image production uses.
- ArchUnit rules for module boundaries, the user-scoped repository, renderer
  determinism, content in logs and locale-sensitive case conversion. Each rule
  was confirmed to fail against a deliberate violation.
- CI pipeline: compile, unit and architecture tests, Testcontainers
  integration tests, CodeQL, Trivy misconfiguration scan, gitleaks over full
  history, and Dependabot for Gradle and GitHub Actions.
- `make` entry points for the daily loop, and a gitleaks pre-commit hook.

### Changed — the six audits

The chapters were read against the code six times between 2026-09-15 and
2026-09-20. What each round found is in `docs/notes/`; what it changed on the
wire is in `docs/handoff/to-frontend.md` as `B-100` through `B-115`. The shape
of the findings moved every time, which is the part worth keeping:

1. **Does the name exist?** Sixteen things the chapters named and the code did
   not have — the archive endpoint, atom tags, `/warmup`, the HTML renderer,
   `format=source`, GitHub import, `/customizations`, the translation step,
   `LISTEN/NOTIFY`, replay. Three silent defects came with them, the worst an
   nginx policy that blocked Turnstile while both repositories' tests were
   green.
2. **The same question, a second pass.** Eight more, six of them in code: WAL
   archiving, a second backup provider, the restore script's anonymous rows,
   the circuit breaker, and design principle 7 — every selection's reason was
   computed and none of it was published.
3. **Design written down as done.** `ContentShape` was named by four documents
   including an absolute rule and did not exist. The phase contract of § 17 was
   never built and never recorded. Format independence sat on the wrong side of
   its own boundary.
4. **Sets rather than prose.** 1,659 names, 61 endpoints, 35 enums, 14
   migrations against the data model, 91 numeric constants. The chapter calling
   itself the complete database schema was thirteen migrations behind.
5. **Liveness — is there a wire for it?** Network calls inside transactions in
   five places; Faz A's eval suite missing entirely while CI measured a
   different prompt and reported green; four unreachable notifications; three
   budget numbers nothing held.
6. **Second authorities, and deferrals nobody was watching.** A duplicate,
   stale copy of the API contract appendix claiming to be the single source; a
   progress log frozen at Stage 1 calling itself the frontend's only address;
   four vocabulary values nothing could produce, one of which a person could
   choose and never see honoured; four ingestion refusals offering no way out
   while the action for them sat unused in the vocabulary; a counter promised
   in two chapters, computed, logged and thrown away; and a prompt sentence
   deferred to "the next version of `job_analysis`" that the next version did
   not carry.

### Notes

- `llm_invocations` keeps its rows when a user is deleted, with the user link
  set to null: aggregate cost history survives account deletion while the
  personal link does not.
- The image scan lives in the deploy workflow rather than in CI: it reads what
  the Dockerfile produced, which is where a base-image CVE actually is.
- Nothing here is deployed. The server, the restore drill and the three checks
  that need a browser against a real origin — OAuth, Turnstile, the challenge —
  are listed in `docs/vps-dagitim-plani.md` § 4.
