# Hata Kataloğu

> **Üretilmiş dosya — elle düzenlenmez.** Kaynağı `ErrorCode`
> enum'u; yeniden üretmek için `make catalogue`.
> `ErrorCatalogueDocumentTest` ikisi ayrıştığı anda düşer.

Sunucu bir kod ve bu parametreleri gönderir, cümle göndermez.
İstemci `errors.{KOD}` anahtarını kendi dilinde çözer, yani
katalogda karşılığı olmayan bir kod kullanıcıya ham anahtar
olarak görünür. Parametrelerin **tipi** de sözleşmenin parçası:
ICU'da `{pinnedPages, number}` biçimlendirir, `{pinnedPages}`
yalnızca yerine koyar.

**Parametreler kullanıcı içeriği taşımaz** (mutlak kural 4) —
sayılar, sınırlar, tanımlayıcılar ve alan adları taşırlar.

| Kod | HTTP | `params` |
|---|---|---|
| `INSUFFICIENT_PROFILE` | 422 | `completeness: integer`, `missing: string[]` |
| `UNPARSEABLE_JOB_DESCRIPTION` | 422 | `reason: string`, `confidence: number`, `skillsFound: integer` |
| `CONFLICTING_PREFERENCES` | 409 | `pinnedPages: number`, `maxPages: integer` |
| `FEATURE_REQUIRES_ACCOUNT` | 403 | `feature: string` |
| `QUOTA_EXCEEDED` | 429 | `metric: string`, `resetsAt: timestamp` |
| `ALL_PROVIDERS_UNAVAILABLE` | 503 | `tried: string[]` |
| `COMPILATION_FAILED` | 502 | `detail: string`, `rawSourceAvailable: boolean` |
| `PAGE_LIMIT_EXCEEDED` | 422 | `actual: integer`, `limit: integer` |
| `REWRITE_VALIDATION_FAILED` | 500 | `atomId: uuid`, `issues: string[]` |
| `COVER_LETTER_REJECTED` | 422 | `issues: string[]` |
| `EMBEDDING_UNAVAILABLE` | 503 | — |
| `GENERATION_PAUSED` | 503 | — |
| `UNSUPPORTED_DOCUMENT` | 415 | `accepted: string[]` |
| `DOCUMENT_TOO_LARGE` | 413 | `limitBytes: integer` |
| `PDF_NOT_TEXT_BASED` | 422 | — |
| `PDF_ENCRYPTED` | 422 | — |
| `EXTRACTION_EMPTY` | 422 | — |
| `EXTRACTION_TIMEOUT` | 504 | — |
| `LANGUAGE_UNDETECTED` | 422 | `detectedCandidates: string[]` |
| `TRANSLATION_FAILED` | 422 | — |
| `PROFILE_QUOTA_EXCEEDED` | 429 | `limit: integer`, `resetsAt: timestamp` |
| `ANONYMOUS_SESSION_EXPIRED` | 401 | — |
| `ATOM_LIMIT_EXCEEDED` | 422 | `limit: integer`, `current: integer` |
| `PROFILE_ALREADY_EXISTS` | 409 | — |
| `GENERATION_ARTIFACT_EXPIRED` | 410 | — |
| `GENERATION_SUPERSEDED` | 409 | — |
| `EDIT_NOT_UNDERSTOOD` | 422 | — |
| `CSRF_TOKEN_INVALID` | 403 | — |
| `AUTHENTICATION_REQUIRED` | 401 | — |
| `OAUTH_FAILED` | 400 | `reason: string` |
| `MAGIC_LINK_INVALID` | 400 | — |
| `RATE_LIMITED` | 429 | `resetsAt: timestamp` |
| `CHALLENGE_FAILED` | 403 | — |
| `RESOURCE_NOT_FOUND` | 404 | — |
| `VERSION_CONFLICT` | 412 | — |
| `PRECONDITION_REQUIRED` | 428 | — |
| `VALIDATION_FAILED` | 400 | `fields: string[]` |
| `INTERNAL_ERROR` | 500 | — |
| `METHOD_NOT_ALLOWED` | 405 | — |
| `NOT_ACCEPTABLE` | 406 | — |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | — |
