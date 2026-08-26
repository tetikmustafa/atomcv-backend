# Bölüm IX — Operasyon (46-50)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM IX — OPERASYON

## 46. Deployment ve Sunucu Yapılandırması

### 46.1 Sunucu özellikleri

| Kaynak | Minimum | **Önerilen** | Büyüme |
|---|---|---|---|
| vCPU | 3 | **4** | 8 |
| RAM | 4 GB | **8 GB** | 16 GB |
| Disk (NVMe) | 80 GB | **160 GB** | 240 GB |
| Swap | 2 GB | **4 GB** | 4 GB |

**Hetzner CPX31** (4 vCPU / 8 GB / 160 GB) — ~€14/ay

### 46.2 RAM dağılımı

| Servis | RAM |
|---|---|
| Spring Boot (heap 512M + JVM) | ~800 MB |
| PostgreSQL (shared_buffers 256M) | ~600 MB |
| Next.js | ~350 MB |
| Redis (maxmemory 128M) | ~150 MB |
| **BGE-M3 embedding** | **~2.000 MB** |
| LaTeX (boşta 50M + 3×250M) | ~800 MB |
| Nginx + Umami | ~150 MB |
| OS + Docker | ~600 MB |
| **Tepe toplam** | **~5.4 GB** |

### 46.3 Disk dağılımı

```
LaTeX imajı (texlive-xetex + fontlar)   ~2.0 GB
BGE-M3 model cache                      ~2.5 GB
Diğer imajlar                           ~2.0 GB
PostgreSQL veri (büyümeyle)             ~5-20 GB
Loglar (rotasyonlu)                     ~1 GB
Yedek geçici alanı                      ~5 GB
Sistem                                  ~10 GB
─────────────────────────────────────────────
Minimum 80 GB, konforlu 160 GB
```

### 46.4 İlk kurulum

```bash
# 1. Sunucu hazırlığı
apt update && apt upgrade -y
apt install -y docker.io docker-compose-plugin ufw fail2ban

# 2. Güvenlik duvarı
ufw default deny incoming
ufw allow 22/tcp   # SSH (Cloudflare IP'lerine kısıtlanabilir)
ufw allow 80,443/tcp
ufw enable

# 3. SSH sertleştirme
# /etc/ssh/sshd_config: PasswordAuthentication no, PermitRootLogin no

# 4. Deploy kullanıcısı
useradd -m -s /bin/bash deploy && usermod -aG docker deploy

# 5. Swap
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile

# 6. TLS
certbot certonly --standalone -d atomcv.mustafatetik.com

# 7. Uygulama
# Sunucuda yalnız dağıtım dosyaları bulunur: compose, .env, scripts/.
# İkisi de backend reposundadır (bkz. 47.3); imajlar GHCR'den gelir.
git clone https://github.com/tetikmustafa/atomcv-backend.git /opt/atomcv && cd /opt/atomcv
cp .env.example .env && chmod 600 .env    # sırları doldur
docker compose -f docker-compose.prod.yml up -d
```

### 46.5 Ortam değişkenleri

```bash
# Veritabanı
POSTGRES_PASSWORD=
DATABASE_URL=jdbc:postgresql://postgres:5432/atomcv

# LLM sağlayıcıları (en az biri)
OPENROUTER_API_KEY=      OPENROUTER_MODEL=
GEMINI_API_KEY=          GEMINI_MODEL=
OPENAI_API_KEY=          OPENAI_MODEL=
ANTHROPIC_API_KEY=       ANTHROPIC_MODEL=
DEEPSEEK_API_KEY=        DEEPSEEK_MODEL=
LLM_CHAIN_CHEAP=gemini,deepseek,openrouter
LLM_CHAIN_MID=openai,anthropic,openrouter

# Güvenlik
OAUTH_GOOGLE_CLIENT_ID=      OAUTH_GOOGLE_CLIENT_SECRET=
OAUTH_GITHUB_CLIENT_ID=      OAUTH_GITHUB_CLIENT_SECRET=
TURNSTILE_SECRET_KEY=        NEXT_PUBLIC_TURNSTILE_SITE_KEY=
SESSION_COOKIE_DOMAIN=       SESSION_COOKIE_SECURE=true

# Servisler
RESEND_API_KEY=
OTLP_ENABLED=   OTLP_URL=   OTLP_AUTHORIZATION=   OTLP_DATASET=
SENTRY_DSN=
R2_ACCOUNT_ID=  R2_ACCESS_KEY=  R2_SECRET_KEY=  R2_BUCKET=

# Bütçe
DAILY_BUDGET_USD=40
```

