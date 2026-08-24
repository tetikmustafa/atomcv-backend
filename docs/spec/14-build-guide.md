# Bölüm XI-A — Adım Adım İnşa Rehberi

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM XI-A — SIFIRDAN BAŞLAMA: ADIM ADIM GELİŞTİRME REHBERİ

Bu bölüm, boş bir klasörden canlı bir uygulamaya kadar her adımı sırayla anlatır. **Geliştirme tamamen kendi bilgisayarında başlar**; VPS ancak Aşama 1 tamamlandıktan sonra devreye girer.

## XI-A.0 — Genel Strateji: Önce Lokal, Sonra Sunucu

```
┌─────────────────────────────────────────────────────────┐
│  1-6. HAFTA — SADECE KENDİ BİLGİSAYARINDA               │
│  Maliyet: €0 · Sunucu yok · Domain yok                  │
│  Sonuç: Çalışan, garantili tek sayfa CV üreten uygulama │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  6-7. HAFTA — VPS KİRALAMA VE İLK DEPLOY                │
│  Maliyet: ~€14/ay başlar                                │
│  Sonuç: atomcv.mustafatetik.com canlıda                 │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  7-14. HAFTA — CANLI ÜZERİNDE GELİŞTİRME                │
│  Her özellik lokalde geliştirilir, CI ile deploy edilir │
└─────────────────────────────────────────────────────────┘
```

**Neden VPS'i erteliyoruz:**
- Aşama 1'de LLM yok, dış servis yok — sunucuya ihtiyaç yok
- Boşuna aylık ödeme yapmazsın
- Mimarinin doğru olduğunu önce kendi makinende kanıtlarsın

**Neden Aşama 1 bitince alıyoruz (daha geç değil):**
- Deploy hattını erken kurmak, sonradan kurmaktan çok daha ucuz
- Her özelliği canlıda görerek ilerlemek motive edici
- "Lokalde çalışıyor, canlıda patlıyor" sorunlarını erken yakalarsın

---

## XI-A.1 — Bilgisayarında Kurulum (İlk Gün)

### Adım 1.1 — Gerekli araçlar

