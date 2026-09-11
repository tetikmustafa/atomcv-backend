# Contributing

Thank you for looking. This is a personal project maintained by one developer,
so the honest expectations first: issues and pull requests are welcome, replies
may take days, and a change that does not fit the design is more likely to be
declined than reshaped for you. Nothing here is a service level agreement.

## Before you write code

**Open an issue for anything larger than a fix.** The specification in
[`docs/spec/`](docs/spec/) records decisions and the reasons behind them, and a
pull request that contradicts one is not a bug report — it is a disagreement
worth having in words first. Say what you want to change and why; if the
specification is wrong, that is a valid answer and the section gets corrected.

**Small fixes need no ceremony.** A typo, a broken link, a wrong constant, a
test that does not test what it claims: open the pull request.

## Running it

```bash
cp .env.example .env      # then set POSTGRES_PASSWORD
make dev                  # postgres, redis, mailpit, then the backend
```

No API keys are needed. `make dev` runs with a fake LLM provider that replays
recorded fixtures, so the whole pipeline works offline and costs nothing. Only
`make record` spends money, and it says so.

On Windows, run `make` **from Git Bash** — under PowerShell or cmd the recipes
cannot find a POSIX shell, and the Makefile stops with an explanatory error.

## The three test lanes

| Command | What it runs | Needs |
|---|---|---|
| `make test` | Unit and architecture tests | nothing |
| `make test-int` | Integration tests | Docker |
| `./gradlew latexTest` | The real TeX compiler | Docker, and minutes |

`make test` is the one to run constantly. The LaTeX lane is slow and is the
only place a real compiler and a real profile meet; CI runs it when a path it
exercises changes.

## What a change is expected to carry

**A test that has been seen to fail.** This is the rule the project is most
serious about: write the assertion, watch it fail against the unfixed code,
then fix it. A guard nobody has seen fail is a guard nobody knows works, and
this repository has found several that did not — a test asserting inside an
`if` that never ran, a measurement compared against itself, a budget file the
build could not see and therefore never re-read.

**A reason, in the commit message.** Say why, not what; the diff already says
what. The reason is the part that survives, and the part a reader six months
later actually needs.

**No new dependency without a reason that survives contact with the
alternative.** The stack is deliberately small.

## House rules that will fail your build

- **`ddl-auto` is always `validate`.** The schema belongs to Flyway. Never
  modify a migration that has been applied; write a new one.
- **All data access goes through a scoped repository.** `UserScopedRepository`
  or `ProfileScopedRepository`, never a raw `JpaRepository` from a controller
  or a service handling user data. This is the multi-tenant isolation defence
  and ArchUnit enforces it.
- **Never log user content.** No profile text, no job description, no email
  body. Log counts and shapes instead.
- **The rendering module may never depend on the LLM module.** Rendering is
  deterministic by design, and ArchUnit checks that it stays that way.
- **No `toLowerCase()` without a locale.** The Turkish locale turns `SQL` into
  `sqı` and breaks skill matching in a way that is very hard to see.
- **The page budget is measured in points, never lines.** Rounding to whole
  lines accumulates error across sixteen bullets into a second page.
- **Secrets come from the environment.** Never from code, never from a test
  fixture.

Formatting is handled by Spotless: run `./gradlew spotlessApply` before you
commit. On Windows it can fail locally on line endings where CI passes, which
is why it is a command rather than a habit.

## Commits and pull requests

Conventional Commits (`feat(scope):`, `fix(scope):`, `test:`, `docs:`). One
logical change per commit, split by meaning rather than by file. Keep the
branch rebased on `main`; the history is linear, and a branch carrying a merge
commit cannot be rebase-merged.

A pull request runs format, build, integration, CodeQL, misconfiguration and
secret checks — plus the LaTeX lane when it touches rendering. All of them have
to pass.

## Security

Do not open a public issue for a vulnerability. See [SECURITY.md](SECURITY.md).

## Licence

By contributing you agree that your contribution is licensed under the
[MIT Licence](LICENSE), the same terms as the rest of the project.
