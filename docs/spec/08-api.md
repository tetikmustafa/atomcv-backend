# Bölüm VII/1 — API Sözleşmesi (35)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM VII — API VE FRONTEND

## 35. API Sözleşmesi

### 35.1 Temel kararlar

- **BFF yok** — Next.js doğrudan Spring API'yi çağırır
- **Aynı domain** (`atomcv.mustafatetik.com/api/*`) → CORS gerekmez, `SameSite=Strict` çalışır
- **Hiçbir yolda `userId` yok** — kaynak sahipliği oturumdan gelir (IDOR koruması)
- **Versiyonlama baştan** — `/api/v1/...`

> **Frontend (EK D.9 · 6).** Kapalı sözlükler API'de **küçük harf** gider ve
> gelir: `kind`, `layout`, `source`, `created_by`, `tone`, ve
> `resolutions[].action`. Değerler şemada enum olarak yayınlanır
> (`bullet_list`, `about_paragraph`, `cv_upload`, `increase_page_limit`).
> Hata `code` alanı bunun tersine **büyük harf**tir — çeviri anahtarı olduğu
> için: `errors.CONFLICTING_PREFERENCES`.

### 35.2 Kaynak haritası

```
── Kimlik ──────────────────────────────────────────
POST   /api/v1/auth/magic-link              magic link iste
POST   /api/v1/auth/verify                  token doğrula (POST! prefetch koruması)
GET    /api/v1/auth/session                 oturum + capabilities
POST   /api/v1/auth/logout
GET    /api/v1/auth/oauth/{provider}/start
GET    /api/v1/auth/oauth/{provider}/callback

── Profil ──────────────────────────────────────────
GET    /api/v1/profile                      yoksa yaratır, 404 dönmez (EK D.8)
PUT    /api/v1/profile
PUT    /api/v1/profile/preferences       PATCH değil — tamamını değiştirir (EK D.6.2)
DELETE /api/v1/profile
GET    /api/v1/profile/export               JSON + Markdown

GET    /api/v1/profile/sections
POST   /api/v1/profile/sections
PATCH  /api/v1/profile/sections/{id}
DELETE /api/v1/profile/sections/{id}
POST   /api/v1/profile/sections/reorder

GET    /api/v1/profile/entries             ?sectionId= ile süzülür (EK D.6.2)
POST   /api/v1/profile/entries
PATCH  /api/v1/profile/entries/{id}
DELETE /api/v1/profile/entries/{id}
POST   /api/v1/profile/entries/reorder     bir bölümün tamamı

GET    /api/v1/profile/atoms
POST   /api/v1/profile/atoms
PATCH  /api/v1/profile/atoms/{id}           kontroller
DELETE /api/v1/profile/atoms/{id}
POST   /api/v1/profile/atoms/reorder
POST   /api/v1/profile/atoms/{id}/variants
PATCH  /api/v1/profile/atoms/{id}/variants/{vid}
DELETE /api/v1/profile/atoms/{id}/variants/{vid}
POST   /api/v1/profile/atoms/{id}/tags
DELETE /api/v1/profile/atoms/{id}/tags/{tagId}

── Ingestion ───────────────────────────────────────
POST   /api/v1/ingestion/cv                 multipart → job
POST   /api/v1/ingestion/cv/{jobId}/apply   gözden geçirme onayı
POST   /api/v1/ingestion/github/connect
POST   /api/v1/ingestion/github/apply

── Şablon ──────────────────────────────────────────
GET    /api/v1/templates
GET    /api/v1/customizations
POST   /api/v1/customizations
PATCH  /api/v1/customizations/{id}
DELETE /api/v1/customizations/{id}

── Üretim ──────────────────────────────────────────
POST   /api/v1/generations/general          → 200 + PDF  (Aşama 1, senkron)
POST   /api/v1/generations                  → 202 + job
GET    /api/v1/generations
GET    /api/v1/generations/{id}
GET    /api/v1/generations/{id}/download?format=pdf|docx|source
POST   /api/v1/generations/{id}/edits       Faz G: doğal dil
POST   /api/v1/generations/{id}/selection   manuel toggle
POST   /api/v1/generations/{id}/archive
POST   /api/v1/generations/{id}/feedback
POST   /api/v1/generations/{id}/cover-letter/regenerate

── İşler ───────────────────────────────────────────
GET    /api/v1/jobs/{id}
GET    /api/v1/jobs/{id}/stream             SSE

── Başvuru takibi ──────────────────────────────────
GET    /api/v1/applications
POST   /api/v1/applications
PATCH  /api/v1/applications/{id}
DELETE /api/v1/applications/{id}

── Hesap ───────────────────────────────────────────
GET    /api/v1/account/usage
PATCH  /api/v1/account/email-preferences
DELETE /api/v1/account                      unutulma hakkı

── Webhook ─────────────────────────────────────────
POST   /webhooks/resend                     imza doğrulamalı
```

