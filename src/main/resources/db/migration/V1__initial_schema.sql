-- ══════════════════════════════════════════════════════════
-- V1__initial_schema.sql
--
-- Full baseline schema (teknik-mimari-dokumani.md, Bolum 13).
-- Tables for later stages (jobs, llm_invocations, ...) are created now and
-- stay empty until their stage arrives; splitting them across migrations
-- would mean revisiting the same tables under a rule that forbids editing
-- applied migrations.
--
-- Tenant integrity: entries, atoms and atom_variants carry a denormalized
-- profile_id so a profile can be loaded with flat queries instead of a
-- cartesian JOIN FETCH chain. Composite foreign keys guarantee that the
-- denormalized profile_id always agrees with the parent row's profile_id;
-- without them a mismatch would be a silent cross-tenant leak.
-- ══════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ─────────────────────────────── IDENTITY ───────────────────────────────

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
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         TEXT NOT NULL CHECK (provider IN ('google','github','linkedin')),
    provider_uid     TEXT NOT NULL,
    access_token_enc TEXT,                      -- encrypted
    scopes           TEXT[],
    connected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_uid)
);
CREATE INDEX ON oauth_identities (user_id);

CREATE TABLE magic_link_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    selector        TEXT UNIQUE NOT NULL,       -- appears in the URL, indexed
    verifier_hash   TEXT NOT NULL,              -- hash of the verifier; the verifier itself only appears in the URL
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

-- ─────────────────────────────── PROFILE ───────────────────────────────

CREATE TABLE profiles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    headline          TEXT,
    contact           JSONB NOT NULL DEFAULT '{}',   -- name, email, phone, linkedin, github, website
    self_description  TEXT,                          -- free-text field
    preferences       JSONB NOT NULL DEFAULT '{}',   -- writingStyle, defaults
    source_language   TEXT NOT NULL DEFAULT 'en',
    enabled_languages TEXT[] NOT NULL DEFAULT ARRAY['en'],
    completeness      SMALLINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0      -- optimistic locking
);

CREATE TABLE sections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    kind            TEXT NOT NULL,     -- about|education|experience|projects|skills|soft_skills|languages|custom
    title           TEXT NOT NULL,     -- displayed heading
    layout          TEXT NOT NULL DEFAULT 'bullet_list'
                      CHECK (layout IN ('bullet_list','entry_list','inline_list','two_column')),
    display_order   SMALLINT NOT NULL,
    always_include  BOOLEAN NOT NULL DEFAULT false,
    verbatim        BOOLEAN NOT NULL DEFAULT false,
    active          BOOLEAN NOT NULL DEFAULT true,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, profile_id)            -- target of composite foreign keys
);
CREATE INDEX ON sections (profile_id, display_order);

CREATE TABLE entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,  -- denormalized
    section_id      UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    organization    TEXT,
    location        TEXT,
    start_date      DATE,
    end_date        DATE,                      -- NULL = ongoing
    url             TEXT,
    display_order   SMALLINT NOT NULL,
    importance      REAL NOT NULL DEFAULT 0.5 CHECK (importance BETWEEN 0 AND 1),
    active          BOOLEAN NOT NULL DEFAULT true,
    always_include  BOOLEAN NOT NULL DEFAULT false,
    verbatim        BOOLEAN NOT NULL DEFAULT false,
    min_atoms       SMALLINT NOT NULL DEFAULT 2,
    render_costs    JSONB NOT NULL DEFAULT '{}',   -- {"classic:v2": 24.0}
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, profile_id),
    FOREIGN KEY (section_id, profile_id)
        REFERENCES sections (id, profile_id) ON DELETE CASCADE
);
CREATE INDEX ON entries (profile_id, section_id, display_order);

CREATE TABLE atoms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    section_id      UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    entry_id        UUID REFERENCES entries(id) ON DELETE CASCADE,   -- NULL: attached directly to the section
    kind            TEXT NOT NULL,   -- bullet | skill | language | certification | about_paragraph
    display_order   SMALLINT NOT NULL,

    -- user controls
    importance      REAL NOT NULL DEFAULT 0.5 CHECK (importance BETWEEN 0 AND 1),
    active          BOOLEAN NOT NULL DEFAULT true,
    always_include  BOOLEAN NOT NULL DEFAULT false,
    verbatim        BOOLEAN NOT NULL DEFAULT false,

    -- scoring inputs
    skills          TEXT[] NOT NULL DEFAULT '{}',   -- canonical form
    metrics         TEXT[] NOT NULL DEFAULT '{}',
    proper_nouns    TEXT[] NOT NULL DEFAULT '{}',   -- used by rewrite validation

    -- embedding (computed from the EN variant)
    embedding       vector(1024),
    embedding_hash  TEXT,                           -- content_hash of the EN variant

    -- provenance
    source          TEXT NOT NULL DEFAULT 'manual', -- manual | cv_upload | github
    verified        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, profile_id),
    FOREIGN KEY (section_id, profile_id)
        REFERENCES sections (id, profile_id) ON DELETE CASCADE,
    -- Not enforced when entry_id IS NULL (MATCH SIMPLE), which is the case
    -- for atoms attached directly to a section.
    FOREIGN KEY (entry_id, profile_id)
        REFERENCES entries (id, profile_id) ON DELETE CASCADE
);
CREATE INDEX ON atoms (profile_id, section_id) WHERE active;
CREATE INDEX ON atoms (entry_id) WHERE active;
CREATE INDEX ON atoms USING gin (skills);

