# Bölüm IV — Veri Modeli (12-16)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM IV — VERİ MODELİ

## 12. Kavramsal Model

### 12.1 Hiyerarşi

```
User
 └── Profile (1:1)
      ├── Section (about | education | experience | projects | skills | languages | custom)
      │    └── Entry (deneyim girdisi, proje — opsiyonel; skills gibi bölümlerde yok)
      │         └── Atom (madde, beceri — en küçük seçilebilir birim)
      │              └── AtomVariant (dil/ton varyantları — metin BURADA)
      └── TemplateCustomization (şablon ayarları)
```

### 12.2 Atom kavramı

**Atom**, profildeki en küçük anlamlı, **bağımsız olarak seçilebilir** bilgi birimidir.

Önceki nesilde seçim birimi "tüm proje bloğu" iken, burada "o projenin 3. maddesi" ayrı bir karar birimidir. Bu, madde-bazlı optimizasyonu mümkün kılıyor.

**Kritik ayrım:** Atom = kimlik + kontroller + skorlama girdileri. **Metin atomda değil, varyanttadır.** Orijinal metin de `is_primary = true` olan bir varyanttır.

Bu tasarım, alternatif metin özelliğini "özel durum" olmaktan çıkarıp modelin doğal parçası yapıyor.

### 12.3 Run modeli — içerik formatı

```json
{
  "v": 1,
  "runs": [
    { "t": "Engineered ", "m": [] },
    { "t": "ETL", "m": ["technology"] },
    { "t": " pipelines processing ", "m": [] },
    { "t": "300K+ rows", "m": ["metric"] },
    { "t": " into a secure Lakehouse", "m": [] }
  ]
}
```

**Neden bu model:**

| Alternatif | Sorun |
|---|---|
| Markdown (`**ETL**`) | Escape sorunu; semantik bilgi kaybı (neden kalın?) |
| Offset tabanlı | Metin düzenlenince offset'ler kayar |
| Alt-metin (substring) | Belirsizlik: aynı kelime iki kez geçerse hangisi? |
| **Run modeli** | ✅ Belirsizlik yok, kayma yok, tek geçişte render, düz metin bedava |

**Ek fayda:** ProseMirror, Tiptap, Slate ve Word'ün OOXML formatı aynı modeli kullanıyor — zengin metin editörü entegrasyonu dönüşümsüz çalışıyor.

**Mark tipleri neden semantik (`technology`, `metric`), stil değil (`bold`):**

1. **Şablonlar farklı davranabilir:** Klasik şablonda ikisi de `\textbf{}`, Modern şablonda `metric` accent renkli.
2. **Doğrulama katmanı kullanıyor:** `metric` işaretli run'lar, "sayılar korundu mu?" kontrolünün doğrudan girdisi.
3. **Skorlama kullanıyor:** `technology` run'ları `skills[]` ile çapraz kontrol edilir; `impactScore` metrik varlığına bakar.

**LLM'e run ürettirmeye gerek yok:** LLM'den basit form istenir (`emphasis: ["ETL", "300K+ rows"]`), sunucu deterministik olarak run'lara çevirir.

---

## 13. Tam Veritabanı Şeması

> **Uygulanan `V1__initial_schema.sql` bunun birebir kopyası değil — bkz. EK D.1.**
> İki ekleme var: denormalize `profile_id`'yi ebeveyne bağlayan bileşik yabancı
> anahtarlar, ve `llm_invocations` üzerinde `ON DELETE SET NULL` taşıyan iki FK.
> Migration uygulandığı için artık değiştirilemez; farkı buradan değil EK D'den
> oku.