### 35.3 Uzun süren işler: 202 + job

```http
POST /api/v1/generations
Idempotency-Key: 7f3a9c2e-...
Content-Type: application/json

{ "jobDescription": "...", "directives": {...}, "options": {...} }
```

```http
HTTP/1.1 202 Accepted
Location: /api/v1/jobs/9b1c4e7a-...

{ "jobId": "9b1c4e7a-...", "status": "queued",
  "streamUrl": "/api/v1/jobs/9b1c4e7a-.../stream" }
```

**Ön kontroller senkron** — profil yetersizliği, çelişki, kota doğrudan 4xx döner, iş kuyruğa girmez.

> **Not (Adım 1.8).** `POST /generations/general` bu akışın dışında: ilan da
> LLM de kuyruk da yok, belge doğrudan `application/pdf` olarak dönüyor ve
> hiçbir yere kaydedilmiyor. **Aşama 1'e özgüdür**; Aşama 2'de üretim kaydı ve
> kuyruk gelince yerini yukarıdaki akışa bırakır (EK D.8.8, D.9 · 22).

### 35.4 Hata formatı — RFC 7807 + resolutions

```json
{
  "type": "https://atomcv.mustafatetik.com/errors/conflicting-preferences",
  "title": "Sabitlenen içerik sayfa sınırını aşıyor",
  "status": 409,
  "instance": "/api/v1/generations",
  "code": "CONFLICTING_PREFERENCES",
  "params": { "pinnedPages": 2.3, "maxPages": 1 },
  "resolutions": [
    { "action": "increase_page_limit", "params": { "maxPages": 3 } },
    { "action": "review_pins" },
    { "action": "keep_top_pinned", "params": { "keep": 3 } }
  ]
}
```

**Sunucu çeviri anahtarı gönderir, metin değil.** Frontend `errors.CONFLICTING_PREFERENCES` anahtarını kendi dilinde çözer. `resolutions` dizisinden butonlar otomatik üretilir.

> **Frontend (EK D.9 · 7, 10-11).** Tam katalog **EK D.6.1'de**: 27 kod, HTTP
> durumları ve her kodun `params` anahtarları **tipleriyle**. `en.json` ve
> `tr.json` artık buradan yazılabilir. Üç kod dokümanın gövdesinde yoktur ve
> Adım 1.2'de eklendi: `RESOURCE_NOT_FOUND`, `VERSION_CONFLICT`,
> `VALIDATION_FAILED`.
>
> Sunucu **bildirilmemiş bir `params` alanı göndermez** — gövde kurulurken
> katalog doğrulanıyor, eksik ya da fazla anahtar orada patlıyor. Bir alan
> eksik görünüyorsa çözüm katalogda, gövdede değil.
>
> `title` alanı **geliştiriciye yöneliktir, gösterilmez** (EK D.6.2); yalnız
> log'a yazılır.

### 35.5 HTTP durum eşlemesi

```java
int httpStatus(PipelineError e) {
    return switch (e) {
        case InsufficientProfile ignored       -> 422;
        case UnparseableJobDescription ignored -> 422;
        case ConflictingPreferences ignored    -> 409;
        case FeatureRequiresAccount ignored    -> 403;
        case QuotaExceeded ignored             -> 429;
        case AllProvidersUnavailable ignored   -> 503;
        case CompilationFailed ignored         -> 502;
        case EmbeddingUnavailable ignored      -> 503;
        case PageLimitExceeded ignored         -> 422;
        case RewriteValidationFailed ignored   -> 500;
    };
}
```

`FeatureRequiresAccount` → 403 + `resolutions: [{ "action": "sign_up" }]`

### 35.6 Kısmi güncelleme ve eşzamanlılık

```http
PATCH /api/v1/profile/atoms/{id}
Content-Type: application/json
If-Match: "7"

{ "importance": 0.9, "alwaysInclude": true }
```

Gönderilmeyen alanlar dokunulmaz. Versiyon uyuşmazsa **412 Precondition Failed**.

JPA `@Version` → ETag.

> **Düzeltme (EK D.6.4).** Bu bölüm önce `application/merge-patch+json` ve
> `If-Match: "v7"` yazıyordu; ikisi de yanlıştı ve ikisi de sessizce
> kırıyordu.
>
> **Media type `application/json`.** RFC 7396'nın kayıtlı tipi kullanılmıyor,
> çünkü dört PATCH ucundan yalnız `EntryPatch` onun semantiğini uyguluyor:
> orada `null` "temizle" demek, atom/bölüm/varyant yamalarında ise
> "dokunma" — kolonları zaten null olamıyor. Uygulamadığımız bir semantiği
> kayıtlı tiple ilan etmek sözleşmede yanlış beyandır. Dokümanı izleyip
> merge-patch gönderen istemci artık **415 `UNSUPPORTED_MEDIA_TYPE`** alıyor;
> daha önce 500 alıyordu, yani sunucunun bozulduğu söyleniyordu.
>
> **ETag'de önek yok.** Gerçek etiket `"7"`, `"v7"` değil; başlık birebir
> karşılaştırılıyor, yani önekli ya da tırnaksız bir değer 412 döner ve bu
> gerçek bir çakışmadan ayırt edilemez.