**`SESSION_SECRET` yok, ve olmamalı.** Oturum kimliği `SecureRandom`'dan gelen
256 bitlik opak bir değer ve Redis'te duruyor; imzalanan hiçbir şey yok,
dolayısıyla imzalayacak bir sır da yok. Kullanılmayan bir sır, yalnızca
sızabilecek bir sırdır.

**Gözlemlenebilirlik değişkenleri `AXIOM_*` değil `OTLP_*`.** Kod
`management.otlp.metrics.export.*` altından okuyor, ve isim satıcının değil
telin adı: Axiom'dan çıkılırsa değişken adı yalan söylemez. İki tuzak birlikte
geliyor — `OTLP_AUTHORIZATION` `Bearer ` önekini de taşır, ve
`micrometer-registry-otlp` **metrik** gönderdiği için URL sağlayıcının
*metrics* ucudur; trace ucuna giden metrik sessizce reddedilir.

---

## 47. CI/CD

> **İki repo, iki hat.** `atomcv-backend` ve `atomcv-frontend` bağımsız CI/CD hatlarına sahiptir; her biri kendi Docker imajını üretip GHCR'a push eder. Sunucudaki `docker-compose.prod.yml` (backend reposunda yaşar) her iki imajı da çeker. Detaylı koordinasyon: Bölüm XI-B.9.

### 47.1 İki bağımsız workflow

> **Kritik:** Bunlar **iki ayrı dosyadır, iki ayrı repoda.** Tek bir workflow'da `needs: [backend, frontend]` yazılamaz — repolar arası job bağımlılığı GitHub Actions'ta mümkün değildir. Her repo kendi testini çalıştırır, kendi imajını üretir, kendi bileşenini deploy eder.

#### `atomcv-backend/.github/workflows/ci-cd.yml`

```yaml
name: CI/CD
on:
  push: { branches: [main] }
  pull_request:

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: gradle }
      - run: sh ./gradlew build -x test
      - run: sh ./gradlew test                # unit + ArchUnit
      - run: sh ./gradlew integrationTest     # Testcontainers
      # - run: sh ./gradlew spotlessCheck     # formatter yapılandırılınca aç

  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aquasecurity/trivy-action@0.28.0      # sürüm sabit, @master değil
        with: { scan-type: 'fs', severity: 'HIGH,CRITICAL' }
      - uses: github/codeql-action/init@v3
        with: { languages: java }
      - uses: github/codeql-action/analyze@v3

  llm-eval:
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - id: changed
        run: |
          if git diff --name-only origin/main...HEAD | grep -q '^src/main/resources/prompts/'; then
            echo "run=true" >> $GITHUB_OUTPUT
          fi
      - if: steps.changed.outputs.run == 'true'
        run: sh ./gradlew llmEval
      # rapor PR'a yorum olarak yazılır

  publish-schema:
    needs: [build-and-test]
    runs-on: ubuntu-latest
    steps:
      - run: sh ./gradlew generateOpenApiDocs
      - uses: actions/upload-artifact@v4       # frontend'in tüketmesi için
        with: { name: openapi-schema, path: build/openapi.json }

  deploy:
    needs: [build-and-test, security]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions: { contents: read, packages: write }
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build & push backend image
        run: |
          docker build -t ghcr.io/tetikmustafa/atomcv-backend:${{ github.sha }} .
          docker push ghcr.io/tetikmustafa/atomcv-backend:${{ github.sha }}
      - uses: webfactory/ssh-agent@v0.9.0
        with: { ssh-private-key: ${{ secrets.SSH_PRIVATE_KEY }} }
      - name: Deploy backend only
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.SSH_USER }}@${{ secrets.SSH_HOST }} \
            "cd /opt/atomcv && ./scripts/deploy.sh backend ${{ github.sha }}"
```

> **Bugünkü hâli (Aşama 1).** Repoda `ci.yml` var, `ci-cd.yml` yok: sunucu
> olmadığı için `deploy` ve `publish-schema` işleri henüz yazılmadı, `llm-eval`
> ise Aşama 2'de prompt'larla gelir. Çalışan işler `build` (derleme + test +
> integrationTest + her koşulda rapor yükleme), `codeql` ve `scan`; sırlar ayrı
> bir `secrets-scan.yml` dosyasında, tüm geçmişi tarayacak şekilde
> (`fetch-depth: 0`). Action sürümleri yukarıdakilerden yeni — Dependabot
> yükseltiyor, elle sabitlenmiş bir liste tutulmuyor. CodeQL dili
> `java-kotlin`'dir; `java` artık geçerli bir tanımlayıcı değil.

#### `atomcv-frontend/.github/workflows/ci-cd.yml`

```yaml
name: CI/CD
on:
  push: { branches: [main] }
  pull_request:

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: npm }
      - run: npm ci
      - run: npm run typecheck
      - run: npm run lint
      - run: npm test
      - run: npm run build
      - run: npm run check:bundle-size
      - run: npm audit --audit-level=high

  contract-check:
    runs-on: ubuntu-latest
    continue-on-error: true          # uyarı, bloker değil
    steps:
      - uses: actions/checkout@v4
      - name: Fetch backend OpenAPI schema
        run: |
          curl -sfL -o openapi.json \
            "https://raw.githubusercontent.com/tetikmustafa/atomcv-backend/main/build/openapi.json" \
            || echo "schema fetch failed, skipping"
      - run: npm ci && npm run gen:api:ci
      - name: Detect drift
        run: |
          git diff --exit-code src/types/api.d.ts \
            || echo "::warning::Backend API şeması değişmiş — 'npm run gen:api' çalıştırıp commit et"

  deploy:
    needs: [build-and-test]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions: { contents: read, packages: write }
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build & push frontend image
        run: |
          docker build -t ghcr.io/tetikmustafa/atomcv-frontend:${{ github.sha }} .
          docker push ghcr.io/tetikmustafa/atomcv-frontend:${{ github.sha }}
      - uses: webfactory/ssh-agent@v0.9.0
        with: { ssh-private-key: ${{ secrets.SSH_PRIVATE_KEY }} }
      - name: Deploy frontend only
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.SSH_USER }}@${{ secrets.SSH_HOST }} \
            "cd /opt/atomcv && ./scripts/deploy.sh frontend ${{ github.sha }}"
```

**Her iki repoda da aynı GitHub Secrets tanımlanır:** `SSH_PRIVATE_KEY`, `SSH_HOST`, `SSH_USER`. Aynı deploy anahtarı kullanılabilir; `deploy.sh` hangi bileşenin güncelleneceğini ilk argümandan alır.

> **Migration'ın yeri — açık karar.** Yukarıdaki backend deploy job'ında ayrı bir migration adımı yoktur; Flyway şu an üretimde de uygulama açılışında çalışır (EK D.1). Bu, tek uygulama örneğiyle güvenlidir. Yatay ölçeklemeye geçilirse iki örnek aynı anda migration çalıştırmaya kalkabilir — o noktada Flyway CLI imajıyla ayrı bir deploy adımı gerekir. Karar o zamana ertelenmiştir.

### 47.2 Kritik kurallar

| Kural | Gerekçe |
|---|---|
| **İmaj tag'i = git SHA** | `latest` kullanma — hangi sürümün canlıda olduğunu bilemezsin, rollback imkânsızlaşır |
| **Bir kere build, her yerde aynı imaj** | Deploy sırasında yeniden build etme |
| **Migration deploy'dan ÖNCE** | Expand-contract deseniyle geriye uyumlu |
| **Health check + otomatik rollback** | Bozuk deploy canlıda kalmasın |
| **Build sunucuda yapılmaz** | RAM tükenir |

### 47.3 Staging

Aynı compose, farklı domain, ayrı VPS (opsiyonel, +€8/ay). Prod'a doğrudan gitme.

---

## 48. Gözlemlenebilirlik

### 48.1 Yapılandırılmış log

```json
{
  "timestamp": "2026-08-11T14:23:01.482Z",
  "level": "INFO",
  "correlationId": "req_abc123",
  "userId": "usr_456",
  "event": "generation.phase.completed",
  "phase": "C",
  "durationMs": 12,
  "atomsSelected": 16,
  "usedPt": 498.3
}
```

**İçerik ASLA loglanmaz.** ArchUnit ile denetlenir:

```java
@ArchTest
static final ArchRule noPiiInLogs = noClasses()
    .should().callMethodWhere(target(nameMatching("(debug|info|warn|error)"))
        .and(rawParameterTypes(anyElementThat(assignableTo(RichContent.class)))));
```

### 48.2 ContentShape — içerik yerine şekil

```java
public record ContentShape(
    int charCount, int wordCount, int runCount, int emphasisCount,
    int numericTokenCount, int properNounCount,
    String language, boolean hasNonAscii, boolean hasSpecialLatex,
    double renderCostPt
) {}
```

`{ charCount: 187, runCount: 5, hasSpecialLatex: true, renderCostPt: 41.2 }` — içeriği bilmeden "bu atom anormal uzun ve özel karakter içeriyor" teşhisi mümkün.

### 48.3 İzlenecek metrikler

| Kategori | Metrik |
|---|---|
| **Pipeline** | Faz bazında p50/p95 gecikme, başarı oranı |
| **Seçim** | Bütçe doluluk oranı, sayfa sapma oranı, tahmin kullanım oranı |
| **LLM** | Sağlayıcı fallback oranı, şema hata oranı, token maliyeti/gün |
| **Doğrulama** | Yeniden yazım red oranı, red nedenleri dağılımı |
| **Kullanıcı** | Manuel düzenleme oranı, geri bildirim oranı |
| **Sistem** | CPU, RAM, disk, kuyruk bekleme süresi |
| **E-posta** | Teslimat oranı, bounce oranı |

### 48.4 Kullanıcı onaylı teşhis

```sql
CREATE TABLE support_grants ( ... );   -- Bölüm 13
```

```
☐ Teşhis için CV içeriğimin 48 saat boyunca incelenmesine izin veriyorum
```

**Erişim denetim kaydı:** Sen içeriğe baktığında `accessed_at` yazılır ve kullanıcı bunu görebilir.

### 48.5 Replay

```bash
./gradlew replay --generation=9b1c... --phase=C
```

Faz B, C, E saf fonksiyon → `selection_state` ile kendi makinende yeniden çalıştırma. Üretim verisine erişmeden hata ayıklama.

---

## 49. Yedekleme ve Felaket Kurtarma

### 49.1 3-2-1 kuralı

```
├── Canlı veri     → sunucudaki Postgres
├── Günlük yedek   → Cloudflare R2 (şifreli)
└── Haftalık arşiv → Backblaze B2 (ikinci sağlayıcı)
```

**Aynı diskteki yedek, yedek sayılmaz** — disk arızası, ransomware, hesap kilitlenmesi senaryolarında işe yaramaz.

### 49.2 Yedek script'i

```bash
#!/bin/bash
set -euo pipefail
STAMP=$(date +%Y%m%d-%H%M)

docker compose exec -T postgres pg_dump -U postgres atomcv \
  | gzip \
  | age -r "$AGE_PUBLIC_KEY" \
  > "/tmp/backup-$STAMP.sql.gz.age"

rclone copy "/tmp/backup-$STAMP.sql.gz.age" "r2:atomcv-backups/daily/"
rm "/tmp/backup-$STAMP.sql.gz.age"

# Saklama: 7 günlük + 4 haftalık + 6 aylık
rclone delete --min-age 7d  "r2:atomcv-backups/daily/"
```