| Araç | Sürüm | Kurulum |
|---|---|---|
| **Java (JDK)** | 21 (LTS) | [Adoptium Temurin](https://adoptium.net) veya `sdk install java 21-tem` |
| **Node.js** | 22 (LTS) | [nodejs.org](https://nodejs.org) veya `nvm install 22` |
| **Docker Desktop** | Güncel | [docker.com](https://docker.com) |
| **Git** | Güncel | Sistem paket yöneticisi |
| **IntelliJ IDEA** | Community yeterli | Backend geliştirme |
| **VS Code** | Güncel | Frontend geliştirme |

**Doğrulama:**
```bash
java -version      # openjdk 21.x
node -v            # v22.x
docker --version   # 27.x veya üstü
docker compose version
git --version
```

### Adım 1.2 — Donanım gereksinimleri (kendi bilgisayarın)

| Kaynak | Minimum | Rahat |
|---|---|---|
| RAM | 8 GB | **16 GB** |
| Disk boş alan | 20 GB | 40 GB |
| CPU | 4 çekirdek | 8 çekirdek |

**Not:** `--profile core` ile günlük çalışmada sadece ~700 MB kullanılır (Postgres + Redis + Mailpit). Ağır servisler (LaTeX 2 GB, embedding 2.5 GB) yalnızca `--profile full` ile ve sadece o kısımlar üzerinde çalışırken açılır.

8 GB RAM'in varsa: LaTeX ve embedding'i **aynı anda** açma. LaTeX üzerinde çalışırken embedding'i kapat, tersi de geçerli.

### Adım 1.3 — Depo (repository) oluşturma

**İki ayrı repo** (XI-B.1). Aşağıdaki her şey `atomcv-backend` içindir; frontend
kendi reposunda aynı adımların Next.js karşılığını yürütür.

```bash
mkdir atomcv-backend && cd atomcv-backend
git init

# Klasör yapısı — src/ kökte, backend/ alt klasörü yok
mkdir -p src docker/latex docs scripts .github/workflows
```

**`.gitignore`:**
```gitignore
# Sırlar
.env
.env.local
*.pem
*.key

# Build çıktıları
build/
.gradle/
out/
bin/

# IDE
.idea/
.vscode/
*.iml

# İşletim sistemi
.DS_Store
Thumbs.db

# Geçici
*.log
/tmp/
```

**`.gitattributes`** — dokümanın gövdesinde yoktu, gerekli (EK D.1):
```gitattributes
* text=auto eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
*.jar binary
*.pdf binary
```

Windows'ta çalışılıyorsa bu dosya olmadan `gradlew` CRLF ile commit edilir ve
Linux runner'da çalışmaz.

**`.env.example`** (gerçek değerler ASLA commit edilmez):
```bash
# ── Uygulama ──
APP_NAME=AtomCV
APP_BASE_URL=http://localhost:3000

# ── Veritabanı ──
POSTGRES_DB=atomcv
POSTGRES_USER=atomcv
POSTGRES_PASSWORD=degistir_beni

# ── LLM (Aşama 2'de doldurulacak) ──
OPENROUTER_API_KEY=
OPENROUTER_MODEL=
GEMINI_API_KEY=
GEMINI_MODEL=
LLM_CHAIN_CHEAP=
LLM_CHAIN_MID=

# ── Güvenlik (Aşama 3'te) ──
SESSION_SECRET=
TURNSTILE_SECRET_KEY=
# NEXT_PUBLIC_* anahtarları frontend reposuna aittir; burada yeri yok.

# ── Servisler (Aşama 3'te) ──
RESEND_API_KEY=
AXIOM_TOKEN=
SENTRY_DSN=

# ── Bütçe ──
DAILY_BUDGET_USD=40
```

### Adım 1.4 — GitHub deposu

```bash
gh repo create atomcv-backend --public --source=. --remote=origin
# frontend için ayrıca: gh repo create atomcv-frontend --public

git add .
git commit -m "chore: initial repository structure"
git push -u origin main
```

**Public seçmenin faydası:** GitHub Actions dakikaları sınırsız, GHCR imajları ücretsiz.

**Sır sızıntısı koruması (hemen kur):** `.github/workflows/secrets-scan.yml`
içine gitleaks, artı yerel bir commit kancası. Kancayı elle `.git/hooks/` altına
yazmak yerine **pre-commit framework'ü** kullanılır — `.git/hooks/` versiyonlanmaz,
yani elle yazılan kanca ikinci bir makinede yoktur ve kimse fark etmez:

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/gitleaks/gitleaks
    rev: v8.30.1
    hooks:
      - id: gitleaks
```

```bash
pre-commit install
```

> **Kancanın çalıştığını doğrula.** Kurulu değilse commit sessizce geçer. Gerçek
> bir token deseniyle dene — AWS'nin dokümantasyon örnek anahtarları
> (`AKIAIOSFODNN7EXAMPLE`) gitleaks'in izin listesindedir ve **yanlış bir "temiz"
> raporu** verir.

---

## XI-A.2 — AŞAMA 0: İskelet (1-2 Hafta)

**Hedef:** Boş ama çalışan, test edilen ve deploy edilebilir bir uygulama.

### Adım 0.1 — Backend iskeleti

Repo'nun kökünde; `backend/` alt klasörü yok (XI-B.2).

```bash
# start.spring.io → Gradle-Kotlin, Java 21, Spring Boot 3.5.x
# Bağımlılıklar: Web, Data JPA, PostgreSQL Driver, Validation,
#                Actuator, Flyway, Testcontainers
# Lombok KULLANILMIYOR — record'lar ve düz constructor'lar (EK D.1)
```

**`build.gradle.kts` — temel bağımlılıklar:**
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Kaynak kodlaması sabitlenir: javac platform charset'ini kullanır ve bu
// Türkçe Windows'ta Cp1254, runner'da UTF-8'dir (EK D.1).
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
```

**Entegrasyon testleri ayrı bir source set'te.** `gradlew test` Docker'sız ve
hızlı kalır; `gradlew integrationTest` Testcontainers'ı çalıştırır. `check`'e
bilerek bağlanmaz — `gradlew build` Docker olmadan da çalışabilmelidir.

```kotlin
sourceSets { create("integrationTest") { /* main output'u classpath'e ekle */ } }
tasks.register<Test>("integrationTest") { /* shouldRunAfter(tasks.test) */ }
```

**Paket yapısını baştan doğru kur** (Bölüm 10.1):
```
com.mustafatetik.atomcv/
├── identity/  profile/  ingestion/  generation/
├── rendering/ llm/      embedding/  compilation/
├── jobs/      tracking/ billing/    shared/
```

**Doğrulama:** `./gradlew bootRun` → `http://localhost:8080/actuator/health` → `{"status":"UP"}`

### Adım 0.2 — Docker Compose (core profil)

**`docker-compose.yml`:**
```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    profiles: [core]
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 5s

  redis:
    image: redis:7-alpine
    profiles: [core]
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s

  mailpit:
    image: axllent/mailpit
    profiles: [core]
    ports: ["1025:1025", "8025:8025"]
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s

volumes:
  pgdata:
```

**Üçünde de healthcheck var.** Yalnız Postgres'te olsaydı `docker compose ps`
diğer ikisi için "healthy" yerine boş durum gösterirdi ve Aşama 0'ın
tamamlanma kontrolü ("üçü de healthy") doğrulanamazdı. `version:` anahtarı
kullanılmaz — Compose v2'de kaldırıldı, yazılırsa uyarı verir.

```bash
docker compose --profile core up -d
docker compose ps        # üçü de healthy olmalı
```

Mailpit arayüzü: `http://localhost:8025`

### Adım 0.3 — İlk Flyway migration

**`src/main/resources/db/migration/V1__initial_schema.sql`** — Bölüm 13'ün
**tamamı** (EK D.1). Boş tablo maliyetsizdir; şemayı bölmek, uygulanmış bir
migration'ı değiştirme yasağı altında aynı tabloları V2/V3'te yeniden açmak
demek olurdu.

```yaml
# application-local.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/atomcv
    username: atomcv
    password: ${POSTGRES_PASSWORD}
  flyway:
    enabled: true         # lokalde başlangıçta çalışır
    validate-on-migrate: true
  jpa:
    hibernate.ddl-auto: validate    # ⚠️ ASLA update/create
```

> **Kritik:** `ddl-auto: validate` — Hibernate'in şemayı kendi başına değiştirmesine asla izin verme. Şema tek kaynaktan (Flyway) yönetilir.

**Doğrulama:**
```bash
./gradlew bootRun
docker compose exec postgres psql -U atomcv -d atomcv -c "\dt"
# flyway_schema_history + tablolar görünmeli
```

### Adım 0.4 — Frontend iskeleti

**Bu adım `atomcv-frontend` reposunda yürütülür**, backend reposunda değil.

```bash
# atomcv-frontend/ içinde
npx create-next-app@latest . --typescript --tailwind --app --src-dir --import-alias "@/*"
npx shadcn@latest init
npm i @tanstack/react-query zustand react-hook-form zod next-intl
```

**Doğrulama:** `npm run dev` → `http://localhost:3000`

### Adım 0.5 — Makefile

Backend reposundadır ve yalnız backend'i çalıştırır; `front` hedefi frontend
reposuna aittir.

```make
# Windows: Git Bash zorunlu. cmd.exe altında SHELL=sh.exe olur ve tarifler çalışmaz.
ifeq ($(SHELL),sh.exe)
$(error Run make from Git Bash. cmd.exe and PowerShell cannot execute these recipes)
endif

# Compose .env'i kendisi okur, Spring okumaz. Bu include olmadan değişmiş bir
# POSTGRES_PASSWORD, kod hatası gibi görünen bir kimlik doğrulama hatası verir.
ifneq (,$(wildcard .env))
include .env
export
endif

# GNU Make, metakarakter içermeyen bir tarif satırını doğrudan CreateProcess ile
# çalıştırır ve ./gradlew bir Windows çalıştırılabiliri değildir.
GRADLE := sh ./gradlew

.PHONY: dev dev-full db-reset record test test-int golden-costs

dev:
	docker compose --profile core up -d
	$(GRADLE) bootRun --args='--spring.profiles.active=local,local-fake'

dev-full:
	docker compose --profile core --profile full up -d

db-reset:
	docker compose --profile core down -v
	docker compose --profile core up -d postgres
	$(GRADLE) bootRun --args='--spring.profiles.active=local,local-fake'

record:
	$(GRADLE) bootRun --args='--spring.profiles.active=local,local-record'

test:
	$(GRADLE) test

test-int:
	$(GRADLE) integrationTest

# Golden set'in render maliyetlerini gercek derleyiciden yeniden olcer (Adim 1.9)
golden-costs:
	$(GRADLE) latexTest --tests '*GoldenCostsIT' -Dgolden.record=true
```

**`gradlew latexTest` Makefile'da bir hedef değil.** LaTeX imajını kurup ondan
derleyen testler dakikalar sürüyor; `docker/latex` değiştiğinde elle
çalıştırılıyor ve CI'da koşmuyor (EK D.8.1).

**Flyway Gradle eklentisi eklenmez.** Migration'lar uygulama açılışında çalışır;
ikinci bir yol, iki farklı yapılandırmanın sessizce ayrışması demektir.

### Adım 0.6 — ArchUnit temel kuralları

`src/test/java/.../ArchitectureTest.java` — Bölüm 51.4'teki kuralları **hemen**
ekle. Sonradan eklemek çok daha zor olur (biriken ihlalleri temizlemek gerekir).

> **Her kuralın ihlalde patladığını doğrula.** Hiç düşmemiş bir kural, çalıştığı
> bilinmeyen bir kuraldır. Modül paketleri henüz boşken kurallar "failed to check
> any classes" ile düşer; muafiyeti global vermek yerine yalnız ilgili kurala ver
> (EK D.1).

### Adım 0.7 — CI hattı (deploy henüz yok)

Her repo kendi hattını taşır; backend reposunda frontend işi yoktur.

**`.github/workflows/ci.yml`** (backend):
```yaml
name: CI
on:
  push: { branches: [main] }
  pull_request:

jobs:
  build:                       # derleme + test + integrationTest
    steps:
      - run: ./gradlew build -x test
      - run: ./gradlew test
      - run: ./gradlew integrationTest
      - uses: actions/upload-artifact@v7      # if: always()
        with: { path: "build/reports/tests/\nbuild/test-results/" }

  codeql:                      # languages: java-kotlin, autobuild
  scan:                        # trivy, scanners: misconfig
```

Sırlar ayrı bir workflow'dadır (`secrets-scan.yml`, `fetch-depth: 0` ile tüm
geçmiş taranır) — tek bir sızıntı iki işi birden kırmasın diye.

> **Test raporlarını her koşulda yükle.** Sıfır test çalıştıran bir suite de
> "başarılı" raporlar; sayıyı görebilmenin tek yolu rapordur.
>
> **CI yalnız `main` push'unda ve PR'da çalışır.** Bir dalı push etmek hattı
> tetiklemez; kontrolleri görmek için PR açmak gerekir.

Deploy job'ı bu aşamada yoktur — VPS henüz alınmadı. Hattın tam hali (her iki
repo için ayrı ayrı, deploy adımlarıyla birlikte) Bölüm 47.1'dedir; VPS
kurulduktan sonra (XI-A.4, Adım V.7) buradaki `ci.yml` genişletilir.

### ✅ Aşama 0 tamamlanma kontrolü

```
□ `make dev` tek komutla çalışıyor
□ Backend health endpoint yanıt veriyor
□ Flyway migration uygulandı, tablolar var
□ ArchUnit testleri geçiyor — ve her biri ihlalde düştüğü görülmüş
□ CI yeşil, test raporları indirilip sayılar görülmüş
□ .env git'te değil, .env.example var
□ Mailpit arayüzü açılıyor
□ gitleaks kancası gerçek bir token deseniyle denenmiş
□ (frontend reposu) Frontend açılıyor
```

---

## XI-A.3 — AŞAMA 1: Yürüyen İskelet (3-4 Hafta)

**Hedef:** LLM olmadan, uçtan uca çalışan, garantili tek sayfa CV üreten uygulama.

> **Neden LLM yok:** Ürünün en riskli parçası (ölçüm + optimizasyon + render) LLM belirsizliği olmadan doğrulanır. Bu aşamanın sonunda bile kullanılabilir bir ürün vardır.

### Adım 1.1 — Domain modeli ve run yapısı

**Sıra önemli** — üstteki bittiğinde alttakine geç:

```
1. RichContent value object (Run, Mark)
   └── plainText() ve contentHash() metodları
2. ContentMigrator ("v" damgası okuma — şimdilik tek sürüm)
3. Atom, AtomVariant, Entry, Section entity'leri
4. UserScopedRepository + ProfileScopedRepository + ProfileRef  ← güvenlik temeli
5. Dört repository cephesi (paket-özel Spring Data arayüzleri üstünde)
6. ProfileAssembler (4 düz sorgu + bellekte birleştirme)
```

İki temel sınıfın neden bir tane olmadığı EK D.4'te; birleştirmenin ayrıntıları
EK D.5'te.

**Test yaz:** `contentHash` yalnızca `plainText` değişince değişmeli (Bölüm 16.2).

### Adım 1.2 — Manuel profil formu

Backend CRUD burada, form `atomcv-frontend`'de. Tek dil (EN), tek şablon
varsayımıyla. **API sözleşmesi EK D.6'da hazır** — endpoint yazarken yeniden
karar verilmez.

```
□ Bölüm ekleme/silme/sıralama
□ Entry ekleme/silme
□ Atom ekleme/düzenleme/silme
□ Tamamlanma yüzdesi hesabı
□ Profil okuma testi (≤6 sorgu)
```

### Adım 1.3 — LaTeX container

**`docker/latex/Dockerfile`** (Bölüm 29.2). İlk kurulumda imaj ~2 GB indirir, sabırlı ol.

```yaml
# docker-compose.yml'e ekle
  latex:
    build: ./docker/latex
    profiles: [full]
    ports: ["8090:8090"]
    networks: [latex-isolated]
    read_only: true
    tmpfs: [/tmp]

networks:
  latex-isolated:
    internal: true
```

**⚠️ Lokal geliştirmede dikkat:** `internal: true` ile container internete çıkamaz. İlk build sırasında bu ağı kullanma, build bitince ekle.

**Doğrulama:**
```bash
docker compose --profile full up -d latex
curl -X POST localhost:8090/compile -H 'Content-Type: text/plain' \
  --data-binary @test.tex -o out.pdf
```

### Adım 1.4 — Klasik şablon ve renderer

```
1. LatexInlineRenderer (escape + mark → komut)
2. Klasik şablon preamble (fontspec, geometry, custom komutlar)
3. DocumentRenderer.renderFinal()
4. DocumentRenderer.renderMeasurement()     ← AYNI preamble kullanmalı
```

**Kritik test:**
```java
@Test
void measurementAndFinalUseSamePreamble() {
    var m = renderer.renderMeasurement(req);
    var f = renderer.renderFinal(req);
    assertThat(extractPreamble(m)).isEqualTo(extractPreamble(f));
}
```

### Adım 1.5 — Ölçüm sistemi

```
1. \savebox ölçüm dokümanı üretimi
2. Log parse (ATOMCOST regex)
3. render_costs kalıcılığı (punto)
4. Şablon sabit maliyetlerini ÖLÇ ve config'e yaz
5. FontMetricEstimator (FontBox ile TTF metrikleri)
6. Geçersizleşme mantığı (content_hash değişince NULL)
```

**Sabit maliyetleri ölçme yöntemi:**
```
1. Bilinen içerikli bir test dokümanı derle
2. Sayfa metin yüksekliğini ölç (\textheight)
3. Boş bir bölüm başlığının yüksekliğini ölç
4. İki boş resumeItem arasındaki mesafeyi ölç (baselineSkip)
5. Sonuçları templates.yaml'a yaz
```

Bu bir kerelik manuel iştir ama **tüm sayfa garantisi buna dayanır** — dikkatli yap.

### Adım 1.6 — Faz C: Seçim algoritması

```
1. BudgetCalculator (toplam − sabit − yapısal rezerv)
2. Aşama 1: zorunlu yerleşim + çelişki tespiti
3. Aşama 2: greedy + etkin maliyet + azalan getiri
4. Aşama 3: swap iyileştirme
5. SelectionState çıktısı (selected + rejected + budget)
```

**Determinizm için tie-break'i unutma:**
```java
.thenComparing(c -> c.atom().id().toString())
```

### Adım 1.7 — Faz E, F ve PDF indirme

```
1. RenderPhase (seçilmiş içerik → LaTeX)
2. CompilationClient (LaTeX container'a HTTP)
3. VerificationPhase (sayfa sayısı ölçümü)
4. Bütçe geri besleme (sapma → %5 kıs → Faz C tekrar)
5. PDF indirme endpoint'i
```

### Adım 1.8 — Genel CV modu

İlan olmadığı için Faz A ve B atlanır. Skorlama ikincil kriterlerle yapılır:

```java
double generalModeScore(Atom atom) {
    return 0.35 * recencyScore(atom)
         + 0.30 * atom.importance()
         + 0.20 * impactScore(atom)
         + 0.15 * (atom.verified() ? 1.0 : 0.0);
}
```

### Adım 1.9 — Golden test set ve seed data

```
1. 5 golden profil JSON'u yaz (Bölüm 51.3)
2. DevSeeder (idempotent)
3. Ölçüm sonuçlarını *.costs.json olarak commit et
4. Dört kritik testi yaz (Bölüm 51.2)
```

### ✅ Aşama 1 tamamlanma kontrolü

```
□ Manuel form ile profil oluşturulabiliyor
□ PDF indiriliyor ve gerçekten 1 sayfa
□ 5 golden profilin hiçbirinde sayfa sınırı aşılmıyor
□ Determinizm testi geçiyor (50 tekrar, aynı sonuç)
□ Kilitler ve yapısal kısıtlar çalışıyor
□ Multi-tenant izolasyon testi geçiyor
□ Türkçe karakterli test dokümanı doğru derleniyor
□ Profil okuma ≤6 sorgu
□ Ölçüm ile gerçek sayfa arasında sapma <%3
```

**🎉 Bu noktada kullanılabilir bir ürünün var. Şimdi VPS zamanı.**

---

## XI-A.4 — VPS KİRALAMA VE SUNUCU KURULUMU

### Adım V.1 — Ne satın alınacak

**Sağlayıcı:** Hetzner Cloud (en iyi fiyat/performans, AB — KVKK/GDPR açısından uygun)

| Plan | vCPU | RAM | Disk | Fiyat | Ne zaman |
|---|---|---|---|---|---|
| **CPX21** | 3 | 4 GB | 80 GB | ~€8 | Aşama 1-2 için yeterli |
| **CPX31** | 4 | 8 GB | 160 GB | **~€14** | **Önerilen** — embedding + LaTeX rahat çalışır |
| CPX41 | 8 | 16 GB | 240 GB | ~€26 | Büyüme |

**Öneri:** CPX31 ile başla. Embedding container (2.5 GB) Aşama 2'de devreye girecek ve CPX21'de sıkışırsın.

**Konum:** Nürnberg / Falkenstein (Almanya) veya Helsinki. Türkiye'den gecikme ~40ms — Cloudflare önde olduğu için statik içerik hızlı.

**İşletim sistemi:** Ubuntu 24.04 LTS

**Sipariş sırasında:**
```
□ SSH anahtarı ekle (şifre ile giriş kapalı olacak)
□ IPv4 + IPv6 (ikisi de)
□ Backup (Hetzner'in kendi yedeği, +%20 fiyat — opsiyonel,
  bizim kendi yedek sistemimiz zaten var)
```

### Adım V.2 — SSH anahtarı oluşturma (kendi bilgisayarında)

```bash
ssh-keygen -t ed25519 -C "atomcv-deploy" -f ~/.ssh/atomcv
cat ~/.ssh/atomcv.pub     # bunu Hetzner'e yapıştır
```

`~/.ssh/config`:
```
Host atomcv
    HostName <SUNUCU_IP>
    User deploy
    IdentityFile ~/.ssh/atomcv
```

### Adım V.3 — Sunucu ilk kurulum

```bash
ssh root@<SUNUCU_IP>

# ── Sistem güncellemesi ──
apt update && apt upgrade -y
apt install -y ca-certificates curl gnupg ufw fail2ban unattended-upgrades

# ── Docker kurulumu ──
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  > /etc/apt/sources.list.d/docker.list
apt update && apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# ── Deploy kullanıcısı ──
useradd -m -s /bin/bash deploy
usermod -aG docker deploy
mkdir -p /home/deploy/.ssh
cp /root/.ssh/authorized_keys /home/deploy/.ssh/
chown -R deploy:deploy /home/deploy/.ssh
chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys

# ── Swap (OOM koruması) ──
fallocate -l 4G /swapfile
chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl vm.swappiness=10
echo 'vm.swappiness=10' >> /etc/sysctl.conf

# ── Güvenlik duvarı ──
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# ── SSH sertleştirme ──
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
systemctl restart ssh

# ── Otomatik güvenlik güncellemeleri ──
dpkg-reconfigure -plow unattended-upgrades
```

**⚠️ Çıkmadan önce test et:** Yeni bir terminalde `ssh deploy@<IP>` çalışıyor mu? Çalışmıyorsa root oturumunu kapatma.

### Adım V.4 — DNS ve Cloudflare

Portfolyo siten `mustafatetik.com`'da (Cloudflare Pages). AtomCV **alt alan adında** ve **ayrı sunucuda** olacak — ikisi birbirini etkilemez.

**Cloudflare dashboard → mustafatetik.com → DNS:**

| Tip | Ad | İçerik | Proxy |
|---|---|---|---|
| A | `atomcv` | `<SUNUCU_IPv4>` | 🟠 Proxied |
| AAAA | `atomcv` | `<SUNUCU_IPv6>` | 🟠 Proxied |

**Proxy (turuncu bulut) açık olmalı:** DDoS koruması, WAF, gerçek IP'nin gizlenmesi.

**Cloudflare SSL/TLS ayarı:** `Full (strict)` — sunucuda geçerli Let's Encrypt sertifikası olacak.

**Yayılma kontrolü:**
```bash
dig atomcv.mustafatetik.com +short
```

### Adım V.5 — TLS sertifikası

Cloudflare proxy açıkken HTTP-01 doğrulaması çalışmaz. **DNS-01** kullan:

```bash
# deploy kullanıcısı olarak
sudo apt install -y certbot python3-certbot-dns-cloudflare

# Cloudflare API token (Zone:DNS:Edit yetkisi)
sudo mkdir -p /etc/letsencrypt
echo "dns_cloudflare_api_token = <TOKEN>" | sudo tee /etc/letsencrypt/cloudflare.ini
sudo chmod 600 /etc/letsencrypt/cloudflare.ini

sudo certbot certonly \
  --dns-cloudflare \
  --dns-cloudflare-credentials /etc/letsencrypt/cloudflare.ini \
  -d atomcv.mustafatetik.com \
  --email <senin-eposta> --agree-tos --non-interactive

# Otomatik yenileme testi
sudo certbot renew --dry-run
```

**Alternatif (daha basit):** Cloudflare Origin Certificate kullan — 15 yıl geçerli, yenileme derdi yok. Cloudflare dashboard → SSL/TLS → Origin Server → Create Certificate.

### Adım V.6 — Uygulama kurulumu

```bash
ssh atomcv    # deploy kullanıcısı

sudo mkdir -p /opt/atomcv && sudo chown deploy:deploy /opt/atomcv
cd /opt/atomcv

# Sunucuda yalnız dağıtım dosyaları durur (compose, .env, scripts/) ve
# bunlar backend reposundadır. Frontend imajı GHCR'den gelir, kodu değil.
git clone https://github.com/tetikmustafa/atomcv-backend.git .
cp .env.example .env
nano .env         # gerçek değerleri doldur
chmod 600 .env

# İlk kalkış
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

**Doğrulama:**
```bash
curl -I https://atomcv.mustafatetik.com
curl https://atomcv.mustafatetik.com/actuator/health
```

### Adım V.7 — GitHub Actions deploy hattı

**GitHub → Settings → Secrets and variables → Actions:**

| Secret | Değer |
|---|---|
| `SSH_PRIVATE_KEY` | `~/.ssh/atomcv` içeriği |
| `SSH_HOST` | Sunucu IP |
| `SSH_USER` | `deploy` |

**`.github/workflows/deploy.yml`:**
```yaml
name: Deploy
on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions: { contents: read, packages: write }
    steps:
      - uses: actions/checkout@v4

      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # Her repo yalnız kendi imajını üretir. Frontend'in kendi deploy
      # workflow'u aynı şeyi atomcv-frontend imajı için yapar.
      - name: Build & push
        run: |
          docker build -t ghcr.io/tetikmustafa/atomcv-backend:${{ github.sha }} .
          docker push ghcr.io/tetikmustafa/atomcv-backend:${{ github.sha }}

      - uses: webfactory/ssh-agent@v0.9.0
        with: { ssh-private-key: ${{ secrets.SSH_PRIVATE_KEY }} }

      - name: Deploy
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.SSH_USER }}@${{ secrets.SSH_HOST }} \
            "cd /opt/atomcv && \
             ./scripts/deploy.sh backend ${{ github.sha }}"
