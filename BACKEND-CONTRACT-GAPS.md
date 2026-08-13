# Backend Contract Gaps

Things the frontend needs decided on the backend side. Found while building
Stage 0 against `docs/`.

Each item states what is missing, why the frontend is blocked or at risk of
guessing, where in the specification it comes from, and a **proposal** — a
concrete shape the backend can accept, adjust, or reject. The proposals are
starting points, not decisions.

Nothing here is implemented on a guess. Where the frontend had to proceed, it
left a marked seam and recorded the assumption.

Status legend:

- **Blocking** — frontend work stops or would have to guess a wire format.
- **Needed soon** — required before the corresponding screen is built.
- **Clarification** — likely already decided, just not written down.

---

## 1. CSRF mechanism is named but never defined

**Status:** Blocking for Aşama 3 (auth). Not blocking today.

**Where:** Bölüm 40.1 (`CSRF | Yok | Token gerekir`), XI-B.3
(`client.ts # fetch wrapper + credentials + CSRF`), EK C.1 (`□ CSRF koruması
aktif`).

**Missing:** The token's name, the header it travels in, how the client
obtains it, its lifetime, and which methods require it. Three places in the
spec require CSRF protection; none describes it.

**Why the frontend cares:** `client.ts` is the single place every request
passes through. Inventing a scheme here means the two repositories can
silently disagree — requests succeed in mocks and fail in production, or
worse, the header is ignored and nobody notices the protection never worked.

**Current state:** `src/lib/api/client.ts` carries a `TODO(csrf)` comment at
the exact insertion point. No scheme was invented.

**Note:** The session cookie is `SameSite=Strict` (Bölüm 40.1), which already
closes the cross-site request vector. A CSRF token is defence in depth here,
not the primary control — so this can land with auth rather than before it.

**Proposal:** Spring Security's double-submit default, since it is the path of
least resistance on the backend:

- Server sets a readable (non-HttpOnly) `XSRF-TOKEN` cookie on session
  creation.
- Client echoes it in an `X-XSRF-TOKEN` header on every unsafe method
  (POST/PUT/PATCH/DELETE).
- Server rejects a mismatch with `403` and `code: "CSRF_TOKEN_INVALID"`.

If a different scheme is chosen, the only thing the frontend needs is: cookie
name, header name, and which methods it applies to.

---

## 2. No endpoint converts an anonymous profile into an account profile

**Status:** Blocking for the anonymous flow.

**Where:** Senaryo 2 (Bölüm 14) requires it explicitly:

> Hesap açar → geçici profil kalıcıya dönüşür, ölçüm ve çıkarım
> **tekrarlanmaz**.

The resource map (Bölüm 35.2) has no route that does this.

**Missing:** The whole operation. This is the conversion moment of the product
funnel — the user has just seen value, downloaded a CV, and is being asked to
sign up. If the profile has to be rebuilt after sign-up, the promise in
Senaryo 2 is broken and the most expensive work (LLM extraction, XeLaTeX
render measurement) is paid for twice.

**Why the frontend cares:** The sign-up flow branches on whether an ephemeral
profile exists. Without an endpoint there is no flow to build.

**Proposal:**

```
POST /api/v1/profile/claim
```

- Requires an authenticated session and an ephemeral profile in the same
  browser session.
- Moves the ephemeral profile to `PERSISTENT` scope, preserving atoms,
  variants, embeddings and measured render costs.
- `200` with the persisted profile.
- `404` + `code: "NO_ANONYMOUS_PROFILE"` when the ephemeral profile has
  already expired (see item 8) — the UI then offers a fresh start rather than
  a spinner.
- `409` + `code: "PROFILE_ALREADY_EXISTS"` when the account already has one,
  with `resolutions` offering replace / keep / merge.

The last case is a real product question, not just a technical one: what
should happen when a returning user tries an anonymous session and then logs
into an account that already has a Master Profile?

---

## 3. `resolutions[].action` has no closed vocabulary

**Status:** Needed soon (first error screen).

**Where:** Bölüm 35.4 shows `increase_page_limit`, `review_pins`,
`keep_top_pinned`. Bölüm 35.5 adds `sign_up`. Bölüm 11.5 describes two more
buttons in prose — "İlanın tam metnini yapıştır" and "Genel CV olarak devam
et" — without naming their actions. Bölüm 11.8 implies another for the
scanned-PDF case.

**Missing:** The complete list of `action` values.