CREATE TABLE atom_variants (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id              UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,  -- denormalized
    atom_id                 UUID NOT NULL REFERENCES atoms(id) ON DELETE CASCADE,
    is_primary              BOOLEAN NOT NULL DEFAULT false,
    language                TEXT NOT NULL DEFAULT 'en',
    tone                    TEXT,               -- formal | casual | technical | NULL

    content                 JSONB NOT NULL,     -- { v, runs }
    plain_text              TEXT NOT NULL,
    content_hash            TEXT NOT NULL,      -- sha256(plain_text)

    render_costs            JSONB NOT NULL DEFAULT '{}',  -- {"classic:v2": 27.7}
    cost_measured_at        TIMESTAMPTZ,

    -- derived variant tracking
    derived_from_variant_id UUID REFERENCES atom_variants(id) ON DELETE SET NULL,
    source_hash             TEXT,
    is_stale                BOOLEAN NOT NULL DEFAULT false,
    is_user_edited          BOOLEAN NOT NULL DEFAULT false,

    created_by              TEXT NOT NULL DEFAULT 'user',  -- user | llm_extract | llm_translate | llm_rewrite
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (atom_id, profile_id)
        REFERENCES atoms (id, profile_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ON atom_variants (atom_id) WHERE is_primary;
CREATE UNIQUE INDEX ON atom_variants (atom_id, language, COALESCE(tone,''));
CREATE INDEX ON atom_variants (profile_id);
CREATE INDEX ON atom_variants (atom_id) WHERE is_stale;

-- ─────────────────────────────── TAGS ───────────────────────────────

CREATE TABLE tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    label       TEXT NOT NULL,          -- canonical form
    UNIQUE (profile_id, label)
);

CREATE TABLE atom_tags (
    atom_id     UUID NOT NULL REFERENCES atoms(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    source      TEXT NOT NULL DEFAULT 'auto' CHECK (source IN ('auto','user')),
    PRIMARY KEY (atom_id, tag_id)
);
CREATE INDEX ON atom_tags (tag_id);

-- ─────────────────────── TEMPLATE CUSTOMIZATION ───────────────────────

CREATE TABLE template_customizations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id          UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name                TEXT NOT NULL,
    base_template_id    TEXT NOT NULL,          -- classic | modern | compact
    template_version    SMALLINT NOT NULL,      -- renderer version
    params              JSONB NOT NULL,         -- fontFamily, fontSizePt, marginIn, lineSpacing, accentColor, sections
    fixed_costs         JSONB,                  -- measured fixed costs
    page_text_height_pt REAL,
    measured_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (profile_id, name)
);

-- ─────────────────────────────── GENERATION ───────────────────────────────

CREATE TABLE generations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID REFERENCES users(id) ON DELETE CASCADE,
    profile_id            UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,

    job_description       TEXT,                 -- NULL = general CV mode
    jd_hash               TEXT,
    jd_analysis           JSONB,                -- phase A output
    directives            JSONB,                -- user directives
    options               JSONB NOT NULL,       -- template, maxPages, cvLanguage, coverLetterLanguage

    selection_state       JSONB NOT NULL,       -- phase C output (snapshot)
    content_snapshot      JSONB,                -- full text, when archived
    cover_letter          TEXT,

    fit_report            JSONB,                -- coverage counts
    page_count            SMALLINT,
    engine_version        JSONB NOT NULL,       -- pipeline, scoringWeights, template, promptVersions
    trace                 JSONB,                -- per-phase telemetry (no PII)

    status                TEXT NOT NULL,        -- completed | failed | superseded
    parent_generation_id  UUID REFERENCES generations(id) ON DELETE SET NULL,
    archived              BOOLEAN NOT NULL DEFAULT false,
    pdf_key               TEXT,                 -- R2 key
    pdf_expires_at        TIMESTAMPTZ,          -- 14 days (NULL when archived)
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

-- ─────────────────────── APPLICATION TRACKING ───────────────────────

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

-- ─────────────────────────────── QUEUE ───────────────────────────────

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

-- ──────────────────────── TELEMETRY AND QUOTA ────────────────────────

CREATE TABLE llm_invocations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- SET NULL rather than CASCADE: aggregate cost history survives account
    -- deletion, while the link to the deleted user is severed.
    job_id          UUID REFERENCES jobs(id) ON DELETE SET NULL,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
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
    -- NOTE: prompt/response CONTENT IS NEVER STORED
);
CREATE INDEX ON llm_invocations (created_at);
CREATE INDEX ON llm_invocations (user_id, created_at);

CREATE TABLE usage_counters (
    subject_type    TEXT NOT NULL,   -- user | ip | anon_session
    subject_id      TEXT NOT NULL,
    metric          TEXT NOT NULL,   -- generation | profile_extract | llm_cost
    period          DATE NOT NULL,
    count           INT NOT NULL DEFAULT 0,
    cost_usd        NUMERIC(10,6) NOT NULL DEFAULT 0,
    PRIMARY KEY (subject_type, subject_id, metric, period)
);

CREATE TABLE feature_flags (
    key         TEXT PRIMARY KEY,
    enabled     BOOLEAN NOT NULL DEFAULT true,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