```

**`scripts/deploy.sh` (sunucuda):** İki bileşen ayrı ayrı dağıtıldığı için
hangisinin SHA'sının güncelleneceğini ilk argüman söyler.

```bash
#!/bin/bash
set -euo pipefail
COMPONENT=$1          # backend | frontend
NEW_SHA=$2
VAR="${COMPONENT^^}_SHA"                     # BACKEND_SHA | FRONTEND_SHA

# İki SHA .env.deploy'da yaşar (XI-B.9.3); compose onu okur.
touch .env.deploy
PREV_SHA=$(grep "^$VAR=" .env.deploy | cut -d= -f2 || echo "")
sed -i "/^$VAR=/d" .env.deploy && echo "$VAR=$NEW_SHA" >> .env.deploy

docker compose --env-file .env.deploy -f docker-compose.prod.yml pull

# Migration (deploy'dan ÖNCE)
# ⚠️ AÇIK KARAR: `--spring.flyway.migrate-only=true` diye bir Spring Boot
# özelliği yoktur (EK D.1). İki gerçek seçenek: (a) Flyway CLI imajı ile
# migration'ı ayrı bir adımda çalıştırmak, (b) migration'ı uygulama
# açılışında bırakıp tek örnekle deploy etmek. Şu an (b) geçerli.

