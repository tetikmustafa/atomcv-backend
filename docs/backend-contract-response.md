# Backend Response to the Contract Gaps

Answers to `BACKEND-CONTRACT-GAPS.md`. Written at the end of Stage 0, when the
backend exposes nothing but `/actuator/health` — so none of these are
implemented yet, and the point of this document is to stop both repositories
guessing before they are.

Three kinds of answer appear here:

- **Decided** — settled now, recorded in `CLAUDE.md`, safe to build against.
- **Stage N** — the decision belongs with the work that introduces it, and
  forcing it earlier would be guessing. The stage is named so nothing is left
  open-ended.
- **Open** — a real question that still needs an answer, with what it blocks.

Where a proposal in the gaps document was accepted as written, this says so
rather than restating it.

## Verdicts

| #   | Item                          | Verdict                        |
| --- | ----------------------------- | ------------------------------ |
| 1   | CSRF mechanism                | Proposal accepted · Stage 3    |
| 2   | Anonymous profile claim       | Accepted with one change · Stage 3 |
| 3   | `resolutions[].action` vocabulary | Accepted as the starting set · Stage 1 |
| 4   | Error `code` list             | Accepted as the starting set · Stage 1 |
| 5   | `title` contradiction         | **Decided** — developer-facing |
| 6   | ETag emission                 | **Decided** — scope below      |
| 7   | Per-atom GET                  | **Decided** — seed from collection |
| 8   | Anonymous session mechanics   | **Decided** — sliding TTL      |
| 9   | Job status and SSE resumption | Proposal accepted · Stage 2    |
| 10  | Pagination                    | Proposal accepted · Stage 1–2  |
| 11  | Download mechanics            | Proposal accepted · Stage 1    |
| 12  | Idempotency scope             | Proposal accepted · Stage 2    |
| 13  | Quota reset time              | Accepted, one open question    |
| 14  | Server-side rendering path    | **Decided** — browser only     |
| 15  | Export format selection       | **Decided** — `?format=`       |
| 16  | `/warmup`                     | **Decided** — not public       |

## Decided now

### 5 — `title` is developer-facing

Fixed English, stable across occurrences, never displayed. RFC 7807 wants it
that way and Bölüm 35.4's own rule agrees; the Turkish string in that section's
example is misleading and the document should be corrected. The frontend's
current handling — `title` as `Error.message` for logs only — is right.

### 6 — ETag scope

V1 gives a `version` column to exactly six tables: `profiles`, `sections`,
`entries`, `atoms`, `atom_variants`, `applications`. **`generations` has none**,
so generation resources will not carry an ETag and will not support `If-Match`.
If the result screen needs optimistic concurrency over a generation, say so and
it becomes a schema change rather than an oversight discovered late.

For the six that do: `ETag: "7"` on single-resource GETs, and a `version` field
on every item inside collection responses, exactly as proposed. The editor
should never need N requests to learn N versions.

### 7 — Seed per-atom cache from the collection

No `GET /profile/atoms/{id}` for now. The editor loads the whole profile
anyway, and the collection response will carry `version` per item, so it has
everything a field-level PATCH needs. A single-atom endpoint gets added when
something concrete needs it — the staleness flow in Bölüm 37.5 is the likely
first caller.

### 8 — Anonymous session mechanics

- Same `sid` cookie for anonymous sessions, so authentication stays a
  `capabilities` question on the client.
- `anonymousExpiresAt` (ISO 8601) in `capabilities`.
- On expiry: `401` + `ANONYMOUS_SESSION_EXPIRED` with a `sign_up` resolution.
- **The TTL slides: it refreshes on activity.** This was argued and decided
  deliberately. Bölüm 9 reads "deleted after 2 hours", which taken literally
  would cut off someone still working through the review screen — the exact
  loss of user effort P8 exists to prevent. The rule is two hours after the
  **last activity**, and the product copy needs to say that rather than promise
  an absolute two hours.

### 14, 15, 16

Server rendering never calls the API: all authenticated fetching stays in the
browser, server components render shell and static content. The descriptive
throw in `client.ts` is the correct behaviour, not a placeholder.

