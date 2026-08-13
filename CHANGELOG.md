# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Stage 0 — skeleton. An empty but working, tested and deployable application.

### Added

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