> **Frontend (EK D.9 · 8, 15).** **`If-Match` yazma işlemlerinde zorunludur**;
> başlıksız istek `428 PRECONDITION_REQUIRED` alır, bayat etiket `412
> VERSION_CONFLICT` + `retry`. Gerekçe P8: önkoşulsuz yazma, kullanıcının kendi
> işini sessizce ezmenin adıdır.
>
> ETag yalnız `version` kolonu olan altı tabloda:
> `profiles`, `sections`, `entries`, `atoms`, `atom_variants`, `applications`.
> **`generations` bunlardan biri değil** — üretim kaynaklarına `If-Match`
> göndermek işe yaramaz, sonuç ekranı iyimser kilit istiyorsa bu bir şema
> değişikliği talebidir. Koleksiyon yanıtları her öğede `version` taşır, yani
> N sürüm için N istek gerekmez. 412'nin kodu `VERSION_CONFLICT`.
>
> **Yazma yanıtları da ETag taşır** ve artık şemada da öyle yazıyor
> (EK D.6.4): `PATCH` hem `ETag` başlığını hem gövdede `version` alanını
> döndürür, yani otomatik kaydetme iki yazma arasında okuma yapmak zorunda
> değil. Koleksiyon okumaları bilerek taşımıyor — tek bir etiket bir listeyi
> temsil edemez; onlarda öğe başına `version` var.

### 35.7 Yetenekler istemciye

> **Frontend (EK D.9 · 9).** Anonim oturumda `capabilities`,
> `anonymousExpiresAt` (ISO 8601) taşır ve **bu değer etkinlikte tazelenir** —
> TTL kayar. Kullanıcıya gösterilen metin "iki saat sonra" değil **"son
> etkinliğinden iki saat sonra"** demeli. Süre dolduğunda sunucu `401` +
> `ANONYMOUS_SESSION_EXPIRED` + `sign_up` resolution'ı döner; oturum çerezi
> hesaplı oturumla aynı `sid`'dir, yani kimlik doğrulama istemci tarafında bir
> `capabilities` sorusudur.

```json
GET /api/v1/auth/session
{
  "authenticated": false,
  "capabilities": {
    "allowedLanguages": ["en"],
    "allowedTemplates": ["classic", "modern", "compact"],
    "canCustomizeTemplate": false,
    "canEditAtomControls": false,
    "canAddAlternatives": false,
    "canSaveHistory": false,
    "dailyGenerationQuota": 5,
    "generationsUsedToday": 2,
    "dailyProfileQuota": 3,
    "profilesUsedToday": 1,
    "maxAtoms": 60
  }
}
```

**Sunucu yine de doğrular** — istemci kontrolü sadece UX.

### 35.8 Tip üretimi (repolar arası)

Backend ve frontend ayrı repolarda olduğu için tip senkronizasyonu **OpenAPI şeması üzerinden** yapılır:

```
atomcv-backend                          atomcv-frontend
──────────────                          ───────────────
springdoc-openapi                       npm run gen:api
  └─> /v3/api-docs (çalışan uygulama)      └─> openapi-typescript
  └─> openapi.json (build çıktısı)         └─> src/types/api.d.ts  (COMMIT EDİLİR)
```

**Backend tarafı:**
```yaml
- run: ./gradlew generateOpenApiDocs
- run: git diff --exit-code build/openapi.json || echo "::warning::API şeması değişti"
- uses: actions/upload-artifact@v4      # frontend'in çekebilmesi için
  with: { name: openapi-schema, path: build/openapi.json }
```

**Frontend tarafı** (`package.json`):
```json
{
  "scripts": {
    "gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts",
    "gen:api:ci": "openapi-typescript ./openapi.json -o src/types/api.d.ts"
  }
}
```

**Üretilen `src/types/api.d.ts` frontend reposuna commit edilir** — böylece backend çalışmadan da frontend build edilebilir. Elle tip yazmak yasaktır (senkronizasyon hatası kaynağı).

**Sözleşme uyumsuzluğu tespiti:** Frontend CI'da `gen:api:ci` çalıştırılıp `git diff --exit-code` kontrol edilir; fark varsa "backend API değişmiş, tipleri güncelle" uyarısı verilir.

---