docker compose --env-file .env.deploy -f docker-compose.prod.yml up -d

# Health check
for i in $(seq 1 30); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null; then
    docker image prune -f
    exit 0
  fi
  sleep 2
done

# Rollback — yalnız bu bileşen geri alınır, diğeri yerinde kalır
echo "Health check başarısız — geri alınıyor"
if [ -n "$PREV_SHA" ]; then
  sed -i "/^$VAR=/d" .env.deploy && echo "$VAR=$PREV_SHA" >> .env.deploy
  docker compose --env-file .env.deploy -f docker-compose.prod.yml up -d
fi
exit 1
```

### Adım V.8 — Yedekleme kurulumu

```bash
# rclone kur ve R2'yi yapılandır
curl https://rclone.org/install.sh | sudo bash
rclone config    # S3-compatible → Cloudflare R2

# age (şifreleme) kur
sudo apt install -y age
age-keygen -o ~/.age-key.txt
# Public key'i backup script'ine, private key'i GÜVENLİ BİR YERE (yerel makine + parola yöneticisi)
```

**`/opt/atomcv/scripts/backup.sh`** (Bölüm 49.2) + cron:
```bash
crontab -e
# Her gece 03:00
0 3 * * * /opt/atomcv/scripts/backup.sh >> /var/log/atomcv-backup.log 2>&1
```

### ✅ VPS kurulum kontrolü

```
□ SSH anahtarla giriş çalışıyor, şifre ile giriş kapalı
□ UFW aktif, sadece 22/80/443 açık
□ Swap aktif (free -h)
□ Docker ve compose çalışıyor
□ DNS yayıldı, atomcv.mustafatetik.com sunucuya gidiyor
□ HTTPS çalışıyor, sertifika geçerli
□ Uygulama açılıyor, health OK
□ GitHub Actions deploy çalışıyor
□ Rollback test edildi (bilerek bozuk deploy yap)
□ Yedek script'i çalışıyor
□ ⚠️ RESTORE TESTİ yapıldı
```

---

## XI-A.5 — AŞAMA 2: İlana Özel Üretim (3-4 Hafta)

Artık canlı bir ortam var — her özellik lokalde geliştirilip CI ile deploy edilir.

### Adım 2.1 — LLM sağlayıcı hesapları

Önce **bir tane** yeterli (OpenRouter önerilir — tek key ile çok model):

```
□ OpenRouter hesabı aç, API key al, $5-10 kredi yükle
□ .env'e OPENROUTER_API_KEY ve OPENROUTER_MODEL yaz
□ Sonra Gemini (ücretsiz katman cömert) ekle
```

### Adım 2.2 — LLM Gateway

```
1. LlmProvider arayüzü + StructuredRequest/Response
2. OpenRouterProvider (ilk adaptör)
3. FakeLlmProvider (local-fake/record/real modları)
4. ProviderChain (fallback mantığı)
5. PromptRegistry (versiyonlu dosyalar)
6. llm_invocations telemetrisi
```

**Geliştirme sırası önemli:** Fake provider'ı **ilk** yaz — sonraki tüm geliştirmeyi ücretsiz yapabilirsin.

### Adım 2.3 — Faz A

```
1. Ön kontroller (uzunluk, entropi, sinyal kelime)
2. Prompt v1 yaz (prompts/job_analysis/v1.md)
3. JSON şeması (schema.json)
4. Makullük kapısı
5. Injection savunması (delimiter + alan denetimi)
6. Redis cache
7. embeddingTarget sentezi
```

**`local-record` ile fixture üret:**
```bash
make record
# Birkaç gerçek ilanla çalıştır → fixture'lar kaydedilir
# Sonra hep local-fake kullan
```

### Adım 2.4 — Embedding

```yaml
# docker-compose.yml
  embeddings:
    image: ghcr.io/huggingface/text-embeddings-inference:cpu-latest
    command: --model-id BAAI/bge-m3 --port 8081
    profiles: [full]
    ports: ["8081:8081"]
    volumes: [modelcache:/data]