**Kritik:** Yedek yükleyen kullanıcının **silme yetkisi olmamalı** (write-only credential) → ele geçirilse bile yedekleri silemez.

### 49.3 WAL arşivleme

Point-in-time recovery için:
```
wal_level = replica
archive_mode = on
archive_command = 'rclone copy %p r2:atomcv-wal/'
```

Veri kaybı penceresi: gecelik snapshot yerine ~5 dakika.

### 49.4 ⚠️ Restore testi

**Ayda bir gerçek restore testi yap.** Test edilmemiş yedek = yedek yok.

```bash
# Ayrı bir container'da
docker run --rm -e POSTGRES_PASSWORD=test -d --name restore-test postgres:17
age -d -i key.txt backup.sql.gz.age | gunzip | docker exec -i restore-test psql -U postgres
# Satır sayılarını doğrula
```

R2'nin egress'i ücretsiz olduğu için bu test bedava.

### 49.5 Felaket kurtarma senaryosu

```
1. Yeni VPS aç                       ~10 dk
2. Docker + compose kur (script'li)  ~2 dk
3. Git'ten config çek                ~2 dk
4. R2'den son yedeği indir + restore ~15 dk
5. DNS yönlendir                     ~5-30 dk (TTL)
─────────────────────────────────────────────
Toplam ~1 saat, veri kaybı ~5 dakika (WAL ile)
```

Ücretsiz bir ürün için kabul edilebilir.

---

## 50. Ölçeklenme Eşikleri

### 50.1 Eşikler ve müdahaleler

| Metrik | Eşik | Müdahale |
|---|---|---|
| CPU (5dk ort.) | %70 | Sunucuyu büyüt |
| RAM | %80 | Büyüt veya JVM heap ayarla |
| Disk | %75 | Log retention kısalt, imaj temizliği |
| Postgres bağlantı | %70 | HikariCP havuz ayarı |
| Kuyruk bekleme p95 | > 30sn | Worker sayısı artır |
| LaTeX kuyruk bekleme | > 15sn | Semafor limitini artır (CPU varsa) |
| Pipeline p95 | > 20sn | Profil çıkar, darboğazı bul |
| Aylık LLM maliyeti | Bütçenin %80'i | Kota sıkılaştır |

### 50.2 Büyüme yolu

```
1. DİKEY BÜYÜME (muhtemelen yıllarca yeterli)
   CPX31 → CPX41 → CPX51

2. VERİTABANINI AYIR
   Aynı DC'de ikinci VPS + private network

3. LATEX'İ AYIR
   En CPU-yoğun bileşen; zaten ayrı container, HTTP arayüzü

4. UYGULAMAYI ÇOĞALT
   Stateless; oturum Redis'te ✓, kuyruk SKIP LOCKED ✓, SSE LISTEN/NOTIFY ✓
```

### 50.3 Hazır olanlar

| Bileşen | Ölçeklenmeye hazır | Neden |
|---|---|---|
| Uygulama | ✅ | Stateless, oturum Redis'te |
| Kuyruk | ✅ | `SKIP LOCKED` çoklu worker |
| SSE | ✅ | LISTEN/NOTIFY dağıtımı |
| LaTeX | ✅ | Ayrı container, HTTP |
| Embedding | ✅ | Ayrı container, HTTP |
| Postgres | ⚠️ | Tek yazıcı; read replica iş gerektirir |

### 50.4 ⚠️ Ölçeklenmeden önce profil çıkar

```
Pipeline yavaşladı
  ├── LLM sağlayıcı gecikmesi?   → sunucu büyütmek çözmez
  ├── N+1 sorgu?                 → kod düzeltmesi
  ├── Ölçüm cache'i çalışmıyor?  → mantık hatası
  └── Gerçekten CPU mu doldu?    → o zaman büyüt
```

---
