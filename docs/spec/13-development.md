# Bölüm XI — Geliştirme (54-55)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM XI — GELİŞTİRME

## 54. Geliştirme Ortamı

> **İki repo:** Docker Compose backend reposunda yaşar. Frontend lokalde yalnızca `npm run dev` ile çalışır ve `http://localhost:8080` üzerinden backend'e bağlanır. Klasör yapıları ve repo ayrımının sonuçları: Bölüm XI-B.

### 54.1 Compose profilleri (backend reposunda)

```yaml
services:
  postgres:   { profiles: [core], ports: ["5432:5432"] }
  redis:      { profiles: [core] }
  mailpit:    { profiles: [core], ports: ["8025:8025"] }   # e-posta yakalayıcı
  latex:      { profiles: [full] }
  embeddings: { profiles: [full] }
```

```bash
make dev        # core (~700 MB) — günlük çalışma
make dev-full   # core + full   — renderer/pipeline üzerinde çalışırken
```

**Backend ve frontend container'da değil, IDE'den çalışır** — hot reload, debugger, breakpoint doğal çalışsın.

### 54.2 Sahte sağlayıcılar

```java
@Component @Profile("local-fake")
public class FakeLlmProvider implements LlmProvider {
    public <T> Result<LlmResponse<T>> callStructured(StructuredRequest<T> req) {
        var key = req.promptId() + ":" + hash(req.userPrompt());
        if (fixtures.containsKey(key)) return parse(fixtures.get(key));
        return Result.ok(SyntheticGenerator.fromSchema(req.outputSchema()));
    }
}
```

| Mod | Davranış | Ne zaman |
|---|---|---|
| `local-fake` | Fixture / sentetik | UI, pipeline mantığı, hata yolları |
| `local-record` | Gerçek çağrı + kaydet | Yeni fixture üretmek |
| `local-real` | Gerçek çağrı | Prompt üzerinde çalışırken |

**Kayıt modu kritik:** Bir kez `local-record` ile çalıştır, fixture'lar `src/test/resources/fixtures/llm/` altına düşsün. Bu fixture'lar aynı zamanda golden test set'in girdisi olur.

**Fixture'lar classpath'te değil, dosya sisteminde.** `local-record` uygulamayı `bootRun` ile çalıştırıp fixture *yazıyor* ve paketlenmiş bir kaynağa yazılamaz; okuma ile yazma aynı yeri göstermezse kaydedilen fixture hiç oynatılmaz. Yol `atomcv.llm.fake.fixture-dir` ile verilir, yalnız `local-*` profillerinde etkindir, üretim jar'ına girmez.

Dosya adı `{promptId}/{version}-{sha256[0:12]}.json` — girdinin **hash'i**, metni değil. Değişen bir ilan ıskalar: alakasız bir kaydı oynatmak fixture'ın hiç olmamasından kötüdür, çünkü hat başka bir işin analizi üzerinde çalışır.

**`synthesize` bayrağı.** `local-fake`'te açık: hiçbir fixture'ın karşılamadığı çağrı şema biçiminde bir yer tutucuyla cevaplanır, yoksa temiz bir klon hiç üretim yapamazdı. `local-record` ve `local-real`'de **kapalı** — sentetik bir cevabın fixture olarak kaydedilmesi onu gerçek bir kayıttan ayırt edilemez yapardı.

`FakeLlmProvider` iki kademeye birden cevap verir; tek tier'a bağlansa `local-fake` altında diğer zincir hiç çalışmazdı.

**`local-fake` zinciri override etmek zorundadır** (`chain.cheap`/`chain.mid` → `[fake]`). Taban yapılandırma gerçek adaptörü sayar ve o profilde anahtar yoktur; override olmasa her üretim `ALL_PROVIDERS_UNAVAILABLE` ile biterdi — üstelik yapılandırma doğru görünürken. Bu, "bedava ve çevrimdışı çalışır"ın tam tersidir, dolayısıyla bir testle bağlanır.

Diğer sahte sağlayıcılar:
- `FakeEmbeddingProvider` — metin hash'inden deterministik vektör
- `FakeLatexCompiler` — sabit PDF döner (`--profile full` gerekmez)

### 54.3 Seed data