```

İlk çalıştırmada ~2.5 GB model indirir.

```
1. EmbeddingProvider arayüzü
2. TeiEmbeddingProvider (HTTP)
3. FakeEmbeddingProvider (hash → deterministik vektör)
4. content_hash bazlı invalidation
5. pgvector kolonunun Hibernate eşlemesi
6. Fallback (servis düşerse ağırlıkları yeniden dağıt)
```

**Madde 5 migration istemiyor.** Kolon `V1`'de zaten var (`atoms.embedding
vector(1024)`, `atoms.embedding_hash TEXT`); eksik olan yalnız eşleme. Yeni bir
migration yazmak, uygulanmış bir migration'ı değiştirmeden mümkün olmazdı
(mutlak kural 2) ve gereksiz olurdu.

### Adım 2.5 — Faz B

```
1. ScoringWeights (config'den)
2. Embedding benzerliği (pgvector sorgusu)
3. Etiket/beceri/keyword örtüşmesi
4. Önem çarpanı
5. İkincil kriterler
6. Determinizm testi
```

### Adım 2.6 — Kuyruk ve SSE

> **Düzeltme.** Birinci madde yanlıştı: `jobs` tablosu **`V1`'de zaten var**
> (2.4'ün pgvector maddesiyle aynı hata). Eksik olan eşlemeydi; migration
> yazmak mutlak kural 2'yi ihlal ederdi. İkinci madde de ikiye ayrıldı:
> `JobQueue` worker'ın kapsamsız yüzü, `JobRepository` kullanıcının kapsamlı
> yüzü — gerekçe § 30.2'de.

```
1. jobs eşlemesi (migration YOK)
2. JobQueue (SKIP LOCKED) + JobRepository (kullanıcı kapsamlı)
3. JobWorker + heartbeat
4. Zombi toplayıcı (@Scheduled)
5. Retry politikası
6. SseRegistry + endpoint
7. Nginx proxy_buffering off      ← unutulursa SSE çalışmaz
8. Idempotency key
9. Graceful shutdown
```

### Adım 2.7 — Kota ve maliyet

> **Düzeltme, dördüncü ve beşinci kez.** `usage_counters` ve `feature_flags`
> **`V1`'de zaten var**. Bu adımda yazılan şey eşleme ve kurallar; migration
> yazmak mutlak kural 2'yi ihlal eder. Artık bir kalıp: bu kılavuz "tablo"
> dediğinde **önce `V1`'e bak**.

```
1. usage_counters eşlemesi (migration YOK)
2. QuotaService (atomik INSERT ON CONFLICT)
3. FeatureFlag tablosu + kill switch
4. AnomalyDetector (@Scheduled)
5. OTLP dışa aktarımı (Axiom hedefi Adım 3.1'de açılır)
6. /api/v1/account/usage endpoint'i
```

### ✅ Aşama 2 kontrolü

> **Düzeltme — sıralama çelişkisi.** Bu liste "Axiom'da loglar görünüyor"
> diyor, ama Axiom dataset'i **Adım 3.1'de** açılıyor (§ XI-A.6). Aşama 2'de
> yapılabilecek olan OTLP dışa aktarımını bağlamak; ihracatçı bir URL
> verilene kadar kapalı duruyor, çünkü gidecek yeri olmayan bir ihracatçı
> zamanlayıcıyla yeniden deneyip kendi arızasını loglar — gözlem, gözlenen
> sistemi bozar. Kutu **3.1'e taşındı**.

```
✅ İlan yapıştırılıp CV üretiliyor   ← JobSpecificCvIT: Faz A (fake sağlayıcı),
                                       Faz B, seçim, gerçek TeX, ve /download
✅ Sağlayıcı fallback çalışıyor       ← ProviderChainTest
✅ SSE ilerleme akıyor                ← JobStreamIT
✅ Kota doluyor ve engelliyor         ← QuotaIT + GenerationEnqueueServiceTest
✅ Kill switch çalışıyor              ← QuotaIT
✅ Anlamsız ilan reddediliyor         ← JobDescriptionPreflight + QueuedGenerationApiIT
✅ Injection denemesi davranışı       ← PlausibilityGate + JobAnalysisPromptTest
   değiştirmiyor
→  Axiom'da loglar görünüyor          ← Adım 3.1 (dataset yok)
```

---

## XI-A.6 — AŞAMA 3: Hesap ve MVP (3-4 Hafta)

### Adım 3.1 — Dış servis hesapları

```
□ Google Cloud Console → OAuth 2.0 Client ID
     Redirect URI: https://atomcv.mustafatetik.com/api/v1/auth/oauth/google/callback
□ GitHub → Settings → Developer settings → OAuth Apps
□ LinkedIn → Developer Portal → Create App
□ Cloudflare → Turnstile → Site ekle (atomcv.mustafatetik.com)
□ Resend → hesap aç, domain doğrula (aşağıda)
□ Sentry → proje oluştur
□ Axiom → dataset oluştur  ← Aşama 2'nin "loglar görünüyor" kutusu buraya taşındı;
                              OTLP ihracatçısı bağlı, tek eksik OTLP_URL/anahtar
```

### Adım 3.2 — E-posta domain kurulumu

Resend'de `mail.atomcv.mustafatetik.com` alt alanını ekle, verdiği kayıtları Cloudflare DNS'e gir:

| Tip | Ad | İçerik | Proxy |
|---|---|---|---|
| MX | `mail.atomcv` | `feedback-smtp.eu-west-1.amazonses.com` | ⚪ DNS only |
| TXT | `mail.atomcv` | `v=spf1 include:amazonses.com ~all` | ⚪ |
| TXT | `resend._domainkey.mail.atomcv` | (Resend verir) | ⚪ |
| TXT | `_dmarc.mail.atomcv` | `v=DMARC1; p=none; rua=mailto:...` | ⚪ |

> **⚠️ E-posta kayıtlarında proxy KAPALI olmalı** (gri bulut).

**DMARC kademeli sertleştirme:** `p=none` (2-4 hafta) → `p=quarantine` → `p=reject`

### Adım 3.3 — Kimlik doğrulama

**Sıra önemli — OAuth önce:**
```
1. Session yönetimi (Redis + HttpOnly cookie)
2. CSRF koruması
3. OAuth (Google → GitHub → LinkedIn)      ← e-posta riski yok
4. Magic link (selector/verifier)
5. POST ile doğrulama sayfası (prefetch koruması)
6. Account enumeration koruması
7. Rate limiting (3 katman)
8. Turnstile entegrasyonu
```

**Cookie ayarı — alt alan adı önemli:**
```java
ResponseCookie.from("sid", sessionId)
    .httpOnly(true).secure(true).sameSite("Strict")
    .domain("atomcv.mustafatetik.com")    // ⚠️ .mustafatetik.com DEĞİL
    .path("/").build();
```
Nokta ile başlayan domain (`.mustafatetik.com`) çerezi portfolyo sitesine de gönderir — gereksiz ve riskli.

### Adım 3.4 — CV yükleme ve çıkarım

```
1. Dosya doğrulama (magic byte, boyut)
2. PDFBox metin çıkarımı + sortByPosition
3. POI DOCX çıkarımı
4. TEX temizliği
5. Karışık metin tespiti
6. Prompt: profile_extraction/v1.md (EN + kaynak dil aynı çağrıda)
7. Normalizasyon (beceri alias, tarih parse, run üretimi)
8. Gözden geçirme ekranı (frontend)
9. Arka plan işleri (embedding + ölçüm) paralel tetikleme
```

### Adım 3.5 — Çok dillilik

```
1. Varyant staleness alanları (migration)
2. Çeviri işi (translation prompt)
3. is_user_edited koruması
4. Bayat varyant UI uyarısı
5. Dil-farkındalıklı Faz C (targetLang maliyeti)
6. ⚠️ TR uzunluk farkı testi
```

### Adım 3.6 — Anonim mod

```
1. EphemeralProfileStore (Redis, 2sa kayan TTL — etkinlikte tazelenir)
2. ProfileRef tipi (PERSISTENT | EPHEMERAL)
3. SessionCapabilities
4. IP bazlı kota (2 sayaç)
5. Yükseltme akışı (geçici → kalıcı)
6. ⚠️ Gizlilik testi: DB'ye hiçbir satır yazmamalı
```

### Adım 3.7 — Profil editörü

```
1. Alan bazlı autosave + debounce
2. Optimistic update + rollback
3. ETag/412 çakışma çözümü
4. dnd-kit sıralama (klavye desteğiyle)
5. Etiket/önem/kilit/alternatif UI
6. Arka plan iş göstergesi (SSE)
7. beforeunload koruması
```

### Adım 3.8 — Faz D ve cover letter

```
1. Alternatif seçimi (LLM'siz)
2. Üç kademeli eşik
3. Rewrite prompt (atom.skills kısıtı ile)
4. RewriteValidator (5 kontrol)
5. StructuredTaskScope paralel yürütme
6. About sentezi
7. Cover letter (bölümlü + klişe filtresi + süre kontrolü)
```

### Adım 3.9 — Hukuki ve kapanış

```
1. Gizlilik Politikası + Kullanım Şartları sayfaları
2. Hesap silme (kaskad + R2 + Redis)
3. Veri export (JSON + Markdown)
4. Geri bildirim (👍/👎 + support_grants)
5. i18n (TR + EN)
6. a11y gözden geçirme
```

### ✅ MVP yayın kontrolü

Bölüm EK C.1'deki tam listeyi uygula. Kritik olanlar:

```
□ Multi-tenant izolasyon testi TÜM endpoint'lerde geçiyor
□ Hesap silme her yerden siliyor (test edildi)
□ Gizlilik Politikası yayında ve AI sağlayıcı listesi doğru
□ Kill switch test edildi
□ ⚠️ Gerçek restore testi yapıldı
□ E-posta teslimatı doğrulandı (Gmail'e ulaşıyor, spam'de değil)
□ Rate limiting çalışıyor
□ Dev endpoint'leri prod'da yok
```

---

## XI-A.7 — AŞAMA 4: Olgunlaşma (Sürekli)

Bu aşamada sabit bir sıra yok — kullanıcı geri bildirimi ve kendi önceliğin belirler. Bölüm 55'teki listeyi referans al.

**Öncelik önerisi:**
```
1. Faz G (doğal dil düzenleme)     — en çok istenecek özellik
2. Ek şablonlar                     — görsel çeşitlilik
3. Başvuru takibi                   — düşük efor, yüksek fayda
4. Analitik + huni ölçümü           — nerede kaybediyorsun?
5. DOCX                             — bazı ATS'ler istiyor
6. GitHub entegrasyonu
7. LLM eval altyapısı               — prompt sayısı arttığında
```

---

## XI-A.8 — Günlük Geliştirme Akışı

```bash
# Sabah
git pull
make dev              # core servisler + backend (fake LLM)
make front            # ayrı terminal

# Özellik geliştirme
git checkout -b feat/faz-c-swap-optimization
# ... kod yaz ...
make test
git commit -m "feat(selection): add local swap improvement"
git push -u origin feat/faz-c-swap-optimization
# PR aç → CI yeşil → merge → otomatik deploy

# Gerçek LLM ile test gerekiyorsa
make record           # fixture üret (bir kez)
# veya
SPRING_PROFILES_ACTIVE=local,local-real make dev
```

**Commit mesajı formatı** (Conventional Commits):
```
feat(scope):     yeni özellik
fix(scope):      hata düzeltmesi
refactor(scope): davranış değişmeden yapı değişikliği
docs:            dokümantasyon
chore:           bakım
```

---

## XI-A.9 — Sık Karşılaşılacak Sorunlar

| Sorun | Neden | Çözüm |
|---|---|---|
| Docker "port already in use" | 5432 lokalde Postgres çalışıyor | Yerel Postgres'i durdur veya compose'da portu değiştir |
| Flyway checksum hatası | Uygulanmış migration değiştirilmiş | `make db-reset` (lokalde). Üretimde ASLA — yeni migration yaz |
| `ddl-auto` şemayı bozdu | `validate` yerine `update` yazılmış | `validate`'e dön, `make db-reset` |
| SSE akmıyor | Nginx buffering açık | `proxy_buffering off;` |
| LaTeX "font not found" | `fc-cache` çalıştırılmamış | Dockerfile'a `RUN fc-cache -fv` |
| Türkçe karakter bozuk | pdflatex kullanılıyor | XeLaTeX'e geç |
| Beceri eşleşmesi tuhaf | Locale TR, `toLowerCase()` bozuyor | `Locale.ROOT` + JVM `-Duser.language=en` |
| Profil yükleme yavaş | N+1 sorgu | 4 düz sorgu + assembler |
| Sayfa taşıyor | Ölçüm yapılmamış, tahmin kullanılıyor | `trace.C.estimatedAtoms` kontrol et, ölçüm işini tetikle |
| Deploy sonrası ilk istek yavaş | JVM soğuk başlangıç | Warm-up endpoint'i çağır |
| Magic link spam'de | DNS kayıtları eksik/yanlış | SPF/DKIM/DMARC doğrula, proxy kapalı mı bak |
| Disk doldu | Docker imajları + loglar | `docker system prune -a`, log rotasyonu ayarla |

---

## XI-A.10 — Maliyet Zaman Çizelgesi

| Dönem | Ne çalışıyor | Aylık maliyet |
|---|---|---|
| **Hafta 1-6** | Sadece lokal | **€0** |
| **Hafta 6-10** | VPS + lokal geliştirme, LLM testleri | **~€14 + ~$5 (tek seferlik kredi)** |
| **Hafta 10-14** | VPS + dış servisler (hepsi ücretsiz katman) | **~€15** |
| **MVP sonrası** | Kullanıcı sayısına göre | **€16-27** |

**İlk gerçek harcama 6. haftada başlar.** O zamana kadar çalışan bir ürünün olacak — yatırım kararını bilgiyle verirsin.

---
