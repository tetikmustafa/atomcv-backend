# Bölüm XI-B — İki Repo, CLAUDE.md, Promptlar

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM XI-B — İKİ REPO YAPISI, KURULUM VE CLAUDE CODE İLE ÇALIŞMA

## XI-B.0 — Dil Politikası

| Alan | Dil | Gerekçe |
|---|---|---|
| **Kod, değişken/sınıf adları** | İngilizce | Standart pratik, açık kaynak hedefi |
| **Kod yorumları** | İngilizce | Aynı |
| **Commit mesajları** | İngilizce | Conventional Commits |
| **README, CLAUDE.md** | İngilizce | Repo'ya giren her şey |
| **Claude Code promptları** | İngilizce | Model performansı + tutarlılık |
| **UI metinleri (kaynak)** | İngilizce | i18n kaynak dili; TR çeviri olarak eklenir |
| **Mimari dokümanları** | Türkçe *(şimdilik)* | Geliştiricinin kişisel referansı |
| **Claude Code ile sohbet** | Türkçe | Tercih |

> **Açık kaynak notu:** Repo public yayınlanmadan önce (Aşama 4) mimari dokümanlarının İngilizceye çevrilmesi gerekir. Bu, ayrı bir görev olarak planlanmalıdır.

---

## XI-B.1 — Repo Ayrımı: Kararlar ve Sonuçları

### XI-B.1.1 Neden iki repo

| Fayda | Açıklama |
|---|---|
| Ayrı IDE'ler | IntelliJ (backend) ve VS Code (frontend) kendi kök klasörlerinde çalışır |
| Bağımsız CI süresi | Frontend değişikliği backend testlerini çalıştırmaz |
| Bağımsız sürümleme | Her repo kendi tempo'sunda ilerler |
| Claude Code odağı | Her oturumda yalnızca ilgili kod tabanı bağlamda olur |
| Ayrı erişim kontrolü | İleride katkıda bulunan olursa granüler yetki |

### XI-B.1.2 Ayrımın getirdiği sorumluluk dağılımı