Export selects format with `?format=json|markdown`, matching download.

`/api/v1/warmup` is operational only — excluded from the OpenAPI schema, not
routed through nginx, and it should not appear in generated types.

## Stage 1 — settled before the first endpoint is written

These are not deferrals. They shape the first profile endpoint, so they get
decided at the start of Stage 1 rather than discovered during it.

**3 and 4 — the action and code vocabularies.** Both proposed tables are
accepted as the starting set. They will be published as enums in the OpenAPI
schema, which is what makes them authoritative; prose in this document is not.
Two commitments beyond the naming: every code documents its exact `params` keys
and their types, and the enum ships in the schema rather than only in the happy
path payloads. On the related question — no, the frontend should not invent
resolutions. The server owns the list; a plain dismiss control outside the
resolution row is fine.

**10 — pagination.** `GET /profile/atoms` unpaginated as proposed. Cursor
pagination for `/generations` and `/applications` when those endpoints arrive in
Stage 2, with `{ items, nextCursor }`; the reasoning about offset pagination
skipping rows in top-growing lists is correct.

**11 — download.** Bytes served directly from the API with
`Content-Disposition: attachment`, filename carrying company and position where
known. `410 Gone` + `GENERATION_ARTIFACT_EXPIRED` + `retry` after the 14-day
retention. This is cheap to honour because `generations.selection_state` is a
permanent snapshot independent of `pdf_expires_at` — the PDF can always be
rebuilt from stored state, so expiry never costs the user their work.

**Enabler.** springdoc-openapi lands with the first endpoint. Six of these
sixteen items close themselves once `npm run gen:api` can run, provided the
schema carries enums and headers and not just payloads.

## Stage 2

**9 — job contract.** Both proposals accepted: an `id` on every SSE event with
`Last-Event-ID` honoured on reconnect, and `GET /jobs/{id}` in the shape given,
with `generationId` on completion and `error` on failure. Polling that endpoint
is an acceptable fallback when the stream closes without a terminal event.

**12 — idempotency.** Honoured on `/generations`, `/generations/{id}/edits`,
`/generations/{id}/cover-letter/regenerate` and `/ingestion/cv`; keys retained
24 hours.

One defect to record while it is fresh: V1 has
`CREATE UNIQUE INDEX ON jobs (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL`,
which does not dedupe anonymous requests — `user_id` is NULL there and Postgres
treats NULLs as distinct, so the same key opens a second job. It needs a
migration keyed on `COALESCE(user_id::text, anon_session_id)`. Deferred rather
than fixed immediately because it presumes the anonymous path uses the queue at
all, which is itself still open.

**13 — quota.** `Retry-After` on 429 plus `resetsAt` in `params`, and the
counters exposed in `capabilities` so the limit is visible before it is hit.

> **Open:** `usage_counters.period` is a `DATE`, so the daily counter rolls
> over at a day boundary that nothing defines. UTC or Europe/Istanbul? This has
> to be answered before `resetsAt` can be sent, and it is user-visible: a UTC
> rollover lands at 03:00 for a Turkish user.

## Stage 3

**1 — CSRF.** Spring Security's double-submit default, as proposed:
`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header, unsafe methods only, `403` +
`CSRF_TOKEN_INVALID` on mismatch. Arrives with auth. The reasoning that
`SameSite=Strict` already closes the primary vector is correct, which is why
this is not being brought forward.

**2 — profile claim.** `POST /api/v1/profile/claim` as proposed, with `200`,
`404 NO_ANONYMOUS_PROFILE` and `409 PROFILE_ALREADY_EXISTS`.

One change: the 409 offers **replace or keep only, not merge**. Merging two
Master Profiles means atom-level deduplication — the Jaro-Winkler plus
embedding work in Bölüm 7 — which is Stage 4. Offering it earlier would either
block the claim endpoint on unrelated work or ship a merge that silently
duplicates content, and P8 forbids the second. The API should not name a
resolution it cannot honour.