```sql
-- ══════════════════════════════════════════════════════════
-- V1__initial_schema.sql
-- ══════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ─────────────────────────────── KİMLİK ───────────────────────────────

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           CITEXT UNIQUE NOT NULL,
    display_name    TEXT,
    locale          TEXT NOT NULL DEFAULT 'tr',
    role            TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
    email_verified  BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE oauth_identities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        TEXT NOT NULL CHECK (provider IN ('google','github','linkedin')),
    provider_uid    TEXT NOT NULL,
    access_token_enc TEXT,                     -- şifreli
    scopes          TEXT[],
    connected_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_uid)
);
CREATE INDEX ON oauth_identities (user_id);

CREATE TABLE magic_link_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    selector        TEXT UNIQUE NOT NULL,      -- URL'de görünür, indeksli
    verifier_hash   TEXT NOT NULL,             -- URL'de görünür, DB'de hash'i
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_ip      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON magic_link_tokens (expires_at) WHERE used_at IS NULL;

CREATE TABLE email_suppressions (
    email       CITEXT PRIMARY KEY,
    reason      TEXT NOT NULL CHECK (reason IN ('hard_bounce','complaint','manual')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE email_preferences (
    user_id           UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    onboarding        BOOLEAN NOT NULL DEFAULT true,
    product_updates   BOOLEAN NOT NULL DEFAULT true,
    unsubscribed_at   TIMESTAMPTZ
);

-- ─────────────────────────────── PROFİL ───────────────────────────────

CREATE TABLE profiles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    headline          TEXT,
    contact           JSONB NOT NULL DEFAULT '{}',   -- name, email, phone, linkedin, github, website
    self_description  TEXT,                          -- serbest metin alanı
    preferences       JSONB NOT NULL DEFAULT '{}',   -- writingStyle, defaults
    source_language   TEXT NOT NULL DEFAULT 'en',
    enabled_languages TEXT[] NOT NULL DEFAULT ARRAY['en'],
    completeness      SMALLINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0       -- optimistic locking
);

CREATE TABLE sections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    kind            TEXT NOT NULL,     -- about|education|experience|projects|skills|soft_skills|languages|custom
    title           TEXT NOT NULL,     -- görünen başlık
    layout          TEXT NOT NULL DEFAULT 'bullet_list'
                      CHECK (layout IN ('bullet_list','entry_list','inline_list','two_column')),
    display_order   SMALLINT NOT NULL,
    always_include  BOOLEAN NOT NULL DEFAULT false,
    verbatim        BOOLEAN NOT NULL DEFAULT false,
    active          BOOLEAN NOT NULL DEFAULT true,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ON sections (profile_id, display_order);

CREATE TABLE entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,  -- denormalize
    section_id      UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    organization    TEXT,
    location        TEXT,
    start_date      DATE,
    end_date        DATE,                      -- NULL = devam ediyor
    url             TEXT,
    display_order   SMALLINT NOT NULL,
    importance      REAL NOT NULL DEFAULT 0.5 CHECK (importance BETWEEN 0 AND 1),
    active          BOOLEAN NOT NULL DEFAULT true,
    always_include  BOOLEAN NOT NULL DEFAULT false,
    verbatim        BOOLEAN NOT NULL DEFAULT false,
    min_atoms       SMALLINT NOT NULL DEFAULT 2,
    render_costs    JSONB NOT NULL DEFAULT '{}',   -- {"classic:v2": 24.0}
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ON entries (profile_id, section_id, display_order);

CREATE TABLE atoms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    section_id      UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    entry_id        UUID REFERENCES entries(id) ON DELETE CASCADE,   -- NULL: doğrudan bölüme bağlı
    kind            TEXT NOT NULL,   -- bullet | skill | language | certification | about_paragraph
    display_order   SMALLINT NOT NULL,

    -- kullanıcı kontrolleri
    importance      REAL NOT NULL DEFAULT 0.5 CHECK (importance BETWEEN 0 AND 1),
    active          BOOLEAN NOT NULL DEFAULT true,
    always_include  BOOLEAN NOT NULL DEFAULT false,
    verbatim        BOOLEAN NOT NULL DEFAULT false,

    -- skorlama girdileri
    skills          TEXT[] NOT NULL DEFAULT '{}',   -- kanonik formda
    metrics         TEXT[] NOT NULL DEFAULT '{}',
    proper_nouns    TEXT[] NOT NULL DEFAULT '{}',   -- doğrulama için

    -- embedding (EN varyantından hesaplanır)
    embedding       vector(1024),
    embedding_hash  TEXT,                           -- EN varyantın content_hash'i

    -- köken
    source          TEXT NOT NULL DEFAULT 'manual', -- manual | cv_upload | github
    verified        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ON atoms (profile_id, section_id) WHERE active;
CREATE INDEX ON atoms (entry_id) WHERE active;
CREATE INDEX ON atoms USING gin (skills);

CREATE TABLE atom_variants (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id              UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,  -- denormalize
    atom_id                 UUID NOT NULL REFERENCES atoms(id) ON DELETE CASCADE,
    is_primary              BOOLEAN NOT NULL DEFAULT false,
    language                TEXT NOT NULL DEFAULT 'en',
    tone                    TEXT,               -- formal | casual | technical | NULL

    content                 JSONB NOT NULL,     -- { v, runs }
    plain_text              TEXT NOT NULL,
    content_hash            TEXT NOT NULL,      -- sha256(plain_text)

    render_costs            JSONB NOT NULL DEFAULT '{}',  -- {"classic:v2": 27.7}
    cost_measured_at        TIMESTAMPTZ,

    -- türetilmiş varyant takibi
    derived_from_variant_id UUID REFERENCES atom_variants(id) ON DELETE SET NULL,
    source_hash             TEXT,
    is_stale                BOOLEAN NOT NULL DEFAULT false,
    is_user_edited          BOOLEAN NOT NULL DEFAULT false,

    created_by              TEXT NOT NULL DEFAULT 'user',  -- user | llm_extract | llm_translate | llm_rewrite
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ON atom_variants (atom_id) WHERE is_primary;
CREATE UNIQUE INDEX ON atom_variants (atom_id, language, COALESCE(tone,''));
CREATE INDEX ON atom_variants (profile_id);
CREATE INDEX ON atom_variants (atom_id) WHERE is_stale;

-- ─────────────────────────────── ETİKETLER ───────────────────────────────

CREATE TABLE tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    label       TEXT NOT NULL,          -- kanonik form
    UNIQUE (profile_id, label)
);

CREATE TABLE atom_tags (
    atom_id     UUID NOT NULL REFERENCES atoms(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    source      TEXT NOT NULL DEFAULT 'auto' CHECK (source IN ('auto','user')),
    PRIMARY KEY (atom_id, tag_id)
);
CREATE INDEX ON atom_tags (tag_id);

-- ─────────────────────────── ŞABLON ÖZELLEŞTİRME ───────────────────────────

CREATE TABLE template_customizations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id          UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name                TEXT NOT NULL,
    base_template_id    TEXT NOT NULL,          -- classic | modern | compact
    template_version    SMALLINT NOT NULL,      -- renderer sürümü
    params              JSONB NOT NULL,         -- fontFamily, fontSizePt, marginIn, lineSpacing, accentColor, sections
    fixed_costs         JSONB,                  -- ölçülmüş sabit maliyetler
    page_text_height_pt REAL,
    measured_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (profile_id, name)
);

-- ─────────────────────────────── ÜRETİM ───────────────────────────────

CREATE TABLE generations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID REFERENCES users(id) ON DELETE CASCADE,
    profile_id            UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,

    job_description       TEXT,                 -- NULL = Genel CV modu
    jd_hash               TEXT,
    jd_analysis           JSONB,                -- Faz A çıktısı
    directives            JSONB,                -- kullanıcı yönlendirmeleri
    options               JSONB NOT NULL,       -- template, maxPages, cvLanguage, coverLetterLanguage

    selection_state       JSONB NOT NULL,       -- Faz C çıktısı (snapshot)
    content_snapshot      JSONB,                -- arşivlenirse tam metin
    cover_letter          TEXT,

    fit_report            JSONB,                -- kapsama sayıları
    page_count            SMALLINT,
    engine_version        JSONB NOT NULL,       -- pipeline, scoringWeights, template, promptVersions
    trace                 JSONB,                -- faz bazında telemetri (PII yok)

    status                TEXT NOT NULL,        -- completed | failed | superseded
    parent_generation_id  UUID REFERENCES generations(id) ON DELETE SET NULL,
    archived              BOOLEAN NOT NULL DEFAULT false,
    pdf_key               TEXT,                 -- R2 anahtarı
    pdf_expires_at        TIMESTAMPTZ,          -- 14 gün (arşivliyse NULL)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON generations (user_id, created_at DESC);
CREATE INDEX ON generations (pdf_expires_at) WHERE pdf_expires_at IS NOT NULL;

CREATE TABLE generation_feedback (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generation_id   UUID NOT NULL REFERENCES generations(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    rating          SMALLINT NOT NULL CHECK (rating IN (-1, 1)),
    category        TEXT,   -- selection | writing | format | density | other
    comment         TEXT,
    content_granted BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE support_grants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    generation_id UUID NOT NULL REFERENCES generations(id) ON DELETE CASCADE,
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    accessed_at   TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ
);

-- ─────────────────────────── BAŞVURU TAKİBİ ───────────────────────────

CREATE TABLE applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    generation_id   UUID REFERENCES generations(id) ON DELETE SET NULL,
    company         TEXT NOT NULL,
    position        TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'applied'
                      CHECK (status IN ('applied','interview','offer','rejected','withdrawn')),
    applied_at      DATE NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ON applications (user_id, applied_at DESC);

-- ─────────────────────────────── KUYRUK ───────────────────────────────

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            TEXT NOT NULL,     -- generation | profile_extract | measurement | translation | embedding | email
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    anon_session_id TEXT,
    idempotency_key TEXT,
    payload         JSONB NOT NULL,
    status          TEXT NOT NULL DEFAULT 'queued'
                      CHECK (status IN ('queued','running','completed','failed','cancelled')),
    priority        SMALLINT NOT NULL DEFAULT 100,
    progress        JSONB NOT NULL DEFAULT '{}',
    result          JSONB,
    error           JSONB,
    attempts        SMALLINT NOT NULL DEFAULT 0,
    max_attempts    SMALLINT NOT NULL DEFAULT 3,
    locked_by       TEXT,
    locked_at       TIMESTAMPTZ,
    heartbeat_at    TIMESTAMPTZ,
    run_after       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX ON jobs (status, priority, run_after) WHERE status = 'queued';
CREATE INDEX ON jobs (status, heartbeat_at) WHERE status = 'running';
CREATE UNIQUE INDEX ON jobs (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- ──────────────────────── TELEMETRİ VE KOTA ────────────────────────

CREATE TABLE llm_invocations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID,
    user_id         UUID,
    prompt_id       TEXT NOT NULL,
    prompt_version  TEXT NOT NULL,
    provider        TEXT NOT NULL,
    model           TEXT NOT NULL,
    input_tokens    INT,
    output_tokens   INT,
    cached_tokens   INT,
    cost_usd        NUMERIC(10,6),
    latency_ms      INT,
    outcome         TEXT NOT NULL,  -- success | schema_error | validation_failed | provider_error
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
    -- ⚠️ prompt/response İÇERİĞİ SAKLANMAZ
);
CREATE INDEX ON llm_invocations (created_at);
CREATE INDEX ON llm_invocations (user_id, created_at);

CREATE TABLE usage_counters (
    subject_type    TEXT NOT NULL,   -- user | ip | anon_session
    subject_id      TEXT NOT NULL,
    metric          TEXT NOT NULL,   -- generation | profile_extract | llm_cost
    period          DATE NOT NULL,   -- UTC takviminde gün (F-007) — EK D.6.5
    count           INT NOT NULL DEFAULT 0,
    cost_usd        NUMERIC(10,6) NOT NULL DEFAULT 0,
    PRIMARY KEY (subject_type, subject_id, metric, period)
);

CREATE TABLE feature_flags (
    key         TEXT PRIMARY KEY,
    enabled     BOOLEAN NOT NULL DEFAULT true,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 13.1 Şema tasarım kararları

| Karar | Gerekçe |
|---|---|
| Her tablo `users`'a `ON DELETE CASCADE` | "Hesabımı sil" tek `DELETE FROM users` ile eksiksiz çalışır — unutulma hakkının teknik garantisi |
| `profile_id` denormalizasyonu (entries, atoms, atom_variants) | Profil yüklemede 4 düz sorgu mümkün olur; JOIN FETCH zinciri kartezyen çarpım üretirdi |
| `selection_state` JSONB snapshot | Geçmiş üretim, profil sonradan değişse bile bozulmaz |
| `content_snapshot` (arşivlenirse) | Tam metin, PDF silinse bile yeniden üretilebilir |
| `parent_generation_id` | Düzenleme zinciri; "3 adım geri al" mümkün |
| `render_costs` JSONB (punto cinsinden) | Şablon başına ayrı satır yerine tek kolon; anahtar `template:version` |
| `embedding` atomda, varyantta değil | Varyantlar aynı anlamın farklı ifadeleri; "hangi atom alakalı?" anlam sorusu |
| `version` kolonları | JPA `@Version` → optimistic locking → ETag desteği |
| Ebeveynlerde `UNIQUE (id, profile_id)` + kompozit FK | Denormalize edilen `profile_id`'nin ebeveyn satırınkiyle aynı olduğunu hiçbir şey garanti etmiyordu; uyuşmazlık sessiz bir kiracılar-arası sızıntı olurdu. `atoms.entry_id IS NULL` durumunda uygulanmaz — bölüm düzeyi atomlar kasıtlı olarak öyle |

---

## 14. JSONB Yapıları

### 14.1 `atom_variants.content`

```json
{
  "v": 1,
  "runs": [
    { "t": "metin parçası", "m": ["technology"] }
  ]
}
```

**Mark tipleri:** `technology`, `metric`, `emphasis`, `link` (ek olarak `href`), `organization`

> **Kurallar — bkz. EK D.2:** `href` yalnız `link` mark'ı olan run'da bulunur ve
> orada zorunludur. Mark listesi kapalı değildir: bilinmeyen bir mark okunur,
> korunur ve düz metin olarak render edilir.
>
> **Frontend (EK D.9 · 1-4).** Editörün uyması gereken dört kural:
> 1. `link` run'ında `href` zorunlu, diğer run'larda yasak — backend aksini
>    reddeder. `richContent.ts` invariant'ı olmalı.
> 2. **Bilinmeyen mark'lar korunmalı.** Backend düşürmüyor; editör düşürürse
>    daha yeni bir sürümün yazdığı işaretler, kullanıcı o cümleyi kaydettiği an
>    sessizce silinir.
> 3. `v` sunucuya aittir. Frontend yalnız `runs` gönderir; gönderirse mevcut
>    sürümden büyük olamaz.
> 4. `m` her zaman dizidir — mark'sız run bile `"m": []` taşır.

### 14.2 `profiles.contact`

```json
{
  "name": "Mustafa Tetik",
  "email": "...",
  "phone": "+90 ...",
  "linkedin": "https://linkedin.com/in/...",
  "github": "https://github.com/...",
  "website": "https://mustafatetik.com",
  "location": "İstanbul, Türkiye"
}
```

### 14.3 `profiles.preferences`

```json
{
  "writingStyle": {
    "emphasizeMetrics": true,
    "tone": "formal",
    "conciseSentences": false,
    "customInstructions": "Liderlik deneyimlerimi öne çıkar"
  },
  "defaults": {
    "maxPages": 1,
    "templateId": "classic",
    "cvLanguage": "auto",
    "coverLetterLanguage": "auto"
  }
}
```

**`cvLanguage: "auto"` "ilanı izle" demektir**, ve iki modda farklı sonuç
verir. İlana özel üretimde Faz A'nın `jdLanguage`'i okunur; genel CV modunda
izlenecek bir ilan yok, bu yüzden profilin `source_language`'ine düşülür.
İlan bir dil adlandırmadıysa (`jdLanguage` boş) yine `source_language`
kullanılır. Dili açıkça adlandıran bir tercih bir karardır ve ilanı yener;
isteğin kendi `language` alanı ikisini de yener.

### 14.4 `generations.options`

```json
{
  "customizationId": "cst_...",
  "templateId": "modern",
  "templateVersion": 2,
  "maxPages": 1,
  "cvLanguage": "en",
  "coverLetterLanguage": "tr",
  "formats": ["pdf"],
  "saveToTracking": true
}
```

### 14.5 `generations.selection_state`

```json
{
  "language": "en",
  "customizationId": "cst_...",
  "budget": { "totalPt": 648.0, "fixedPt": 142.0, "freePt": 506.0, "usedPt": 498.3 },
  "selected": [
    {
      "atomId": "atm_...", "variantId": "var_...",
      "score": 0.94, "renderCostPt": 27.7,
      "matchedKeywords": ["go", "microservices"],
      "forcedByLock": false,
      "rewritten": true
    }
  ],
  "rejected": [
    { "atomId": "atm_...", "score": 0.12, "reason": "BUDGET" }
  ]
}
```

`rejected.reason` değerleri: `BUDGET` | `LOW_SCORE` | `INACTIVE` | `DIVERSITY_CAP` | `USER_EXCLUDED`

### 14.6 `generations.trace`

```json
{
  "A": { "durationMs": 1840, "provider": "gemini", "promptVersion": "v2",
         "confidence": 0.91, "requiredSkillsFound": 4, "cacheHit": false },
  "B": { "durationMs": 47, "atomsScored": 63,
         "scoreDistribution": { "p10": 0.11, "p50": 0.44, "p90": 0.87 } },
  "C": { "durationMs": 12, "selected": 16, "rejected": 47,
         "rejectionReasons": { "BUDGET": 31, "DIVERSITY_CAP": 9, "INACTIVE": 7 },
         "pinnedCostPt": 84.2, "estimatedAtoms": 2 },
  "D": { "durationMs": 3120, "attempts": 6, "accepted": 5, "rejected": 1,
         "rejectReasons": ["NUMBER_LOST"], "translationsUsed": 4, "translationsGenerated": 2 },
  "E": { "durationMs": 210, "sourceBytes": 8420 },
  "F": { "durationMs": 4900, "pageCount": 1, "driftPt": 2.1, "atsExtractionOk": true }
}
```

### 14.7 `generations.engine_version`

```json
{
  "pipeline": "1.4.0",
  "scoringWeights": "v3",
  "template": "modern:v2",
  "promptVersions": { "job_analysis": "v2", "atom_rewrite": "v1", "about_synthesis": "v1" }
}
```

---

## 15. İndeks Stratejisi

| İndeks | Sorgu deseni |
|---|---|
| `atoms (profile_id, section_id) WHERE active` | Profil yükleme (en sık) |
| `atom_variants (profile_id)` | Profil yükleme — düz sorgu |
| `atoms USING gin (skills)` | Beceri bazlı filtreleme |
| `jobs (status, priority, run_after) WHERE queued` | Kuyruk çekme |
| `jobs (status, heartbeat_at) WHERE running` | Zombi iş toplama |
| `generations (user_id, created_at DESC)` | Geçmiş listesi |
| `applications (user_id, applied_at DESC)` | Başvuru listesi |
| `atoms USING hnsw (embedding vector_cosine_ops)` | **Sadece 10k+ satırda** |

**pgvector notu:** Sorgu her zaman `WHERE profile_id = ?` ile filtreleniyor ve tek profilde 50-300 atom var. Bu küme üzerinde sequential scan, HNSW indeksinden hızlıdır. İndeksi erken ekleme — ölçüp karar ver.

---

## 16. Şema Evrimi

Üç bağımsız mekanizma:

### 16.1 SQL şeması — Flyway

```
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_template_customizations.sql
└── V3__add_content_version.sql
```

**Kurallar:**
- Uygulanmış migration dosyası **asla değiştirilmez** (checksum korumalı)
- `flyway.validateOnMigrate=true`
- Migration **deploy'dan önce** çalışır (CI adımı), uygulama başlangıcında değil (üretimde)
- Lokalde uygulama başlangıcında çalışabilir

**Expand-contract deseni** (rollback mümkün kalsın):
```
1. EXPAND    : Yeni kolonu nullable ekle
2. DEPLOY    : Yeni kod hem eski hem yeniyi okur
3. BACKFILL  : Arka planda veriyi doldur (batch'ler halinde)
4. ENFORCE   : NOT NULL kısıtı ekle
5. CONTRACT  : Eski kolonu sil (birkaç deploy sonra)
```

### 16.2 JSONB içi yapı — ContentMigrator

Flyway JSONB'nin içini göremez. Sürüm damgası + lazy upgrade:

```java
@Component
public class ContentMigrator {
    private static final int CURRENT_VERSION = 1;

    private final Map<Integer, Function<JsonNode, JsonNode>> upgrades = Map.of(
        // 1, this::v1_to_v2   (gelecekte)
    );

    public RichContent read(JsonNode stored) {
        int version = stored.path("v").asInt(1);
        JsonNode current = stored;
        while (version < CURRENT_VERSION) {
            current = upgrades.get(version).apply(current);
            version++;
        }
        return parse(current);
    }
}
```

**Kritik:** `content_hash` **`plain_text` üzerinden** hesaplanır, JSONB yapısı üzerinden değil. Aksi halde format değişimi, metin aynı kalsa bile tüm embedding ve ölçümleri geçersiz kılar.

> **Frontend (EK D.9 · 5).** Yalnız işaretleme değişince hash değişmez.
> "Değişti, yeniden ölçülmeli" türü bir gösterge run yapısına değil `contentHash`
> alanına bakmalı; aksi halde bir kelimeyi kalınlaştırmak, hiçbir şey
> gerektirmediği hâlde yeniden ölçüm uyarısı çıkarır.

**İleri uyumluluk:** Renderer bilinmeyen mark tiplerini sessizce yok sayar:
```java
default -> text;   // bilinmeyen mark → düz metin, çökme yok
```

### 16.3 Renderer geometrisi — Template version

Renderer'da geometrik değişiklik (madde aralığı, başlık boşluğu) yapılırsa mevcut `render_costs` değerleri yanlış olur — ve bu **sessizce** sayfa garantisini bozar.

```yaml
templates:
  modern:
    version: 3    # geometrik değişiklikte artır
```

```java
if (storedCostVersion < currentTemplateVersion) {
    invalidateCosts(profileId, templateId);
    enqueueMeasurement(profileId, templateId);
}
```

`render_costs` anahtarı bu yüzden `"modern:v3"` formatındadır.

---