| Sorumluluk | Hangi repo |
|---|---|
| Docker Compose (lokal + prod) | **Backend** — altyapının sahibi |
| Veritabanı şeması (Flyway) | **Backend** |
| LaTeX container tanımı | **Backend** |
| Nginx yapılandırması | **Backend** |
| Deploy script'leri | **Backend** |
| Yedekleme script'leri | **Backend** |
| OpenAPI şeması üretimi | **Backend** (yayınlar) |
| Üretilmiş TypeScript tipleri | **Frontend** (tüketir, commit eder) |
| i18n mesaj dosyaları | **Frontend** |
| Mimari dokümanları | **Her ikisi** (backend ana kaynak, frontend'e kopyalanır) |

### XI-B.1.3 Doküman senkronizasyonu

Dokümanlar her iki repo'da da `docs/` altında bulunmalıdır — Claude Code yalnızca kendi kök klasörünü okuyabilir.

**Ana kaynak: `atomcv-backend/docs/`**

```bash
# Doküman güncellendiğinde (backend repo'da)
cp docs/*.md ../atomcv-frontend/docs/
cd ../atomcv-frontend && git add docs/ && git commit -m "docs: sync architecture docs from backend"
```

Bunu bir script'e bağla: `atomcv-backend/scripts/sync-docs.sh`

---

## XI-B.2 — Backend Repo Klasör Yapısı

```
atomcv-backend/
├── .github/
│   └── workflows/
│       ├── ci.yml                          # build + test + security
│       ├── deploy.yml                       # main'e merge → GHCR → SSH deploy
│       └── secrets-scan.yml                 # gitleaks
│
├── docs/                                    # mimari dokümanları (ana kaynak)
│   ├── urun-konsept-dokumani-v2.md
│   └── teknik-mimari-dokumani.md
│
├── docker/
│   └── latex/
│       ├── Dockerfile                       # texlive-xetex + fontlar + format cache
│       ├── preamble.tex                     # önceden derlenen format dosyası
│       ├── fonts/                           # whitelist fontları
│       └── server/                          # HTTP wrapper (küçük Java/Go servis)
│
├── scripts/
│   ├── deploy.sh                            # sunucuda çalışır: pull + migrate + up + healthcheck
│   ├── backup.sh                            # pg_dump + age + rclone → R2
│   ├── restore-test.sh                      # aylık restore doğrulaması
│   ├── sync-docs.sh                         # dokümanları frontend repo'ya kopyala
│   └── measure-template.sh                  # yeni şablonun sabit maliyetlerini ölç
│
├── src/
│   ├── main/
│   │   ├── java/com/mustafatetik/atomcv/
│   │   │   ├── AtomCvApplication.java
│   │   │   │
│   │   │   ├── identity/                    # kimlik, oturum, hesap
│   │   │   │   ├── api/                     #   IdentityController, dto/
│   │   │   │   ├── domain/                  #   User, OAuthIdentity, MagicLinkToken
│   │   │   │   ├── service/                 #   AuthService, SessionService, OAuthService
│   │   │   │   └── repository/
│   │   │   │
│   │   │   ├── profile/                     # Master Profil
│   │   │   │   ├── api/
│   │   │   │   ├── domain/                  #   Profile, Section, Entry, Atom, AtomVariant
│   │   │   │   │   └── content/             #   RichContent, Run, Mark, ContentMigrator
│   │   │   │   ├── service/                 #   ProfileService, ProfileAssembler
│   │   │   │   └── repository/
│   │   │   │
│   │   │   ├── ingestion/                   # profil oluşturma
│   │   │   │   ├── extraction/              #   PdfExtractor, DocxExtractor, TexExtractor
│   │   │   │   ├── structuring/             #   LlmStructuringService
│   │   │   │   ├── normalization/           #   SkillNormalizer, DateParser, RunBuilder
│   │   │   │   └── github/                  #   GitHubImportService
│   │   │   │
│   │   │   ├── generation/                  # üretim hattı
│   │   │   │   ├── api/
│   │   │   │   ├── pipeline/                #   GenerationOrchestrator, PipelineContext,
│   │   │   │   │                            #   Result, PipelineError, PreflightGuard
│   │   │   │   ├── phases/
│   │   │   │   │   ├── JobAnalysisPhase.java        # Faz A
│   │   │   │   │   ├── ScoringPhase.java            # Faz B
│   │   │   │   │   ├── SelectionPhase.java          # Faz C
│   │   │   │   │   ├── RewritePhase.java            # Faz D
│   │   │   │   │   ├── RenderPhase.java             # Faz E
│   │   │   │   │   ├── VerificationPhase.java       # Faz F
│   │   │   │   │   └── EditPhase.java               # Faz G
│   │   │   │   ├── scoring/                 #   ScoringWeights, RelevanceScorer
│   │   │   │   ├── selection/               #   BinPacker, BudgetCalculator, SelectionBuilder
│   │   │   │   └── validation/              #   RewriteValidator, CoverLetterValidator
│   │   │   │
│   │   │   ├── rendering/
│   │   │   │   ├── model/                   #   RenderRequest, RenderableSection
│   │   │   │   ├── latex/                   #   LatexDocumentRenderer, LatexInlineRenderer,
│   │   │   │   │                            #   LatexEscaper, PreambleBuilder
│   │   │   │   ├── html/
│   │   │   │   ├── docx/
│   │   │   │   ├── measurement/             #   MeasurementDocumentBuilder, TexLogParser,
│   │   │   │   │                            #   FontMetricEstimator, CalibrationService
│   │   │   │   └── template/                #   TemplateRegistry, CapacityModel,
│   │   │   │                                #   TemplateCustomization, FontRegistry
│   │   │   │
│   │   │   ├── llm/
│   │   │   │   ├── gateway/                 #   LlmProvider, ProviderChain, StructuredRequest
│   │   │   │   ├── providers/               #   OpenRouter, Gemini, OpenAI, Anthropic, DeepSeek
│   │   │   │   ├── prompts/                 #   PromptRegistry, PromptTemplate
│   │   │   │   ├── fake/                    #   FakeLlmProvider (local-fake/record)
│   │   │   │   └── telemetry/               #   LlmInvocationRecorder
│   │   │   │
│   │   │   ├── embedding/                   #   EmbeddingProvider, TeiClient, FakeEmbedding
│   │   │   ├── compilation/                 #   LatexCompilerClient, CompilationSemaphore
│   │   │   │
│   │   │   ├── jobs/
│   │   │   │   ├── queue/                   #   JobRepository, JobClaimer, StaleReclaimer
│   │   │   │   ├── workers/                 #   GenerationWorker, MeasurementWorker,
│   │   │   │   │                            #   TranslationWorker, EmbeddingWorker, EmailWorker
│   │   │   │   └── sse/                     #   SseRegistry, ProgressPublisher
│   │   │   │
│   │   │   ├── tracking/                    # başvuru takibi
│   │   │   ├── billing/                     #   QuotaService, CostTracker, AnomalyDetector,
│   │   │   │                                #   KillSwitch
│   │   │   ├── email/                       #   ResendClient, EmailTemplateRenderer, Suppression
│   │   │   │
│   │   │   └── shared/
│   │   │       ├── security/                #   UserContext, UserRole, UserOwned, ProfileOwned,
│   │   │       │                            #   ProfileRef, UserScopedRepository,
│   │   │       │                            #   ProfileScopedRepository, CsrfConfig
│   │   │       ├── error/                   #   ErrorPresenter, ProblemDetailAdvice
│   │   │       ├── config/
│   │   │       └── util/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-prod.yml
│   │       ├── db/migration/                #   V1__initial_schema.sql, V2__...
│   │       ├── prompts/                     #   versiyonlu prompt dosyaları
│   │       │   ├── job_analysis/{v1.md, schema.json}
│   │       │   ├── profile_extraction/{v1.md, schema.json}
│   │       │   ├── atom_rewrite/v1.md
│   │       │   ├── about_synthesis/v1.md
│   │       │   ├── cover_letter/v1.md
│   │       │   ├── edit_intent/v1.md
│   │       │   └── translation/v1.md
│   │       ├── templates/                   #   şablon config + preamble parçaları
│   │       │   ├── templates.yaml           #   kapasite + sabit maliyetler
│   │       │   ├── classic/
│   │       │   ├── modern/
│   │       │   └── compact/
│   │       ├── email/                       #   Thymeleaf şablonları
│   │       ├── skills/aliases.json          #   beceri normalizasyon sözlüğü
│   │       └── seeds/                       #   dev seed profilleri + ölçüm cache'leri
│   │
│   └── test/
│       ├── java/com/mustafatetik/atomcv/
│       │   ├── architecture/                #   ArchUnit kuralları
│       │   ├── pipeline/                    #   faz testleri
│       │   ├── security/                    #   multi-tenant izolasyon testleri
│       │   └── ...
│       └── resources/
│           ├── golden/                      #   profiles/, jobs/, analyses/, content-formats/
│           └── fixtures/llm/                #   local-record ile üretilen yanıtlar
│
├── .env.example
├── .gitignore
├── CLAUDE.md                                # ← Claude Code kalıcı bağlamı
├── README.md
├── CHANGELOG.md
├── SECURITY.md
├── LICENSE                                  # MIT
├── Makefile
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml                       # lokal (core/full profilleri)
├── docker-compose.prod.yml                  # üretim (frontend imajını da içerir)
└── nginx/
    ├── nginx.conf
    └── proxy_params.conf
```

---

## XI-B.3 — Frontend Repo Klasör Yapısı

> **Not (Frontend Aşama 0).** Aşağıdaki ağaç kurulmuş hâliyle güncellendi;
> yaşayan sürüm frontend reposundaki dizinin kendisidir. Sapmaların gerekçeleri
> **EK D.10**'da: `middleware.ts` yerine `proxy.ts` (Next 16), Tailwind v4'te
> `tailwind.config.ts` olmaması, `src/app/api/`'nin hiç oluşturulmaması,
> `legal/`'in `[locale]` altına taşınması ve bundle bütçesi için kendi
> script'imiz.

```
atomcv-frontend/
├── .github/workflows/
│   ├── ci.yml                               # typecheck + lint + test + build + bundle butcesi
│   └── secrets-scan.yml                     # deploy.yml Asama 1 sonrasina ertelendi (Bolum 55)
│
├── docs/                                    # backend'den senkronize (salt-okunur kopya)
│   ├── urun-konsept-dokumani-v2.md
│   └── teknik-mimari-dokumani.md
│
├── public/
│   ├── favicon.ico
│   └── og-image.png
│
├── src/
│   ├── app/
│   │   ├── [locale]/
│   │   │   ├── layout.tsx
│   │   │   ├── page.tsx                     # landing (SSG)
│   │   │   │
│   │   │   ├── (auth)/
│   │   │   │   ├── login/page.tsx
│   │   │   │   └── verify/page.tsx          # magic link onay (POST ile doğrular)
│   │   │   │
│   │   │   └── (app)/
│   │   │       ├── layout.tsx               # auth guard + app shell
│   │   │       ├── onboarding/              # profil kurulum sihirbazı
│   │   │       │   ├── page.tsx
│   │   │       │   ├── upload/
│   │   │       │   ├── review/              # zorunlu gözden geçirme ekranı
│   │   │       │   └── manual/
│   │   │       ├── profile/
│   │   │       │   ├── page.tsx             # profil editörü
│   │   │       │   └── settings/
│   │   │       ├── generate/page.tsx        # ilan girişi + seçenekler
│   │   │       ├── generations/
│   │   │       │   ├── page.tsx             # geçmiş
│   │   │       │   └── [id]/page.tsx        # sonuç ekranı
│   │   │       ├── applications/page.tsx    # başvuru takibi
│   │   │       ├── account/page.tsx
│   │   │       └── dev/mocks/page.tsx       # dev-only dogrulama; uretimde notFound()
│   │   │
│   │   │   └── legal/                       # [locale] ALTINDA: cevrilebilir olmali
│   │   │       ├── privacy/page.tsx
│   │   │       └── terms/page.tsx
│   │   │
│   │   │   # app/api/ YOK ve olmayacak — rewrite ile ayni-origin (EK D.10 · 20)
│   │
│   ├── components/
│   │   ├── ui/                              # shadcn/ui (uretilen, --base radix)
│   │   ├── providers/                       # AppProviders, MockProvider
│   │   ├── layout/                          # SkipLink, Announcer, AppShell, SiteFooter, LegalDocument
│   │   ├── profile/
│   │   │   ├── AtomEditor.tsx               # memo'lu, granüler query
│   │   │   ├── SectionList.tsx              # dnd-kit sıralama
│   │   │   ├── VariantTabs.tsx              # dil varyantları + stale uyarısı
│   │   │   ├── TagInput.tsx
│   │   │   ├── ImportanceSlider.tsx
│   │   │   ├── LockToggles.tsx              # alwaysInclude + verbatim
│   │   │   └── CompletenessBar.tsx
│   │   ├── generation/
│   │   │   ├── JobDescriptionInput.tsx
│   │   │   ├── GenerationOptions.tsx
│   │   │   ├── ProgressStream.tsx           # SSE + aria-live
│   │   │   ├── FitReport.tsx                # kapsama sayıları (yüzde DEĞİL)
│   │   │   ├── SelectionExplainer.tsx       # neden seçildi
│   │   │   ├── RejectedAtomsList.tsx        # tek tıkla ekle
│   │   │   ├── NaturalLanguageEdit.tsx      # Faz G
│   │   │   └── CoverLetterPanel.tsx
│   │   ├── preview/
│   │   │   ├── PdfPreview.tsx               # dynamic import
│   │   │   └── DiffViewer.tsx               # dynamic import
│   │   └── feedback/
│   │       ├── ErrorPanel.tsx               # RFC 7807 + resolutions → butonlar
│   │       └── FeedbackWidget.tsx           # 👍/👎
│   │
│   ├── hooks/
│   │   ├── useProfile.ts
│   │   ├── useAutosave.ts                   # debounce + optimistic + ETag
│   │   ├── useJobStream.ts                  # SSE
│   │   ├── useCapabilities.ts               # anonim/hesaplı yetenekler
│   │   └── useGeneration.ts
│   │
│   ├── proxy.ts                             # locale yonlendirmesi (Next 16'da middleware.ts yerine)
│   │
│   ├── lib/
│   │   ├── i18n/                            # routing.ts, request.ts, navigation.ts, locales.ts
│   │   ├── api/
│   │   │   ├── client.ts                    # fetch wrapper + credentials + CSRF
│   │   │   ├── errors.ts                    # ProblemDetail parse + resolution mapping
│   │   │   └── endpoints/                   # profile.ts, generation.ts, auth.ts...
│   │   ├── content/
│   │   │   ├── richContent.ts               # Run/Mark tipleri + yardımcılar
│   │   │   └── plainText.ts
│   │   └── utils.ts                         # klasor degil dosya: shadcn o yolu bekliyor
│   │
│   ├── stores/                              # Zustand — SADECE geçici UI durumu
│   │   ├── editorUiStore.ts                 # açık bölümler, seçili atom
│   │   └── wizardStore.ts                   # onboarding adımı
│   │
│   ├── types/
│   │   ├── api.d.ts                         # ⚠️ ÜRETİLEN — elle düzenleme yasak
│   │   └── domain.ts                        # frontend'e özel tipler
│   │
│   ├── messages/                            # i18n
│   │   ├── en.json                          # kaynak dil
│   │   └── tr.json
│   │
│   ├── mocks/                               # MSW — backend hazır olmadan geliştirme
│   │   ├── handlers.ts
│   │   ├── browser.ts
│   │   ├── node.ts                          # Vitest icin ayni handler'lar
│   │   └── contracts.ts                     # ISKELE — uc yayinlandikca tip tip bosalir (EK D.6.4)
│   │
│   └── styles/globals.css
│
├── scripts/check-bundle-size.mjs            # prerender edilen her rotanin script etiketlerini okur
├── bundle-budget.json                       # sharedKb / perRouteOwnKb / totalKb
│
├── tests/
│   ├── setup.ts
│   ├── unit/
│   └── e2e/                                 # Playwright, `next dev`'e karsi, port 3100
│
├── .env.example
├── .env.local                               # git'te DEĞİL
├── .gitignore
├── .gitattributes
├── .dockerignore
├── vitest.config.mts
├── AGENTS.md                                # `next dev` uretiyor, o yuzden commit'li
├── CLAUDE.md                                # ← Claude Code kalıcı bağlamı
├── README.md
├── LICENSE                                  # MIT
├── next.config.ts                           # create-next-app tipli config uretiyor
├── tsconfig.json                            # tailwind.config.ts YOK: Tailwind v4 CSS-first
├── package.json
├── playwright.config.ts
└── Dockerfile
```

---

## XI-B.4 — Backend `CLAUDE.md`

> Bu dosya `atomcv-backend/CLAUDE.md` olarak kaydedilir. Claude Code her oturumda otomatik okur.
>
> **Aşağıdaki metin yalnızca ilk halidir.** Yaşayan sürüm repodaki `CLAUDE.md`
> dosyasıdır ve o gün itibarıyla çözülmüş kararları, makineye özgü notları,
> aşama durumunu ve çalışma düzenini taşır. İkisi ayrıştığında repodaki
> dosya geçerlidir; burayı her değişiklikte güncellemek iki kopyayı da
> güvenilmez yapar.

````markdown
# AtomCV Backend — Working Context

## What This Project Is

AtomCV lets a user build a structured "Master Profile" once, then generate
job-specific, ATS-optimized resumes and cover letters in seconds — with a
**mathematically guaranteed page limit** and **structural protection against
fabricated content**.

The core architectural insight: a person's professional history is not a CV
file — it is a **structured dataset**. A CV is a transient *view* rendered
from that data.

This repository contains **only the backend**. The frontend lives in a
separate repository (`atomcv-frontend`, Next.js). Never add frontend code here.

## Architecture Documents

Full specifications live in `docs/`. They are written in Turkish.

| Document | Contents |
|---|---|
| `docs/urun-konsept-dokumani-v2.md` | Product concept, user journeys, scenarios |
| `docs/teknik-mimari-dokumani.md` | All technical decisions, schema, algorithms |

**Do not read both documents in full every session.** Use this map to read
only what the current task needs:

| Task | Read section |
|---|---|
| Any task (first session) | Bölüm 4 (design principles) |
| Module placement | Bölüm 10 |
| Database work | Bölüm 13, 14, 15, 16 |
| Pipeline phase A (job analysis) | Bölüm 18 |
| Pipeline phase B (scoring) | Bölüm 19 |
| Pipeline phase C (selection) | Bölüm 20 |
| Pipeline phase D (rewriting) | Bölüm 21 |
| Pipeline phase E (rendering) | Bölüm 22 |
| Pipeline phase F (verification) | Bölüm 23 |
| Pipeline phase G (editing loop) | Bölüm 24 |
| Result type / error hierarchy | Bölüm 25 |
| Render cost measurement | Bölüm 26 |
| LLM gateway | Bölüm 27 |
| Embeddings | Bölüm 28 |
| LaTeX container | Bölüm 29 |
| Job queue / SSE | Bölüm 30 |
| CV upload / profile extraction | Bölüm 31 |
| Multilingual atoms | Bölüm 32 |
| Templates / customization | Bölüm 33 |
| Cover letter | Bölüm 34 |
| API contract | Bölüm 35 |
| Auth / session | Bölüm 40 |
| Multi-tenant isolation | Bölüm 41 |
| Input security / injection | Bölüm 42, 43 |
| Quota / cost control | Bölüm 44 |
| Deployment / server | Bölüm 46 |
| CI/CD | Bölüm 47 |
| Observability | Bölüm 48 |
| Testing | Bölüm 51 |
| Performance budgets | Bölüm 52 |
| Prompt management | Bölüm 53 |
| Step-by-step build guide | Bölüm XI-A |
| Repo structure / prompts | Bölüm XI-B |

## The Eight Design Principles

Every decision in this codebase traces back to one of these. When facing a
design question, consult these first.

1. **Separate content from presentation.** No format-specific markup in the
   data model. Emphasis is semantic (`technology`, `metric`), converted to
   `\textbf{}` / `<strong>` / bold-run only at render time.
2. **Do not use an LLM where determinism is possible.** Scoring, selection,
   rendering and validation are pure code. LLMs only handle language
   understanding and language generation.
3. **Prevent fabrication structurally, not by asking.** Scope limit + task
   limit + automated validation.
4. **Never produce a silently bad result.** Explain the problem, state the
   cause, offer concrete options, let the user decide.
5. **Run checks before incurring cost.** All validation happens before any
   LLM call.
6. **Edits apply to selection state, never to rendered output.**
7. **Transparency.** Every selection decision is explainable to the user.
8. **Never silently overwrite user's own work.** Ask instead.

## Tech Stack

- Java 21 (virtual threads, sealed interfaces, records, pattern matching)
- Spring Boot 3.x (Web MVC, Data JPA, Security, Actuator)
- PostgreSQL 17 + pgvector
- Flyway (schema migrations)
- Redis (session, cache, ephemeral profiles, rate limit counters)
- XeLaTeX in an isolated container (PDF rendering)
- BGE-M3 self-hosted (embeddings, via text-embeddings-inference)
- Testcontainers, JUnit 5, ArchUnit

## Module Map

```
identity/    auth, session, account
profile/     Master Profile: Section > Entry > Atom > AtomVariant
ingestion/   CV upload, extraction, structuring, GitHub import
generation/  pipeline (phases A-G), scoring, selection, validation
rendering/   LaTeX/HTML/DOCX renderers, measurement, templates
llm/         provider gateway, prompt registry, telemetry
embedding/   embedding provider abstraction
compilation/ LaTeX compiler client
jobs/        queue, workers, SSE
tracking/    application tracking
billing/     quota, cost tracking, anomaly detection, kill switch
email/       Resend client, templates, suppression list
shared/      user-scoped repository base, error presentation, config
```

Modules communicate only through public interfaces. No cyclic dependencies
(enforced by ArchUnit). `shared/` must not depend on any business module.

## Absolute Rules — Never Violate

1. **`spring.jpa.hibernate.ddl-auto` is always `validate`.** Never `update`,
   never `create`. Schema is owned solely by Flyway.
2. **Never modify an applied Flyway migration.** Write a new one.
3. **All data access goes through `UserScopedRepository`.** Never call a raw
   `JpaRepository` from a controller or service that handles user data. This
   is the IDOR defense and it is enforced by ArchUnit.
4. **Never log user content.** No `RichContent`, no atom text, no job
   description, no email body. Log `ContentShape` (statistics) instead.
5. **Never put secrets in code.** Environment variables only.
6. **The rendering module must never depend on the llm module.** Rendering is
   deterministic by design.
7. **Never call `String.toLowerCase()` / `toUpperCase()` without a locale**
   for identity or matching operations — use `Locale.ROOT`. Turkish locale
   turns "SQL" into "sqı" and silently breaks skill matching.
8. **LaTeX compilation always uses `-no-shell-escape`** and runs in the
   isolated container.
9. **Never let the LLM produce LaTeX.** Renderers produce LaTeX; LLMs produce
   plain text only.
10. **Page budget is measured in points (pt), never in lines.** Rounding to
    whole lines accumulates error.

## Development Commands

```bash
make dev        # core services (postgres, redis, mailpit) + backend with fake LLM
make dev-full   # also starts latex + embeddings containers
make db-reset   # wipe database and re-run migrations (LOCAL ONLY)
make record     # run with local-record profile to capture LLM fixtures
make test       # unit + architecture tests
make test-int   # integration tests (Testcontainers)
```

Spring profiles:
- `local,local-fake` — default for daily work; no real LLM calls, no cost
- `local,local-record` — real LLM calls, responses saved as fixtures
- `local,local-real` — real LLM calls, nothing saved (prompt work)
- `prod` — production

## Testing Requirements

Write these tests alongside the code they cover, not afterwards:

| Test | Guards |
|---|---|
| Page limit never exceeded | The product's core promise |
| Determinism (same input → same output, 50 runs) | Phase B and C purity |
| Multi-tenant isolation (every protected endpoint) | Data leakage |
| Locks and structural constraints respected | User control guarantees |
| Anonymous flow writes nothing to database | Privacy claim |
| Profile load uses ≤6 queries | N+1 regression |

## Code Style

- Code, comments, commit messages, and identifiers: **English**
- Conversation with the developer: **Turkish**
- Commit format: Conventional Commits (`feat(scope):`, `fix(scope):`, ...)
- Prefer records for value objects, sealed interfaces for closed hierarchies
- Prefer `Result<T>` over exceptions for expected failure paths

## How We Work Together

1. **Apply the documented decisions as written.** If you disagree with a
   decision, or find something missing or contradictory in the documents,
   raise it *before* implementing — never silently deviate.
2. **Work in small steps.** State what you are about to do, wait for
   approval, then do it. Do not create twenty files in one turn.
3. **Ask when the documents are ambiguous.** A wrong assumption baked into an
   early layer is expensive to remove.
4. **Update this file** when we make a decision that future sessions need to
   know.

## Current Stage

<!-- Update this section as work progresses -->
**Stage 0 — Skeleton.** Setting up project structure, Docker Compose,
Flyway baseline, health endpoint, CI pipeline.

Next: Stage 1 (Walking Skeleton) — domain model, manual profile CRUD,
LaTeX container, measurement system, selection algorithm, PDF output.
No LLM in Stage 1.
````

---

## XI-B.5 — Frontend `CLAUDE.md`

> Bu dosya `atomcv-frontend/CLAUDE.md` olarak kaydedilir.

````markdown
# AtomCV Frontend — Working Context

## What This Project Is

AtomCV lets a user build a structured "Master Profile" once, then generate
job-specific, ATS-optimized resumes and cover letters in seconds.

This repository contains **only the frontend**. The backend lives in a
separate repository (`atomcv-backend`, Java + Spring Boot). All business
logic belongs there.

During local development the backend runs at `http://localhost:8080`.

## Architecture Documents

Full specifications live in `docs/` (Turkish). These are a **read-only copy**
synced from the backend repository — never edit them here.

| Task | Read section |
|---|---|
| Any task (first session) | Bölüm 4 (design principles), Bölüm 9 (user journey) |
| Understanding the product | Bölüm I-II, Bölüm IV (scenarios) |
| API integration | Bölüm 35 |
| Frontend architecture | Bölüm 36 |
| Profile editor behavior | Bölüm 37 |
| i18n | Bölüm 38 |
| Accessibility | Bölüm 39 |
| Error screens and edge cases | Bölüm 11 |
| Anonymous mode capabilities | Bölüm 9 (Aşama 0), Bölüm 35.7 |
| Performance budgets | Bölüm 52.3 |
| Folder structure | Bölüm XI-B.3 |

## Critical Architecture Rule

**No BFF, and no `src/app/api/` at all.**

Next.js is a presentation layer only. The same-origin illusion is a rewrite in
`next.config.ts`, which runs none of our code — so the rule cannot be broken by
accident (EK D.10 · 20). If you think you need an API route, ask first.

## Tech Stack

- Next.js 16 (App Router, Turbopack), React 19, TypeScript (strict)
- Tailwind CSS + shadcn/ui (Radix primitives — accessibility comes free)
- TanStack Query — **server state**
- Zustand — **transient UI state only** (open sections, selected atom)
- React Hook Form + Zod — forms
- next-intl — i18n with ICU MessageFormat
- dnd-kit — drag-and-drop with keyboard sensor
- MSW — mocking while backend endpoints are not ready

## API Types Are Generated, Not Written

```bash
npm run gen:api      # requires backend running at localhost:8080
```

This regenerates `src/types/api.d.ts` from the backend's OpenAPI schema.
**The generated file is committed** so the frontend builds without the
backend running.

**Never hand-write types that mirror backend DTOs.** That is a
synchronization bug waiting to happen.

## Absolute Rules — Never Violate

1. **No business logic in `src/app/api/`.**
2. **Never hand-edit `src/types/api.d.ts`.** Regenerate it.
3. **Server data lives in TanStack Query, not Zustand.** Never copy server
   state into a client store — two sources of truth create drift.
4. **Heavy components are lazily loaded** via `next/dynamic` with
   `ssr: false`: `react-pdf`, diff viewer, rich text editor. Initial JS
   bundle must stay under 200 KB gzipped.
5. **Every interactive element must be keyboard accessible.** Drag-and-drop
   needs both a keyboard sensor and explicit "move up / move down" buttons.
6. **Progress and save status must be announced** via `aria-live` regions,
   not conveyed by color or icon alone.
7. **Error responses follow RFC 7807 with a `resolutions` array.** Render
   those resolutions as buttons — do not hardcode error UI per error type.
8. **The server sends translation keys, not translated text.** Resolve
   `errors.{code}` through next-intl.
9. **Never use `Intl`-less date/number formatting.** Dates inside a generated
   CV follow the *content* language, not the UI language.
10. **Session cookie is HttpOnly** — the frontend never reads or writes auth
    tokens in JavaScript. All API calls use `credentials: 'include'`.

## Product Behaviors That Are Easy to Get Wrong

- **Manual control is optional, not required.** The default output must be
  usable without the user touching anything. Do not force a review step
  after generation.
- **The fit report shows countable facts, never a percentage.**
  "Required skills 4/4" — not "87% match". Percentages imply false precision.
- **When the profile is too thin, the CV may be shorter than one page.**
  This is correct behavior. Never pad. Show an informational note.
- **Anonymous mode is fully functional**, only narrower in scope: English
  only, preset templates, no customization, no history. Quality is never
  reduced.
- **The one screen that cannot be skipped** is the post-extraction review
  screen. Automatic extraction is never 100% accurate; an error that slips
  through silently propagates into every future CV.

## Development Commands

```bash
npm run dev          # localhost:3000
npm run gen:api      # regenerate API types (backend must be running)
npm run typecheck
npm run lint
npm test             # Vitest
npm run test:e2e     # Playwright
npm run build
npm run check:bundle-size   # rota rota bundle butcesi
```

## Code Style

- Code, comments, commit messages, identifiers: **English**
- Conversation with the developer: **Turkish**
- UI source strings: **English** in `messages/en.json`; Turkish is a
  translation in `messages/tr.json`
- Commit format: Conventional Commits
- Prefer server components where possible; `'use client'` only when needed

## How We Work Together

1. **Apply the documented decisions as written.** Raise disagreements or gaps
   *before* implementing.
2. **Work in small steps** with approval between them.
3. **Ask when ambiguous** rather than assuming.
4. **Update this file** when we make decisions future sessions need.

## Current Stage

<!-- Update this section as work progresses -->
**Stage 0 — Skeleton.** Next.js setup, Tailwind, shadcn/ui, folder structure,
base layout, i18n scaffolding, CI pipeline. Backend not yet available —
using MSW mocks.
````

---

## XI-B.6 — Backend Bootstrap Prompt (İlk Oturum)

> IntelliJ IDEA'da `atomcv-backend` klasörünü aç, terminalde `claude` çalıştır, aşağıdakini yapıştır.

```
# Role and Context

You are helping me build the backend of AtomCV, a resume tailoring platform.
I am the sole developer. This is a greenfield project with a complete
architecture specification already written.

Your role: implement the specification faithfully, in small reviewable steps,
raising concerns before deviating rather than after.

# Step 1 — Read and Confirm Understanding

Two specification documents exist in `docs/`. They are written in Turkish.

- `docs/urun-konsept-dokumani-v2.md` — product concept, user journeys, scenarios
- `docs/teknik-mimari-dokumani.md` — technical decisions, schema, algorithms

Read these sections now, in this order:

1. Bölüm 1-4 — what the product is, the problem it solves, lessons from the
   previous-generation system, and the eight design principles
2. Bölüm 5-8 — technology choices with rationale, design patterns,
   algorithms, and rejected alternatives
3. Bölüm 10 — backend module structure
4. Bölüm XI-A.2 — the Stage 0 step-by-step guide
5. Bölüm XI-B.2 and XI-B.4 — this repository's folder structure and the
   CLAUDE.md contents you will create

Then produce, in Turkish:

**A. Understanding check (max 250 words)**
Explain in your own words: what this product does, and what the single most
important architectural idea is. Do not quote the document — paraphrase.
I need to verify you understood, not that you can copy.

**B. Concerns list**
List anything in the specification that seems ambiguous, contradictory, or
technically questionable to you. If nothing, say so explicitly. Do not
invent concerns to seem thorough.

**C. Stage 0 task breakdown**
List the concrete tasks for Stage 0, in dependency order, with a one-line
description each. Mark which ones you can do autonomously and which need
input or decisions from me.

Stop after producing A, B, and C. Do not write any code yet.

# Step 2 — After My Approval

Once I confirm your understanding is correct, your first action will be to
create `CLAUDE.md` at the repository root, using the content specified in
Bölüm XI-B.4 of the technical document. Adapt it if you found genuine
improvements, but tell me what you changed and why.

Then we proceed through Stage 0 tasks one at a time.

# Working Agreement

- Implement documented decisions as written. If you disagree, say so before
  implementing, not after.
- Small steps. State what you will do, wait for my approval, then do it.
  Never create many files in a single turn.
- Ask when the specification is ambiguous rather than guessing.
- Write tests alongside the code they cover, not later.
- Code, comments, commit messages, and identifiers in English.
- Talk to me in Turkish.

# Scope Boundary

This repository contains **only the backend**. The frontend is a separate
repository using Next.js. Never create frontend code here. If a task seems
to require frontend work, tell me and we will handle it in the other
repository.
```

---

## XI-B.7 — Frontend Bootstrap Prompt (İlk Oturum)

> VS Code'da `atomcv-frontend` klasörünü aç, terminalde `claude` çalıştır.

```
# Role and Context

You are helping me build the frontend of AtomCV, a resume tailoring platform.
I am the sole developer. A complete architecture specification already exists.

The backend is a separate repository (Java + Spring Boot) and is not yet
running. We will build the frontend against mocks first.

Your role: implement the specification faithfully, in small reviewable steps,
raising concerns before deviating rather than after.

# Step 1 — Read and Confirm Understanding

Two specification documents exist in `docs/` (Turkish). They are a read-only
copy synced from the backend repository — never edit them here.

Read these sections now, in this order:

1. Bölüm 1-4 — what the product is and the eight design principles
2. Bölüm 9 — the full user journey, stage by stage
3. Bölüm IV (Bölüm 13-16) — four detailed end-to-end scenarios
4. Bölüm 11 — edge case handling and the exact user-facing messages
5. Bölüm 35 — the API contract
6. Bölüm 36-39 — frontend architecture, profile editor, i18n, accessibility
7. Bölüm XI-B.3 and XI-B.5 — this repository's folder structure and the
   CLAUDE.md contents you will create

Then produce, in Turkish:

**A. Understanding check (max 250 words)**
Explain in your own words: what this product does, and what the frontend's
specific responsibility is within the system. Paraphrase, do not quote.

**B. Product behaviors you consider easy to get wrong**
From the scenarios and edge cases, identify the three UI behaviors most
likely to be implemented incorrectly by someone who skimmed the spec.
Explain why each is subtle.

**C. Concerns list**
Anything ambiguous, contradictory, or technically questionable. If nothing,
say so explicitly.

**D. Stage 0 task breakdown**
Concrete tasks in dependency order. Additionally, recommend a mocking
strategy for developing against the not-yet-existing backend, and justify
your choice against at least one alternative.

Stop after producing A through D. Do not write any code yet.

# Step 2 — After My Approval

Your first action will be to create `CLAUDE.md` at the repository root using
the content specified in Bölüm XI-B.5. Tell me about any changes you make.

Then we proceed through Stage 0 tasks one at a time.

# Working Agreement

- Implement documented decisions as written. Raise disagreements before
  implementing.
- Small steps with approval between them.
- Ask when ambiguous rather than guessing.
- Accessibility from the start, not retrofitted.
- Code, comments, commit messages, and identifiers in English.
- UI source strings in English (`messages/en.json`); Turkish is a translation.
- Talk to me in Turkish.

# Scope Boundary

This repository contains **only the frontend**. All business logic belongs to
the backend. Specifically: do not put logic in `src/app/api/` — Next.js is a
presentation layer here, not a BFF. If something seems to require an API
route, ask me first.
```

---

## XI-B.8 — Devam Eden Oturumlar İçin Prompt Şablonları

`CLAUDE.md` kalıcı bağlamı sağladığı için sonraki oturumlar çok daha kısa olabilir. Yine de yapı korunmalıdır.

### Yeni bir özellik/faz başlatma

```
# Task: Implement <feature name>

## Context to load
Read Bölüm <N> of docs/teknik-mimari-dokumani.md before starting.
<If relevant: Also read Bölüm <M> for <reason>.>

## What I want
<One or two sentences describing the goal in your own words.>

## Before writing code
1. Summarize the specification for this feature in 3-5 bullet points, so I
   can confirm you read the right section.
2. List the files you will create or modify.
3. Identify which tests are required for this feature according to
   Bölüm 51.2, and state that you will write them alongside.

Wait for my approval before implementing.

## Constraints specific to this task
<Anything unusual — e.g. "This must not add any new dependency",
"This must stay under the 40ms budget in performance-budgets.yaml">
```

### Hata ayıklama

```
# Task: Debug <symptom>

## Observed behavior
<What actually happens, with exact error text or logs if available.>

## Expected behavior
<What should happen, and which section of the spec says so.>

## What I have already checked
<Rule out obvious causes so we don't waste turns.>

## How to approach this
Form at least two competing hypotheses before proposing a fix. For each,
state what evidence would confirm or eliminate it. Then tell me which
diagnostic to run first.

Do not change code until we have identified the cause.
```

### Kod inceleme

```
# Task: Review <file or module>

Review against these criteria, in priority order:

1. Violations of the absolute rules in CLAUDE.md
2. Violations of the eight design principles (Bölüm 4)
3. Security issues, especially multi-tenant isolation and input handling
4. Missing tests that Bölüm 51.2 requires
5. Deviations from the documented specification
6. Code quality and readability

For each finding: state severity (blocker / should-fix / nitpick), the
specific line or block, why it matters, and the concrete fix.

Do not fix anything yet — produce the review first.
```

### Refactor

```
# Task: Refactor <what>

## Motivation
<Why this needs to change — what problem it causes today.>

## Constraint
Behavior must not change. All existing tests must still pass without
modification. If a test must change, that means behavior changed — stop
and tell me.

## Before starting
Describe the current structure, the target structure, and the sequence of
steps to get there safely. Identify any step where the code would be
temporarily broken, and how to avoid it.
```

### Prompt üzerinde çalışma (LLM promptları)

```
# Task: Improve prompt <prompt_id>

Read Bölüm 53 (prompt management) and Bölüm <N> for this phase's contract.

## Current problem
<What the current prompt does poorly, with a concrete example if possible.>

## Rules
- Create a NEW version file (vN+1.md). Never edit an applied version.
- The output schema must not change unless we agree to change the consuming
  code in the same commit.
- After writing, list what could regress, and which eval metrics from
  Bölüm 53.5 would catch it.
```

---

## XI-B.9 — Repolar Arası Koordinasyon

### XI-B.9.1 API sözleşmesi değiştiğinde

```
1. Backend: endpoint/DTO değişikliği yapılır
2. Backend: `./gradlew generateOpenApiDocs` → şema güncellenir
3. Backend: commit + push (CI şema değişikliğini uyarı olarak işaretler)
4. Frontend: backend'i lokalde çalıştır → `npm run gen:api`
5. Frontend: TypeScript hataları yeni sözleşmeyi gösterir → düzelt
6. Frontend: `src/types/api.d.ts` ile birlikte commit
```

**Kırıcı değişiklik yapılıyorsa:** Backend'de eski ve yeni alanı bir süre birlikte döndür (expand-contract), frontend geçtikten sonra eskisini kaldır.

### XI-B.9.2 Geliştirme sırası

Backend'i **bir adım önde** tut:

| Sıra | Backend | Frontend |
|---|---|---|
| 1 | Aşama 0: şema + health | — |
| 2 | — | Aşama 0: iskelet + layout (MSW ile) |
| 3 | Adım 1.1-1.2: domain + profil CRUD | — |
| 4 | — | `gen:api` + profil formu (gerçek API) |
| 5 | Adım 1.3-1.8: LaTeX, ölçüm, seçim, render | — |
| 6 | — | Üretim akışı + sonuç ekranı |
| 7 | Aşama 2: LLM + Faz A/B + kuyruk + SSE | — |
| 8 | — | SSE ilerleme + uygunluk raporu |

> **Düzeltme (EK D.6.4).** 4. adım "`gen:api` çalışınca `src/mocks/contracts.ts`
> silinir" diyordu. Silinemez: yayımlanan şema Aşama 1'dir — on beş yol, profil
> CRUD ve senkron `/generations/general` — mock'ların kapsadığı her uç
> (`GET /auth/session`, asenkron `POST /generations`, `GET /jobs/{id}`,
> `.../stream`) Aşama 2 ya da 3'tür. Dosyayı silmek bir kopyayı kaldırmaz,
> mock'ları tipsiz bırakır.
>
> Doğru kural daha dar ve denetlenebilir: **dosya uç uca boşalır, tip tip** —
> *şemanın zaten taşıdığı bir ucu `contracts.ts` tarif edemez.* Hata zarfı bu
> kuralla çoktan taşındı.

### XI-B.9.3 Ortak deploy

`docker-compose.prod.yml` backend reposunda yaşar ve **her iki imajı** referans eder:

```yaml
services:
  backend:
    image: ghcr.io/tetikmustafa/atomcv-backend:${BACKEND_SHA}
  frontend:
    image: ghcr.io/tetikmustafa/atomcv-frontend:${FRONTEND_SHA}
```

Her repo kendi deploy workflow'unda yalnızca **kendi SHA'sını** günceller:

```bash
# scripts/deploy.sh (backend reposunda)
./deploy.sh backend <sha>    # sadece BACKEND_SHA'yı günceller
./deploy.sh frontend <sha>   # sadece FRONTEND_SHA'yı günceller
```

Sunucuda `.env.deploy` dosyasında iki SHA saklanır; rollback her biri için bağımsız yapılabilir.

---

## XI-B.10 — İlk Gün Kontrol Listesi

### Backend

```
□ mkdir atomcv-backend && cd atomcv-backend && git init
□ docs/ klasörüne iki dokümanı kopyala
□ .gitignore ve .env.example oluştur
□ gh repo create atomcv-backend --public --source=. --remote=origin
□ İlk commit + push
□ gitleaks pre-commit hook kur
□ IntelliJ IDEA'da klasörü aç
□ Terminalde `claude` başlat
□ XI-B.6'daki bootstrap promptu yapıştır
□ Claude'un özet + endişeler + plan çıktısını değerlendir
□ Onayla → CLAUDE.md oluşturulsun
□ Aşama 0 Adım 0.1'den başla
```

### Frontend

```
□ mkdir atomcv-frontend && cd atomcv-frontend && git init
□ docs/ klasörüne aynı iki dokümanı kopyala
□ .gitignore ve .env.example oluştur
□ gh repo create atomcv-frontend --public --source=. --remote=origin
□ İlk commit + push
□ VS Code'da klasörü aç
□ Terminalde `claude` başlat
□ XI-B.7'deki bootstrap promptu yapıştır
□ Claude'un özet + zor davranışlar + endişeler + plan çıktısını değerlendir
□ Mock stratejisi önerisini onayla
□ Onayla → CLAUDE.md oluşturulsun
□ Aşama 0'a başla
```

> **Önemli:** Her iki oturumda da ilk turda **kod yazdırma**. Önce anlama teyidi, sonra plan, sonra kod. Yanlış varsayımlar erken katmanlara gömülürse çıkarması pahalıdır.