**Why the frontend cares:** CLAUDE.md rule 7 forbids per-error UI: buttons are
generated from the `resolutions` array. Each action needs a translation key
and a client behaviour. An unnamed action cannot get either.

**Current state:** `ResolutionAction` in `src/types/domain.ts` is an open union
— known values are typed, unknown ones still render as a button with a
fallback label rather than crashing the panel. That keeps P4 intact but is a
safety net, not a substitute for the list.

**Proposal:** Publish the closed set in the OpenAPI schema as an enum. Adding
the missing ones:

| action                   | Meaning                               | Client behaviour                      |
| ------------------------ | ------------------------------------- | ------------------------------------- |
| `increase_page_limit`    | Raise `maxPages` to `params.maxPages` | Re-submit with new option             |
| `review_pins`            | Open the pinned-content review        | Navigate to profile, filtered to pins |
| `keep_top_pinned`        | Keep the top `params.keep` pins       | Re-submit with the narrowed set       |
| `sign_up`                | Feature requires an account           | Navigate to sign-up, preserving state |
| `paste_full_posting`     | Job text was too thin                 | Focus the job description field       |
| `continue_as_general_cv` | Proceed without a posting             | Re-submit with empty `jobDescription` |
| `switch_to_manual_form`  | Extraction failed                     | Navigate to the manual profile form   |
| `retry`                  | Transient failure                     | Re-submit unchanged                   |

**Related question:** should the frontend ever add its own resolutions? For
example a "Cancel" that only closes the panel. Current assumption: no — the
server owns the list, the client renders it and may add a plain dismiss
control outside the resolution row.

---

## 4. Error `code` list is partial

**Status:** Needed soon (message catalogue).

**Where:** Bölüm 35.5 enumerates ten pipeline errors. Bölüm 31.10 lists six
ingestion failure states as _behaviours_ with no codes. Auth, rate limiting
and the anonymous-mode failures have no codes at all.

**Missing:** Codes for everything outside the pipeline.

**Why the frontend cares:** Every code needs an `errors.{CODE}` entry in both
`en.json` and `tr.json`, with ICU placeholders matching `params`. A code that
arrives without a catalogue entry renders as a raw key to the user. The
catalogue cannot be written from prose descriptions alone — it needs the code
and the exact `params` each one carries.

**Proposal:** Codes for the ingestion cases in Bölüm 31.10:

| Situation                          | Proposed code            | `params`             |
| ---------------------------------- | ------------------------ | -------------------- |
| Scanned PDF, no text layer         | `PDF_NOT_TEXT_BASED`     | —                    |
| Extraction produced zero atoms     | `EXTRACTION_EMPTY`       | —                    |
| Encrypted PDF                      | `PDF_ENCRYPTED`          | —                    |
| Language undetected                | `LANGUAGE_UNDETECTED`    | `detectedCandidates` |
| Extraction timed out after retries | `EXTRACTION_TIMEOUT`     | —                    |
| Daily profile quota exhausted      | `PROFILE_QUOTA_EXCEEDED` | `limit`, `resetsAt`  |

And for anonymous mode: `ANONYMOUS_SESSION_EXPIRED`, `ATOM_LIMIT_EXCEEDED`
(`limit`, `current`).

**What the frontend needs per code, regardless of naming:** the exact `params`
keys and their types. `"Sabitlediğin içerik 2.3 sayfa tutuyor, sınırın 1
sayfa"` needs `pinnedPages: number` and `maxPages: number`; the ICU message
cannot be written without knowing they exist and are numbers.

---

## 5. The example error body contradicts the rule above it

**Status:** Clarification.

**Where:** Bölüm 35.4. The rule states:

> **Sunucu çeviri anahtarı gönderir, metin değil.**

The example immediately above it contains:

```json
"title": "Sabitlenen içerik sayfa sınırını aşıyor"
```

That is Turkish prose in the response body.

**Why the frontend cares:** If `title` is ever meant for display, then the
server needs the user's UI language and the rule is wrong. If it is not, the
example is misleading and someone will eventually render it.

**Current state:** The frontend treats `title` as developer-facing and never
displays it. `ApiError` uses it only as the JS `Error.message` for logs.
CLAUDE.md rule 8 states this explicitly.

**Proposal:** Keep `title` as a fixed English developer string (RFC 7807 says
it should not change between occurrences), and update the example so it does
not model the opposite. If localized text is genuinely wanted, that is a
different decision and requires `Accept-Language` handling throughout.