```java
@Component @Profile("local")
public class DevSeeder implements ApplicationRunner {
    public void run(ApplicationArguments args) {
        if (userRepo.count() > 0) return;              // idempotent
        seedFromJson("seeds/senior_backend_tr.json");
        seedFromJson("seeds/junior_frontend_en.json");
        seedFromJson("seeds/career_changer.json");
        seedFromJson("seeds/minimal_edge.json");
    }
}
```

**Ölçüm önbelleği repoya commit edilir** (`*.costs.json`) — `--profile full` olmadan Faz C üzerinde çalışılabilir.

```java
@Profile("local")
@PostMapping("/dev/login-as/{email}")
public void devLogin(@PathVariable String email) { ... }
```

### 54.4 Makefile

```make
dev:        docker compose --profile core up -d && ./gradlew bootRun --args='--spring.profiles.active=local,local-fake'
dev-full:   docker compose --profile core --profile full up -d
db-reset:   docker compose --profile core down -v && docker compose --profile core up -d postgres && $(GRADLE) bootRun ...
record:     ./gradlew bootRun --args='--spring.profiles.active=local,local-record'
test:       ./gradlew test
test-int:   ./gradlew integrationTest
test-llm:   ./gradlew llmEval
lint:       ./gradlew spotlessApply
```

`front`, `e2e` ve `npm` hedefleri frontend reposunun Makefile'ındadır.
`db-reset` Flyway'i uygulamayı açarak çalıştırır: Flyway Gradle eklentisi
kurulmaz, yoksa migration'ların iki ayrı yapılandırması olur (EK D.1).

Yeni makinede kurulum: `make dev`

### 54.5 Üretimle farkı kontrol altında tutmak

1. **Entegrasyon testleri Testcontainers ile gerçek Postgres+pgvector kullanır** — fake DB yok
2. **CI'da smoke test:** gerçek LaTeX container'ıyla bir CV derle, PDF çıktığını doğrula

---

## 55. Aşama Aşama Yol Haritası

> **Repo etiketleri:** Her kalem hangi repoda yapılacağını gösterir.
> **[B]** = `atomcv-backend` · **[F]** = `atomcv-frontend` · **[B+F]** = her ikisinde ayrı ayrı
>
> Adım adım komutlar ve doğrulama kontrolleri: Bölüm XI-A. Repo kurulumu, klasör yapıları ve Claude Code promptları: Bölüm XI-B.

### AŞAMA 0 — İskelet (1-2 hafta)

**Amaç:** Deploy hattını en başta kurmak — sonradan kurmaktan çok daha ucuz.

> **Not:** VPS bu aşamada henüz alınmaz. Aşağıdaki "Deployment" kalemleri Aşama 1 bittikten sonra, VPS kiralandığında yapılır (bkz. XI-A.0 ve XI-A.4). Aşama 0'da yalnızca CI (test) hattı kurulur, CD (deploy) hattı sonra eklenir.

```
[B] Backend iskeleti
├── Spring Boot + actuator health
├── Docker Compose (core profil): Postgres+pgvector, Redis, Mailpit
├── Flyway + V1 şema (users, profiles, sections, entries, atoms, atom_variants, tags)
├── Makefile
├── ArchUnit temel kuralları
└── CLAUDE.md

[F] Frontend iskeleti                     ✅ tamamlandı (EK D.7, EK D.10)
├── Next.js + Tailwind + shadcn/ui
├── Klasör yapısı (XI-B.3)
├── i18n iskeleti (next-intl, en + tr)
├── MSW mock altyapısı
└── CLAUDE.md

[B+F] CI (her repoda ayrı)
├── build + test
├── gitleaks
└── (CD hattı VPS alındıktan sonra eklenir)

[B] Deployment — VPS alındıktan sonra (Aşama 1 sonu)
├── Hetzner VPS kurulumu (ufw, fail2ban, SSH sertleştirme, swap)
├── Cloudflare DNS (atomcv alt alanı) + TLS
├── Nginx + docker-compose.prod.yml
├── scripts/deploy.sh (bileşen argümanlı)
└── Health check + rollback
```

**Çıktı:** İki repo da lokalde çalışıyor, CI yeşil.

---

### AŞAMA 1 — Yürüyen İskelet (3-4 hafta)

**Amaç:** LLM olmadan uçtan uca çalışan ürün. En riskli parça (ölçüm + optimizasyon + render) LLM belirsizliği olmadan doğrulanır.

