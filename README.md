# AtomCV — Backend

Build a structured professional profile once, then generate a job-specific,
ATS-optimized resume in seconds — with a **mathematically guaranteed page
limit** and **structural protection against fabricated content**.

The idea underneath: a person's professional history is not a CV file, it is a
structured dataset. A CV is a transient *view* rendered from that data.

This repository holds the backend only. The frontend lives in
[`atomcv-frontend`](https://github.com/tetikmustafa/atomcv-frontend) (Next.js).

> **Status: Stage 0 — skeleton.** The project structure, database baseline,
> local environment and CI pipeline are in place. There is no working
> generation pipeline yet. See [CHANGELOG.md](CHANGELOG.md).

## How it works

Generation runs as a pipeline where each phase is handled by whichever tool
actually suits the problem:

| Phase | Handled by | What it does |
|---|---|---|
| A — Job analysis | LLM | Natural language understanding |
| B — Relevance scoring | Code | Deterministic, repeatable, free |
| C — Selection | Code | Bin packing under a measured page budget |
| D — Rewriting | LLM | Narrow, validated, per atom |
| E — Rendering | Code | LaTeX, HTML, DOCX |
| F — Verification | Code | Page count, ATS readability |

Scoring, selection and rendering never involve an LLM. The page limit is a
mathematical constraint, not a request in a prompt: every unit of content is
measured in points by TeX itself, and selection maximizes relevance within
that budget.

## Tech stack

Java 21 · Spring Boot 3.5 · PostgreSQL 17 + pgvector · Flyway · Redis ·
XeLaTeX in an isolated container · BGE-M3 embeddings · Docker Compose ·
Testcontainers · ArchUnit

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 |
| Docker | with Compose v2 |
| GNU Make | any |

On Windows, run `make` **from Git Bash**. Under PowerShell or cmd, Make cannot
find a POSIX shell and every recipe fails; the Makefile stops with an
explanatory error rather than a confusing one.

## Getting started

```bash
cp .env.example .env      # then set POSTGRES_PASSWORD
make dev                  # starts postgres, redis, mailpit, then the backend
```

Flyway applies the schema on startup. Verify:

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP"}
```

Mailpit's inbox is at <http://localhost:8025>.

## Commands

| Command | Does |
|---|---|
| `make dev` | Core services plus the backend, fake LLM provider |
| `make dev-full` | Also the heavier containers (LaTeX, embeddings) |
| `make db-reset` | Drop the database volume; local only |
| `make record` | Real LLM calls, responses saved as fixtures |
| `make test` | Unit and architecture tests |
| `make test-int` | Integration tests (Testcontainers, needs Docker) |

Spring profiles: `local,local-fake` for daily work (no LLM cost),
`local,local-record` to capture fixtures, `local,local-real` for prompt work,
`prod` in production.

## Layout

```
src/main/java/com/mustafatetik/atomcv/
├── identity/     auth, session, account
├── profile/      Master Profile: Section > Entry > Atom > AtomVariant
├── ingestion/    CV upload, extraction, structuring, GitHub import
├── generation/   pipeline phases A-G, scoring, selection, validation
├── rendering/    LaTeX/HTML/DOCX renderers, measurement, templates
├── llm/          provider gateway, prompt registry, telemetry
├── embedding/    embedding provider abstraction
├── compilation/  LaTeX compiler client
├── jobs/         queue, workers, SSE
├── tracking/     application tracking
├── billing/      quota, cost tracking, anomaly detection
├── email/        transactional email
└── shared/       user-scoped repository base, errors, config
```

Modules talk only through public interfaces, with no cycles and a `shared/`
that depends on nothing. ArchUnit enforces this, along with the rule that
rendering may never reach for an LLM.

## Architecture documents

The full specification lives in [`docs/`](docs/) and is written in Turkish.
`teknik-mimari-dokumani.md` covers every technical decision, the schema and
the algorithms; `urun-konsept-dokumani-v2.md` covers the product concept and
user journeys. [CLAUDE.md](CLAUDE.md) maps tasks to the sections worth reading.

## Security

Please report vulnerabilities privately — see [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE). This is a personal project offered free of charge with no
service level agreement.