---

## 6. ETag emission is implied but not specified

**Status:** Needed soon (profile editor).

**Where:** Bölüm 35.6 shows `If-Match: "v7"` on a PATCH and says a mismatch
returns 412. Bölüm 13.1 and 35.6 both say `JPA @Version → ETag`.

**Missing:**

- Which resources carry an `ETag` response header. Atoms only, or also
  profile, sections, entries, variants?
- The header's exact format. The example value `"v7"` suggests the version
  number is rendered into a quoted string, but that is inference.
- Whether it is a strong or weak validator.
- Whether collection responses (`GET /profile/atoms`) embed a per-item version
  so the client can PATCH an item without fetching it individually.

**Why the frontend cares:** `useAutosave` (XI-B.3) must send `If-Match` on
every field-level save. It can only do that if it has the current version for
the specific atom it is saving. If versions only arrive on individual GETs,
then editing a list of 200 atoms means 200 extra requests — which would
contradict the performance intent of Bölüm 37.7.

**Proposal:** Emit `ETag` on single-resource GETs _and_ include a `version`
field on every item inside collection responses, so the list response seeds
everything the editor needs. Format: `ETag: "7"` matching `version: 7`.

---

## 7. No per-atom GET, but the editor is designed around one

**Status:** Clarification, possibly a missing endpoint.

**Where:** Bölüm 37.7 shows the intended cache shape:

```typescript
const { data } = useQuery(['atom', atomId]); // granüler cache anahtarı
```

Bölüm 35.2 lists `GET /api/v1/profile/atoms` (collection) but no
`GET /api/v1/profile/atoms/{id}`.

**Question:** Is the per-atom cache entry meant to be seeded from the
collection response, with the collection as the only fetch? Or is a
single-atom endpoint intended and simply missing from the map?

**Why the frontend cares:** These produce different code. Seeding from a
collection means one query plus `setQueryData` per item, and invalidation has
to be written carefully so a single atom's mutation does not refetch all 200.
A per-atom endpoint means the straightforward `useQuery(['atom', id])` in the
spec's own example.

**Proposal:** Seed from the collection — one request instead of N, and the
editor already loads the whole profile. Add `GET /profile/atoms/{id}` only if
something needs to refresh a single atom after a background job (see the
staleness flow in Bölüm 37.5, which may well need exactly that).

---

## 8. Anonymous session mechanics are not described client-side

**Status:** Needed soon (anonymous flow).

**Where:** Bölüm 9 (Aşama 0) and Bölüm 41.3. The server-side model is defined
(`ProfileRef.scope = EPHEMERAL`, Redis, 2-hour TTL). The client-facing
mechanism is not.

**Missing:**

- How an anonymous visitor is identified across requests. Is a session cookie
  issued for anonymous users too, or is a profile id carried explicitly?
- What happens when the 2-hour TTL expires mid-session — which status and
  code? The user may be halfway through a review screen.
- Whether the TTL is refreshed on activity or is absolute from creation.

**Why the frontend cares:** The 2-hour expiry is the one case where a user can
lose work they have already invested effort in — which is exactly what P8
("kullanıcının emeğini silme") exists to prevent. The UI should warn before
expiry, not report it afterwards. That needs a known expiry time and a
distinguishable error.

**Proposal:**

- Issue the same `sid` cookie for anonymous sessions; the frontend then treats
  authentication as purely a `capabilities` question, which it already does.
- Add `anonymousExpiresAt` (ISO 8601) to the `capabilities` object from
  `GET /auth/session`, so the client can warn at, say, fifteen minutes
  remaining.
- On expiry, `401` + `code: "ANONYMOUS_SESSION_EXPIRED"` with
  `resolutions: [{ "action": "sign_up" }]`.
- Refresh the TTL on activity. An absolute two hours would cut off a user who
  is actively working, which is the opposite of the intent.

---

## 9. Long-running job contract is incomplete

**Status:** Needed soon (generation flow).

**Where:** Bölüm 35.3 (202 + job), Bölüm 30.6 (SSE events).

The event types are well specified — `phase`, `completed`, `failed` with their
payloads. What is missing is everything around them:

**a. `GET /api/v1/jobs/{id}` response shape is never given.** The endpoint is
listed but not described. The frontend needs the status values, which of them
are terminal, and whether a completed job carries the result or only a
pointer to `GET /generations/{id}`.