```
[B] Veri modeli
├── Atom + AtomVariant + run modeli
├── ContentMigrator iskeleti ("v" damgası)
├── RichContent value object
└── User-scoped repository base

[B] Profil API
├── Bölüm/entry/atom CRUD (tek dil: EN)
├── Tamamlanma hesabı
├── Profil okuma optimizasyonu (4 düz sorgu)
└── OpenAPI şeması yayınlama

[F] Profil UI
├── Manuel form (adım adım)
├── gen:api ile tip üretimi → gerçek API'ye bağlanma
└── Tamamlanma göstergesi

[B] Render
├── LaTeX container (XeLaTeX, izole, semafor, warm-up)
├── Klasik şablon
├── InlineRenderer + merkezi escape
├── DocumentRenderer: final + ölçüm modları
└── Font metrik tahmini (FontBox)

[B] Ölçüm
├── \savebox ölçüm dokümanı
├── Log parse
├── render_costs kalıcılığı (punto)
└── Geçersizleşme mantığı

[B] Pipeline
├── PipelineContext, Result, PipelineError
├── Faz C: bin-packing seçim (3 aşama)
├── Faz E: render
├── Faz F: sayfa doğrulama + bütçe geri besleme
├── Genel CV modu (ikincil kriterler)
└── PDF indirme

[B] Test
├── Sayfa sınırı testi
├── Determinizm testi
├── Kilit/kısıt testleri
├── Multi-tenant izolasyon testi
└── Golden profiller + seed data
```

**Çıktı:** Kullanıcı profil girer, garantili tek sayfa CV alır. **Bu bile kullanılabilir bir üründür.**

---

### AŞAMA 2 — İlana Özel Üretim (3-4 hafta)

```
[B] LLM altyapısı
├── LlmProvider arayüzü + Strategy
├── 5 sağlayıcı adaptörü (OpenRouter, Gemini, OpenAI, Anthropic, DeepSeek)
├── Fallback zinciri (env-driven)
├── PromptRegistry (versiyonlu dosyalar)
├── llm_invocations telemetrisi
└── FakeLlmProvider (local-fake/record/real)

[B] Faz A
├── Ön kontroller (uzunluk, entropi, sinyal kelime)
├── LLM çağrısı + şema
├── Makullük kapısı
├── Prompt injection savunması (3 katman)
├── Redis cache (7 gün)
└── embeddingTarget sentezi

[B] Embedding
├── BGE-M3 container (text-embeddings-inference)
├── EmbeddingProvider arayüzü + fallback
├── content_hash bazlı invalidation
└── pgvector entegrasyonu

[B] Faz B
├── Hibrit skorlama (embedding + etiket + beceri + keyword)
├── Önem çarpanı
├── İkincil kriterler
└── Determinizm (tie-break)

[B] Asenkron
├── jobs tablosu + SKIP LOCKED
├── Worker + heartbeat + zombi toplayıcı
├── Retry politikası (hata tipine göre)
├── SSE + Nginx buffering off
├── Idempotency key
└── Graceful shutdown

[B] Kota ve maliyet
├── usage_counters
├── Kill switch (feature flag)
├── Anomali tespiti
└── Axiom entegrasyonu (OpenTelemetry)

[B] Faz F
└── Uygunluk raporu (kapsama sayıları)
```

**Çıktı:** İlana göre doğru içerik seçimi, uydurma riski **sıfır** (yeniden yazım yok).

---

### AŞAMA 3 — Hesap ve Zenginleştirme (3-4 hafta) → **HALKA AÇIK MVP**

