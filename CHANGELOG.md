# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Stage 0 — skeleton: complete. Stage 1 — walking skeleton: in progress.

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
- `EK D` in the architecture document: every deviation, addition and correction
  made while building, with the frontend-facing ones collected in `EK D.5`.

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

### Notes

- `llm_invocations` keeps its rows when a user is deleted, with the user link
  set to null: aggregate cost history survives account deletion while the
  personal link does not.
- Trivy currently has nothing to scan; it starts finding misconfiguration once
  the LaTeX container arrives in Stage 1.
- `archunit.properties` disables the empty-should check while the module
  packages hold no classes. It comes out in Stage 1.