**b. No SSE resumption contract.** No event `id` field, no `Last-Event-ID`
handling described. A dropped connection mid-pipeline — a phone changing
networks, a laptop waking from sleep — is ordinary, and the pipeline runs long
enough (Bölüm 52.6: p95 14s, plus queue time) for it to happen.

**c. Stream lifetime.** Bölüm 30.6 sets a five-minute `SseEmitter` timeout.
What should the client do when the stream closes without a terminal event —
reopen, or fall back to polling `GET /jobs/{id}`?

**Why the frontend cares:** Without a resumption story the progress UI has
exactly one failure mode: the spinner runs forever while the job has actually
finished. That is a silent bad outcome, which P4 forbids.

**Proposal:**

- Give every SSE event an `id`, and honour `Last-Event-ID` on reconnect by
  replaying from that point or at minimum re-sending current state.
- Define `GET /jobs/{id}` as:

```json
{
  "jobId": "...",
  "status": "queued | running | completed | failed",
  "phase": "C",
  "pct": 60,
  "generationId": "...",
  "error": { "code": "...", "params": {}, "resolutions": [] }
}
```

with `generationId` present on `completed` and `error` present on `failed`.
The client can then reconcile after any disconnect with a single request,
and polling becomes a viable fallback if SSE proves unreliable behind
proxies.

---

## 10. Collection endpoints have no pagination contract

**Status:** Needed soon.

**Where:** `GET /generations`, `GET /applications`, `GET /profile/atoms` in
Bölüm 35.2. No pagination appears anywhere in the specification.

**Why the frontend cares:** Bölüm 37.7 anticipates 200 atoms and mentions
virtualization above 500. Senaryo 4 mentions a user with 47 tracked
applications. Generation history grows without bound. Whether these responses
are paginated changes the query keys, the cache shape and the list components
— it is expensive to retrofit.

**Proposal:**

- `GET /profile/atoms` — unpaginated. The editor needs the whole profile
  anyway, and the ceiling is bounded by `maxAtoms`.
- `GET /generations` and `GET /applications` — cursor pagination
  (`?cursor=&limit=`), returning `{ items, nextCursor }`. Offset pagination
  skips or repeats rows when new items are created while the user pages, which
  is likely here since both lists grow from the top.

---

## 11. Download endpoint mechanics

**Status:** Needed soon (result screen).

**Where:** `GET /api/v1/generations/{id}/download?format=pdf|docx|source`
(Bölüm 35.2). PDF retention is 14 days, indefinite if archived (Bölüm 9,
Aşama 7).

**Missing:**

- Whether the response is the file itself or a redirect to object storage.
- `Content-Disposition` — does the server name the file, or should the client?
  A user with twenty applications needs distinguishable filenames.
- What the endpoint returns once the 14-day retention has passed, and with
  which code. "Expired" and "never existed" must be distinguishable, because
  only one of them can be resolved by regenerating.

**Why the frontend cares:** The PDF preview (`react-pdf`, lazy-loaded per
CLAUDE.md rule 4) needs a fetchable source. With an HttpOnly cookie the file
must be fetched with `credentials: 'include'` and turned into a blob URL — a
plain `<iframe src>` will work only if the browser sends the cookie, which
`SameSite=Strict` makes fragile across navigations. If the endpoint redirects
to signed storage URLs instead, the preview code is quite different.

**Proposal:** Serve the bytes directly from the API with
`Content-Disposition: attachment; filename="..."` containing company and
position where known. On expiry, `410 Gone` +
`code: "GENERATION_ARTIFACT_EXPIRED"` with
`resolutions: [{ "action": "retry" }]` — the selection state is still stored,
so regenerating is cheap and the user's work is not lost.

---

## 12. Idempotency scope

**Status:** Clarification.

**Where:** Bölüm 35.3 shows `Idempotency-Key` on `POST /generations`. Bölüm
30.7 shows the lookup.

**Missing:** Which other endpoints honour it, and how long keys are retained.

**Why the frontend cares:** Double-submit protection is generic in
`client.ts`. Knowing where it applies determines where the UI can rely on the
server rather than on disabling buttons — and `POST /generations/{id}/edits`
in particular re-runs the pipeline, so a double click there is expensive.

**Proposal:** Honour it on every non-idempotent POST that costs money or
starts a job: `/generations`, `/generations/{id}/edits`,
`/generations/{id}/cover-letter/regenerate`, `/ingestion/cv`. Retain keys 24
hours.