```
[B] Kimlik
├── OAuth (Google, GitHub, LinkedIn)   ← magic link'ten ÖNCE
├── Session cookie + Redis + CSRF
├── Magic link (selector/verifier, POST doğrulama)
├── Account enumeration koruması
├── Rate limiting (3 katman)
└── Turnstile

[B] E-posta
├── Resend entegrasyonu
├── SPF/DKIM/DMARC (alt domain: mail.atomcv.mustafatetik.com)
├── Thymeleaf şablonları (HTML + plain text)
├── Suppression list + webhook (imza doğrulamalı)
└── Mailpit (lokal)

[B] Ingestion
├── Dosya doğrulama (magic byte, boyut)
├── PDFBox / POI / TEX çıkarımı
├── Karışık metin tespiti
├── LLM yapılandırma (EN + kaynak dil tek çağrıda)
├── Normalizasyon (beceri, tarih, run, Locale.ROOT)
├── Gözden geçirme ekranı (zorunlu)
└── Arka plan işleri (embedding, ölçüm) paralel

[B] Çok dillilik
├── İki dilli atomlar
├── Staleness takibi (derived_from, source_hash, is_stale)
├── is_user_edited koruması
├── Pivot çeviri
└── Dil-farkındalıklı Faz C (TR uzunluk farkı)

[B] Anonim mod
├── EphemeralProfileStore (Redis, 2sa kayan TTL — etkinlikte tazelenir)
├── SessionCapabilities
├── Kota (2 ayrı sayaç, IP bazlı)
├── Yükseltme akışı (geçici → kalıcı)
└── Gizlilik testi (DB'ye yazmaz)

[F] Profil editörü
├── Alan bazlı autosave + debounce
├── Optimistic update + ETag/412
├── Sürükle-bırak (dnd-kit, klavye)
├── Etiket / önem / kilit / alternatif metin
├── Bayat varyant uyarısı
└── Arka plan iş göstergesi

[B] Faz D
├── Alternatiflerden seçim (LLM'siz)
├── Üç kademeli eşik
├── Paralel yürütme (StructuredTaskScope)
├── Doğrulama katmanı (5 kontrol)
└── About sentezi

[B] Cover letter
├── Atomlardan türetme
├── Bölümlü yapı
├── Klişe filtresi
├── Süre iddiası kontrolü
└── Yeniden üretim

[F] i18n + a11y
├── next-intl (3 eksen)
├── ICU MessageFormat
├── CV içi tarih formatı
├── Radix bileşenleri
└── aria-live bölgeleri

[B+F] Hukuki
├── Gizlilik Politikası + Kullanım Şartları
├── Hesap silme (kaskad)
├── Veri export (JSON + Markdown)
└── Sorumluluk reddi

[B+F] Geri bildirim
├── 👍/👎 + kategori + yorum
├── support_grants (48sa, denetim kaydı)
└── Örtük sinyal takibi
```

**Çıktı: Halka açık MVP.**

> Bu aşamada iki repo yoğun şekilde birlikte ilerler. Backend her endpoint grubunu bitirdiğinde OpenAPI şeması güncellenir; frontend `npm run gen:api` ile tipleri tazeler. Sıralama önerisi: Bölüm XI-B.9.2.

---

### AŞAMA 4 — Olgunlaşma (sürekli)

```
[B] Şablon ve format
├── Modern + Kompakt şablonlar
├── Özelleştirme (Katman A + B)
├── Şablon sürümleme + ölçüm geçersizleştirme
├── DOCX renderer
└── Ham kaynak indirme

[B+F] Pipeline
├── Faz G: doğal dil düzenleme
├── Manuel toggle
└── Selection state üzerinden iterasyon

[B+F] Ürün
├── Başvuru takibi + PDF arşivleme (14 gün / süresiz)
├── GitHub entegrasyonu
├── ATS uyumluluk doğrulaması
├── Sürüm iletişimi + changelog
└── Yaşam döngüsü e-postaları

[B+F] Kalite
├── Golden test set genişletme
├── LLM eval altyapısı
├── Performans bütçeleri CI'da
├── axe-core a11y denetimi
└── Playwright E2E genişletme

[F] Büyüme
├── Analitik (Umami) + huni ölçümü
├── SEO landing + blog
└── Diğer diller (pivot)

[B+F] Açık kaynak hazırlığı
├── Mimari dokümanlarının İngilizceye çevrilmesi
├── README (İngilizce, mimari özet + kurulum)
├── CONTRIBUTING.md + SECURITY.md
└── Örnek .env.example doğrulaması

Gelecek
├── İlan URL'den çekme (SSRF korumalı)
├── Toplu (batch) mod
├── Kullanıcı tanımlı şablonlar
└── LinkedIn About / bio çıktıları
```

### 55.1 Zaman tahmini

| Aşama | Süre (part-time) | Kümülatif |
|---|---|---|
| 0 — İskelet | 1-2 hafta | 2 hafta |
| 1 — Yürüyen iskelet | 3-4 hafta | 6 hafta |
| 2 — İlana özel | 3-4 hafta | 10 hafta |
| 3 — Hesap + MVP | 3-4 hafta | **14 hafta (~3.5 ay)** |
| 4 — Olgunlaşma | Sürekli | — |

---
