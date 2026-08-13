# Security Policy

## Reporting a vulnerability

Please report security issues privately to **tetikmustafa03@gmail.com**.
Do not open a public issue for a vulnerability.

Useful things to include: what the issue is, how to reproduce it, and what an
attacker could reach with it. A proof of concept helps but is not required.

This is a personal project maintained by one developer, offered free of charge
with no service level agreement. Expect an acknowledgement within a few days
and a fix on a best-effort basis. There is no bug bounty.

Please do not test against other people's accounts or data. Run against a
local instance — `make dev` gives you a complete one.

## Supported versions

Only the currently deployed version is supported. There are no maintenance
branches or backports.

## What matters most here

Two classes of issue are treated as the highest severity:

- **Cross-tenant data access.** One user reaching another user's profile,
  generations or application history. Defended structurally: every query goes
  through a user-scoped repository, and the denormalized `profile_id` on
  content tables is held to the parent row by composite foreign keys.
- **Sandbox escape from LaTeX compilation.** Compilation is user-influenced
  input reaching a document processor, so it runs in an isolated container
  with no network, no shell escape and resource limits.

Also of interest: prompt injection that changes system behaviour rather than
just producing an odd answer, secrets exposed in logs or telemetry, and
authentication or session handling flaws.

## Out of scope

- Findings from automated scanners with no demonstrated impact
- Missing hardening headers with no exploitable consequence
- Denial of service through sheer volume; quotas and rate limits are known
  and deliberate trade-offs
- Social engineering