---

## 13. Quota responses should say when the user can retry

**Status:** Needed soon.

**Where:** `QuotaExceeded → 429` (Bölüm 35.5), quota model in Bölüm 44.1.
`Retry-After` appears nowhere in the specification.

**Why the frontend cares:** Bölüm 11's tone throughout is honest and
actionable — every blocked path offers a way forward. "Daily quota exhausted"
with no reset time is the one message that offers nothing. It also determines
whether the UI can disable the generate button with a countdown or must let
the user discover the limit by failing.

**Proposal:** Send `Retry-After` on 429, and include `resetsAt` (ISO 8601) in
`params`. Also expose the counters already present in `capabilities`
(`generationsUsedToday`, `dailyGenerationQuota`) alongside a `quotaResetsAt`,
so the limit is visible before it is hit rather than only after.

---

## 14. Server-side rendering has no defined path to the API

**Status:** Clarification. Not blocking — the frontend is client-fetching for
now.

**Where:** Not covered. Bölüm 35.1 states same-domain and no BFF; Bölüm 11.1
puts both containers behind nginx.

**Missing:** Whether the frontend is expected to call the API during server
rendering at all, and if so, the internal origin to use and whether the
session cookie should be forwarded.

**Why the frontend cares:** In production the frontend container reaches the
backend over the internal Docker network, not through nginx, so the relative
`/api/v1` path has no meaning server-side. Forwarding an HttpOnly session
cookie from an incoming request to an internal fetch is doable but is a
security decision, not a detail.

**Current state:** `client.ts` is browser-only and throws a descriptive error
if called during server rendering, rather than silently constructing a wrong
URL.

**Proposal:** Keep all authenticated data fetching in the browser. Server
components render shell and static content only. If server-side fetching is
wanted later, it needs an explicit internal base URL and a deliberate
cookie-forwarding decision — worth a separate discussion rather than drifting
into it.

---

## 15. `GET /profile/export` returns two formats from one route

**Status:** Clarification.

**Where:** Bölüm 35.2: `GET /api/v1/profile/export    JSON + Markdown`. Bölüm
57.5 and 58.2 make export a sustainability guarantee, so it matters.

**Missing:** How the format is selected — query parameter, `Accept` header, or
a single archive containing both.

**Proposal:** `?format=json|markdown`, matching the download endpoint's
existing `?format=` convention. Consistency here is worth more than
elegance.

---

## 16. `/api/v1/warmup` is not in the resource map

**Status:** Clarification, low priority.

**Where:** Bölüm 52.5 calls `curl -sf localhost:8080/api/v1/warmup` after
deploy. Bölüm 35.2 does not list it.

**Question:** Confirm it is an operational endpoint that is not part of the
public API and is not reachable through nginx. If it is in the OpenAPI schema
it will end up in the generated types, which would be misleading.

---

## Summary

| #   | Item                                               | Status             |
| --- | -------------------------------------------------- | ------------------ |
| 1   | CSRF mechanism undefined                           | Blocking (Aşama 3) |
| 2   | Anonymous profile claim endpoint missing           | Blocking           |
| 3   | `resolutions[].action` vocabulary incomplete       | Needed soon        |
| 4   | Error `code` list partial                          | Needed soon        |
| 5   | `title` example contradicts the keys-not-text rule | Clarification      |
| 6   | ETag emission unspecified                          | Needed soon        |
| 7   | No per-atom GET, editor designed around one        | Clarification      |
| 8   | Anonymous session mechanics undescribed            | Needed soon        |
| 9   | Job status shape and SSE resumption missing        | Needed soon        |
| 10  | No pagination contract                             | Needed soon        |
| 11  | Download mechanics and expiry behaviour            | Needed soon        |
| 12  | Idempotency scope                                  | Clarification      |
| 13  | Quota responses lack a reset time                  | Needed soon        |
| 14  | No defined server-side path to the API             | Clarification      |
| 15  | Export format selection                            | Clarification      |
| 16  | `/warmup` not in the resource map                  | Clarification      |

Items 3, 4, 6, 9, 10 and 13 all resolve themselves once the OpenAPI schema
exists and `npm run gen:api` can run — provided the schema carries the enums
and headers rather than only the happy-path payloads. Items 1, 2 and 8 are
design decisions that a schema cannot answer.
