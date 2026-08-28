# Kapanış Denetimi — Aşama 0-3

> **2026-08-28.** Aşama 0, 1, 2 ve 3 baştan tarandı: dokümanların söylediği ile
> kodun yaptığı karşılaştırıldı, açıklar çıkarıldı, kararlar alındı.
> Repo-yerel, senkronlanmaz. **`current.md` rolling log'dur, bu dosya denetimin
> kaydıdır** — bir madde kapandıkça buradaki satırı ✅ ile işaretle.
>
> **Hedef:** uygulama geliştiricinin makinesinde eksiksiz çalışsın, ve VPS
> alındığı gün deploy için gereken her şey hazır olsun. **VPS satın alma ve
> sunucu üzerindeki işlemler bu denetimin dışında** (§ XI-A.4).

---

## 1. Ne bulundu — aşamaya göre

### Aşama 0 — iskelet

| Bulgu | Durum |
|---|---|
| **CD hattı yok.** `.github/workflows/` içinde yalnız `ci.yml` ve `secrets-scan.yml`; `deploy.yml`, `docker-compose.prod.yml`, `nginx.conf`, `scripts/deploy.sh` hiç yazılmadı | § 55 "VPS alındıktan sonra" diye erteliyor — gecikme değil, ama açık. **Dilim 6** |
| **`latexTest` CI'da hiç koşmuyor.** `build.gradle.kts:131`'de tanımlı, `ci.yml` yalnız `test` + `integrationTest` çalıştırıyor. § 54.5'in "CI'da gerçek LaTeX container'ıyla smoke test" maddesi karşılanmıyor | **Dilim 2** |
| **Spotless yok.** § 47.1 `spotlessCheck` çalıştırıyor, eklenti bile eklenmemiş — CI'da biçim kapısı yok | **Dilim 2** |
| **Trivy imajı görmüyor.** `Misconfiguration scan` Dockerfile'ı tarıyor, derlenen imajı değil (imaj zaten derlenmiyor) | **Dilim 6** |

### Aşama 1 — yürüyen iskelet

| Bulgu | Durum |
|---|---|
| **ATS metin çıkarma (§ 23.2) yarım.** `FitReport` indi; **üretilen PDF geri okunmuyor** — "kendi çıktımızı PDFBox ile oku, atomlar metinde çıkıyor mu" yarısı yok. PDFBox 3.0.5 zaten bağımlılıkta | **Dilim 5** |
| **Determinizm atom id'sine bağlı.** Aynı skor *ve* aynı maliyetteki iki atom, her içe aktarımda yeniden üretilen id yüzünden yer değiştiriyor. Mevcut determinizm testi tek profil üzerinde koştuğu için yakalamıyor | **Dilim 5** |
| **Atomsuz entry sayfaya hiç çıkamıyor.** Seçim atom atom çalışıyor; altında madde olmayan bir diploma satırı aday bile değil | **Dilim 5** (karar 2) — ✅ indi |
| `UserScopedRepository.findAll` yok | Bilinçli, kapandı sayıldı |

### VPS (§ XI-A.4)

**Hiç başlanmadı — ve bu denetimin kapsamı dışında.** On bir maddelik kontrol
listesi (sunucu, UFW, swap, DNS, TLS, deploy, rollback, yedek, **restore
testi**) sende. **Dilim 6 bunun kodunu hazırlıyor**, sunucuyu değil.

### Aşama 2 — ilana özel üretim

| Bulgu | Durum |
|---|---|
| **`make record` hiçbir şey kaydetmiyordu.** `FixtureStore.save` yazılmış, javadoc'u "`local-record` kullanır" demiş, tek çağıranı bir test yardımcısıydı. Gerçek çağrılar yapılıyor, cevaplar atılıyordu | ✅ **Dilim 1'de kapandı** |
| **Zincirde tek sağlayıcı var.** `llm/providers/` içinde yalnız `OpenRouterProvider`; § 55 beş adaptör istiyor, `.env`'de Gemini anahtarı dolu. OpenRouter kesilirse ürün duruyor | **Dilim 7** (karar 13) |
| **Axiom telde doğrulanmadı.** `.env` dolu, exporter bağlı; kanıt dağıtımı bekliyor | Sende — § 4 |
| **`llm_invocations.user_id` NULL.** Günlük bütçe freni çalışıyor, kullanıcı bazlı maliyet çıkmıyor | **Dilim 4** (karar 8) |
| **§ 44.3'ün üretim limiti yok.** Mevcut `RateLimiter` girişe bağlı; üretim hakkını kısan şey `QuotaService`'te olmalı ve orada değil | **Dilim 4** (karar 12) |
| **`local-fake` fixture'ları yok** → `latexTest` 44/48, `bullet_rewrite`/`about_synthesis` yerelde anlamsız | Yol açıldı; **`make record` sende** — § 4 |

### Aşama 3 — hesap ve MVP

Adım listesi bitti (3.3-3.6, 3.8, 3.9 ✅). **EK C.1 yayın listesinde karşılığı
olmayan altı şey var:**

| Bulgu | Durum |
|---|---|
| **Sentry hiç bağlı değil.** `build.gradle.kts`'de bağımlılık yok, hiçbir yml `SENTRY_DSN` okumuyor. EK C.1 "Sentry hata yakalıyor" diyor | **Dilim 2** (karar 6) |
| **Resend webhook + suppression yazımı yok.** `EmailSuppressions` okuyor, hiçbir şey yazmıyor — kendi javadoc'u söylüyor. Sert bounce'lar birikirse gönderim itibarı gider, magic link herkes için ölür | **Dilim 7** |
| **Güvenlik header'ları açıkça ayarlanmamış.** `SecurityConfig`'de `headers(...)` çağrısı yok; Spring varsayılanı **CSP vermez** | **Dilim 2** |
| **Dev bean'lerinin prod'da olmadığını tutan test yok.** `DevSeeder`, `LocalDevUser`, `LocalDevSessions`, `FakeLlmProvider` doğru `@Profile` taşıyor — ama silen biri olsa sessiz geçer | **Dilim 2** |
| **Saklama süresi politikası yok.** `jobs.payload` CV ve ilan metnini, `generation` ilan metnini taşıyor; hiçbir şey budamıyor | **Dilim 3** (karar 5) |
| **`support_grants.accessed_at` hiç yazılmıyor.** Operatör arayüzü bu repoda yok; § 48.4'ün sözü bugün boş | Açık kalıyor — **erişim aracı gelirse ilk işi bu** |

**Daha küçük, hâlâ canlı olanlar:**

- Hesabın **boş** profil satırı anonim yükseltmeyi engelliyor → kullanıcı emeği sessizce gidiyor (**karar 3**)
- **`PROFILE_ALREADY_EXISTS` (409) katalogda, hiçbir şey üretmiyor** — ikinci CV bugün profile *ekleniyor*, bölümler ikiye katlanıyor (**karar 4**)
- Anonim işler yükseltmede taşınmıyor; anonim oturumun kullanıcı indeksi (toplu iptali) yok
- `ExtractedContact` / `Contact` / şema aynı şekli üç yerde taşıyor
- **R2 istemcisi hiç yok** → PDF her indirişte yeniden derleniyor, § 57.4'ün "silme R2'den de geçer" cümlesi karşılıksız (**karar 7**)

### Süreç borcu

- **`docs/handoff/to-frontend.md` 396 satır**, sınır 100. `B-044`-`B-058` — **on beş madde, tek bir `ACK` yok.** (**Dilim 8**)
- ~~`docs/notes/current.md` 200 satırda~~ ✅ 172'ye indirildi (Dilim 1)

### Yerelde bulunan üç tuzak — hepsi Dilim 1'de kapandı ✅

1. **`.env`'in üretim anahtarları yereli sessizce değiştiriyordu.** Makefile `.env`'i `export` ediyor: `RESEND_API_KEY` dolu olduğunda magic link Mailpit'e değil internete gidiyordu, `TURNSTILE_SECRET_KEY` dolu olduğunda `POST /auth/magic-link` widget'sız 403 dönüyordu. Artık `application-local.yml` ikisini `LOCAL_*` adlarından okuyor.
2. **Swagger UI Adım 3.3'ten beri POST edemiyordu** — CSRF zorunlu, UI başlığı eklemiyordu. `springdoc.swagger-ui.csrf.enabled` yalnız `local`'de açıldı.
3. **`manual-test-stage-2.md`'nin çıplak `curl -X POST` komutları 403 alıyor** — güncel tur `manual-test-stage-3.md`'de.

---

## 2. Alınan kararlar

Onay: geliştirici, 2026-08-28. **On üçü de onaylandı.**

| # | Karar | Gerekçe | Dilim |
|---|---|---|---|
| 1 | **Üretim migration'ı açılışta kalıyor, tek örnekle deploy** | § 47'nin `--spring.flyway.migrate-only=true`'su bir Spring Boot property'si değil. Flyway kendi kilidini alıyor; ayrı bir CLI adımı bakım yükü | 8 (§ 47 Düzeltme) |
| 2 | **Atomsuz entry sayfaya çıkabilecek** — sıfır-atomlu aday, maliyeti `entryHeader` | Alternatifi ("her entry'ye zorunlu atom") kullanıcıya yalan söyletir; diploma satırının maddesi olmaz | 5 |
| 3 | **Anonim yükseltme: hedef profil satırı boşsa sessizce ezilir**, doluysa `conflict` döner ve seçim kullanıcınındır | Bugünkü `kept_existing` boş satırda saf kayıp. Birleştirme MVP'ye girmiyor | 4 |
| 4 | **İkinci CV `409 PROFILE_ALREADY_EXISTS` alır**, **`mode=replace`** ile açıkça istenebilir | Sessiz ekleme bölümleri ikiye katlıyor. ⚠️ Önce `mode=append` önermiştim; **spec haklıydı** — § 08b "yalnız değiştir veya koru" diyor, `append` P8'in yasakladığı sessiz çoğaltmanın ta kendisi | 4 |
| 5 | **Saklama:** tamamlanmış işin `payload`'ı 7 gün, `generation.job_description` 30 gün sonra `NULL`'lanır | Gizlilik Politikası yayına girdiği gün bunun bir cevabı olmalı | 3 |
| 6 | **Sentry bağlanacak** | EK C.1'de zorunlu madde, bugün sıfır referans | 2 |
| 7 | **R2 MVP'ye girmiyor**, § 57.4'ün R2 cümlesi işaretlenecek | PDF'i yeniden derlemek çalışıyor; karşılıksız bir gizlilik iddiası bırakmamak lazım | 8 |
| 8 | **`llm_invocations.user_id` doldurulacak** | `UserContext` zaten zincirin üstünde; sonradan geriye dönük veri üretmek imkânsız | 4 |
| 9 | **Spotless dar kurulacak** — boşluk + import düzeni, formatlayıcı değil | Tam formatlayıcı 250 dosyalık tek diff üretir, `git blame`'i bozar | 2 |
| 10 | **Trivy imaj taraması** deploy hattıyla gelecek | Bugün taranacak imaj yok | 6 |
| 11 | **DMARC:** `p=none` → 3 hafta → `quarantine` → MVP+1 ay → `reject` | Rapor okumadan sertleştirmek teslimatı kırar | Sende — § 4 |
| 12 | **§ 44.3 üretim limiti** `QuotaService`'e — saatte N üretim | Kota günlük; bir kullanıcı hakkını 4 dakikada yakıp LLM faturasını tepeye çıkarabiliyor | 4 |
| 13 | **Gemini adaptörü yazılacak** | Zincirin düşecek ikinci sağlayıcısı yok; OpenRouter kesilirse ürün duruyor. Anahtar `.env`'de zaten dolu | 7 |

**Karar istemeyen, devreden:** V1 bazı enum benzeri kolonlara `CHECK` koyuyor,
bazılarına koymuyor — § 13'ü bilinçli yansıtıyor, eksikleri ucuz bir migration.

---

## 3. Dilimler — tek PR'da birikiyor

Geliştirici kararı: **dilimler biriktirilecek, tek PR açılacak.**

| # | Dilim | İçerik | Durum |
|---|---|---|---|
| 1 | Yerel geliştirme sürülebilir | Fixture kayıt yolu, `.env` tuzakları, Swagger CSRF, `manual-test-stage-3.md` | ✅ **bitti** — 994 birim test |
| 2 | Yayın sertleştirme | Sentry, güvenlik header'ları, prod-profil testi, `latexTest` → CI, Spotless | ✅ **bitti** |
| 3 | Saklama işi | `jobs.payload` 7 gün, ilan metni 30 gün, gecelik iş | ✅ **bitti** |
| 4 | Küçük kararlar | `user_id`, § 44.3 limiti, `409` + mod, boş profil ezme | ✅ **bitti** |
| 5 | Model kararları | Atomsuz entry adayı, determinizm bozucusu, ATS geri okuma | ✅ **bitti** — atomsuz entry 2026-08-28 ayrı bir oturumda indi |
| 6 | Deploy altyapısı | `Dockerfile`, `docker-compose.prod.yml`, nginx, `deploy.sh`, `deploy.yml`, yedek + restore, Trivy | ✅ **bitti** |
| 7 | Sağlayıcı ve e-posta | Gemini adaptörü, Resend webhook + suppression yazımı | ✅ **bitti** |
| 8 | Dokümanlar | § 47, § 57.4, § 3.2 düzeltmeleri · § 51.7 · `STATUS.md` · `CLAUDE.md` | ✅ **bitti** |

---

## 4. Senin yapacakların — adım adım

Kod dilimlerinden bağımsız, sırayla yapılabilir.

### A · Yerelde eksiksiz çalıştırma

**A1 — Magic link turunu sür.** `manual-test-stage-3.md` § 1. Özet:

```bash
LOCAL_DEV_SESSION=false make dev
curl -s -c jar.txt localhost:8080/actuator/health > /dev/null
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' jar.txt)
curl -s -b jar.txt -H "X-XSRF-TOKEN: $XSRF" -X POST localhost:8080/api/v1/auth/magic-link \
  -H 'Content-Type: application/json' -d '{"email":"ben@example.com"}'
```

**202** bekliyorsun, sonra `http://localhost:8025` (Mailpit) → linkteki `?s=` ve
`&v=` → `POST /api/v1/auth/verify`. `.env`'e dokunmana gerek yok, yerel
yapılandırma üretim anahtarlarını artık okumuyor.

**A2 — Fixture'ları kaydet.** `manual-test-stage-3.md` § 5. **Para harcar.**

```bash
make dev-full     # ayrı kabuk: container'lar
make record       # backend, local+local-record — gerçek OpenRouter
```

Sonra **her fazı bir kez sür**: gerçek bir ilanla `POST /generations`
(`job_analysis`, `bullet_rewrite`, `about_synthesis`), gerçek bir CV ile
`POST /profile/import` (`profile_extraction`), gövdeye `"coverLetter": true`
(`cover_letter`). Log'da `Recorded job_analysis:v1 to ...` satırlarını
göreceksin.

⚠️ **Fixture anahtarı istek metninin özetinden türüyor — kullandığın ilan ve CV
metinlerini sakla.** Başka bir metinle fixture ıskalanır.

Bitince: `sh ./gradlew latexTest` → **48/48** (bugün 44/48).

**A3 — Aşama 2 turunu tekrar sür.** `manual-test-stage-2.md`, ama POST'ları
çerez kavanozuyla (§ 0'daki uyarı). Fixture'lar indikten sonra üretim artık
*anlamlı* çıktı verecek.

### B · Adım 3.2 — e-posta domaini

1. Resend → Domains → `mail.atomcv.mustafatetik.com` ekle.
2. Cloudflare DNS'e dört kaydı gir — **dördünde de proxy KAPALI (gri bulut)**:

| Tip | Ad | İçerik |
|---|---|---|
| MX | `mail.atomcv` | `feedback-smtp.eu-west-1.amazonses.com` (öncelik 10) |
| TXT | `mail.atomcv` | `v=spf1 include:amazonses.com ~all` |
| TXT | `resend._domainkey.mail.atomcv` | Resend'in verdiği anahtar |
| TXT | `_dmarc.mail.atomcv` | `v=DMARC1; p=none; rua=mailto:<adresin>` |

3. Resend'de **Verify** → yeşil olana kadar bekle (5-30 dk).
4. Doğrula — **`dig` Windows'ta yok, PowerShell'de**:

```powershell
Resolve-DnsName -Name mail.atomcv.mustafatetik.com -Type MX
Resolve-DnsName -Name mail.atomcv.mustafatetik.com -Type TXT
Resolve-DnsName -Name resend._domainkey.mail.atomcv.mustafatetik.com -Type TXT
Resolve-DnsName -Name _dmarc.mail.atomcv.mustafatetik.com -Type TXT
```

Yayılımı test etmek için otoriter sunucuyu atla:
`nslookup -type=TXT mail.atomcv.mustafatetik.com 1.1.1.1`

5. Prod `.env`'e yaz (yerel `.env`'e **değil**):
   `EMAIL_FROM=no-reply@mail.atomcv.mustafatetik.com`, `EMAIL_FROM_NAME=AtomCV`,
   `EMAIL_REPLY_TO=<adresin>`
6. **Takvime not düş: +3 hafta → `p=quarantine`.** Rapor gelmiyorsa `rua`
   adresini kontrol et.

### C · Dış servisler

**C1 — Sentry DSN.** Sentry → **Settings** → **Projects** → projen → sol menüde
**Client Keys (DSN)** → `DSN` alanı (`https://…@…ingest.…sentry.io/…`).
Prod `.env`'e `SENTRY_DSN=` olarak yaz. *(Dilim 2 bunun yerini açıyor.)*

**C2 — OAuth redirect URI'ları.** Google Console'a prod callback'ini ekle:
`https://atomcv.mustafatetik.com/api/v1/auth/oauth/google/callback`.
GitHub tek callback kabul ettiği için **prod için ikinci bir OAuth App** aç,
dev'inkine dokunma. *(Yerelde OAuth denemek istersen Google'a
`http://localhost:8080/api/v1/auth/oauth/google/callback`'i ikinci URI olarak
ekle — ama gerekmez, oturum ve CSRF yolları magic link turundan geçiyor.)*

**C3 — Turnstile.** Cloudflare → Turnstile → siteye prod domainini ekle.

**C4 — UptimeRobot.** `https://atomcv.mustafatetik.com/actuator/health`,
5 dk aralık, e-posta bildirimi. *(VPS sonrası.)*

**C5 — Axiom.** Dağıtımdan sonra dataset'te gerçekten satır göründüğünü
doğrula. Aşama 2 kontrol listesinin tek açık kutusu bu.

### D · Yayından önce — EK C.1

- Gmail'e magic link at → **spam'de değil**, gelen kutusunda. E-postanın
  kaynağından SPF/DKIM/DMARC üçünün de `pass` olduğunu oku.
- Kill switch'i canlıda bir kez aç-kapa.
- Gizlilik Politikası + Kullanım Şartları yayında, **AI sağlayıcı listesi
  gerçekten kullandıklarımızla aynı** (OpenRouter + Gemini).
- Sunucudaki `.env` dizininde `gitleaks detect --no-git` temiz.
- ⚠️ **Gerçek restore testi** — yedeği indir, çöz, boş bir Postgres'e yükle,
  satır say. Yapılmamış bir yedek, yedek değildir.

### E · Frontend'e

**`B-044`-`B-058`'in on beşi de `ACK` beklemiyor, hiçbiri okunmamış görünüyor.**
Frontend oturumuna bunları ACK ettir; sonra `to-frontend.md` 100 satırın altına
arşivlenecek (**Dilim 8**). Dosya bugün süreç kuralının dört katı.

---

## 5. Kapsam dışı — bilerek

- **VPS satın alma ve sunucu üzerindeki işlemler** (§ XI-A.4). Dilim 6 kodu
  hazırlıyor; sunucuya dokunmuyor.
- **Aşama 4 kalemleri** — `tracking` (başvuru izleme) ve `ingestion/github`
  paketleri bugün yalnız `package-info.java` taşıyor. Bu bilinçli.
- **Profil editörü (Adım 3.7)** — frontend'in.
- **`suspicious_output`'un telde görülmesi** — bir enjeksiyon tripwire'ı;
  açılmaması beklenen davranış, eksik değil.

---

## 6. Çalışırken çıkan kayıtlar

### Dilim 2 — yayın sertleştirme

- **Sentry** `io.sentry:sentry-spring-boot-starter-jakarta:8.9.0`, DSN boşken
  no-op. **`send-default-pii: false`** açıkça yazıldı: açık olsaydı istek
  gövdesi, başlıklar ve adres her olaya iliştirilirdi — ilan metni ve CV dahil.
  `traces-sample-rate: 0`, çünkü süre ölçümleri zaten OTLP ile Axiom'a gidiyor
  ve ikinci, farklı örneklenmiş bir kopya onunla çelişirdi.
- **CSP, HSTS ve Referrer-Policy** `SecurityConfig`'te. `X-Frame-Options` ve
  `X-Content-Type-Options` Spring'in varsayılanı — **yeniden yazılmadı**, ama
  `SecurityHeadersIT` ikisini de doğruluyor, yani bir yükseltmede düşerlerse
  test söyler. **HSTS düz http'de gönderilmiyor**; `make dev` etkilenmiyor.
- **Dev bean'leri için ArchUnit kuralı** — `LocalDev*`, `Fake*` ve `..seed..`
  altındaki her `@Component` `@Profile` taşımak zorunda. İsimle liste tutan bir
  test beşinci bean'de sessiz kalırdı. `DevSeeder`'dan `@Profile` sökülerek
  düşürüldü, geri kondu.
- **`latexTest` kendi workflow'unda** (`.github/workflows/latex.yml`), yol
  filtreleriyle: iki gigabaytlık imaj bir doküman değişikliğinde derlenmemeli.
  ⚠️ **Fixture'lar commit'lenene kadar bu lane kırmızı kalacak** — 44/48.
- **Spotless dar kuruldu** ve CI'ın ilk adımı. Beklenen oldu: tüm repoda
  **yalnız beş dosya** değişti, hepsi kullanılmayan import.

### Dilim 3 — saklama

- **`jd_analysis` bilerek duruyor.** İlanın şekli, sözcükleri değil — ve
  `CoverLetterRegenerationService`'in okuduğu şey o. Silmek, pencereden eski
  her üretimde mektup yeniden yazımını bozardı ve kimsenin yazdığı bir şeyi
  kaldırmazdı. **Doğrulandı:** regenerasyon ham ilanı hiç okumuyor.
- **`jd_hash` de duruyor**, ve sebebi ince: `job_description IS NULL` şemada
  "genel CV modu" demek. Digest olmasa, metni unutmak *bir ilan olduğunu* da
  unuturdu.
- **Kuyrukta bekleyen iş dokunulmuyor** — yalnız `completed|failed|cancelled`.
  Yaş testi tek başına, bir haftadır yeniden denenen bir işin girdisini
  işçinin altından çekerdi. Bu koşul sökülerek test düşürüldü, geri kondu.

### Dilim 4 — boş profil ezme (üçte biri)

- **Ekleme — `UserScopedRepository.flush()`.** Boş satırı silip anonim profili
  onun yerine yazmak tek transaction'da çalışmıyordu: `profiles.user_id`
  unique ve **Hibernate insert'leri delete'lerden önce sıralıyor**, çağrı sırası
  ne olursa olsun. Silme ile yazma arasına flush kondu. Testte gerçek bir
  `duplicate key` olarak görüldü, tahmin değil.
- **"Boş" ölçüsü section sayısı.** Bir entry section'a, bir atom entry'ye
  bağlı; section'ı olmayan profilin hiçbir yerinde içerik olamaz. **Contact
  sayılmıyor** — girişin kendisinden doluyor ve her satırı dolu gösterirdi.
- Mevcut `anaccountThatAlreadyHasAProfileKeepsIt` testi **boş** bir satır
  kuruyordu, yani yeni davranışla çelişiyordu; artık section'lı bir profil
  kuruyor ve `KEPT_EXISTING`'i aynı şekilde tutuyor.

### Kayit turu — telde ogrenilenler (2026-08-28)

**Duzeltme — `dev-record.sh` sabit bir `Idempotency-Key` kullaniyordu.** Bolum
30.7 ayni anahtar icin **ayni isi** geri veriyor; local-fake altinda bir kez
basarisiz olan uretim, `make record` ile yapilan koşuşta yeniden calismadan
geri geldi. Ekranda taze bir model reddi gibi okundu; gerçekte Faz A hic
kosmadi, saglayici cagrisi olmadi, fixture yazilmadi. Kanit zaman
damgalariydi: basarisiz is 15:35:44, gercek `profile_extraction` cagrisi
15:36:29 — **is, kayittan once olusmustu.** Anahtar artik kosuş basina.

**Ekleme — ikinci `profile_extraction` atlaniyor.** Fixture varsa import
kosulmuyor: ikinci bir import profile *ekliyor* (karar 4 henuz inmedi) ve
bolumleri ikiye katliyor, ustelik ayni dosyayi uretmek icin ikinci kez para
harciyor.

**Ekleme — ilan dosyadan verilebiliyor** (`dev-record.sh <cv> [ilan.txt]`).
Kapinin esikleri `MIN_CONFIDENCE 0.55` ve `MIN_REQUIRED_SKILLS 2`; script'in
gomulu ilani gercek modelde `confidence 0.9` ama **tek** beceri aldi ve
`too_few_skills` ile reddedildi. Faz A'nin reddi Faz D'yi de kapatiyor, yani
`bullet_rewrite`, `about_synthesis` ve `cover_letter` hic kosmuyor. Kapanis
raporu artik **hangi promptun eksik kaldigini adiyla** soyluyor.

### ACIK — fiyat tablosu kullanilan modeli kapsamiyor

`.env` `OPENROUTER_MODEL=openai/gpt-4.1-nano` diyor; `application.yml`'in
`atomcv.llm.pricing` tablosunda yalniz `google/gemma-4-26b-a4b-it:free` var.
Telde olculdu: gercek bir `job_analysis` cagrisi **783 girdi + 201 cikti
token** harcadi ve `llm_invocations.cost_usd` **0.000000** yazildi.

**Bu, gunluk butce freninin (§ 44.3) hic calisamayacagi anlamina gelir** —
toplam her zaman sifir. `F-015`'in kapattigi sey tam olarak buydu ve
`LlmPricingAudit` acilista uyariyor olmali. **Geliştiriciye dusuyor:**
OpenRouter'in fiyat sayfasindan `openai/gpt-4.1-nano` icin input/output
rakamlarini al ve tabloya yaz — ya da modeli fiyati bilinen ucretsiz olana
dondur. **Rakam tahmin edilmedi**: yanlis bir fiyat, fiyatsizliktan kotudur.

### Kayit turunun iki ince tuzagi (2026-08-28, telde)

**Duzeltme — `$(cat dosya)` sondaki satir sonunu yutuyor.** Fixture anahtari
istegin *tam baytlarinin* ozeti; sonunda satir sonu olan bir ilan dosyasi bir
anahtarla kaydedilip baska bir anahtarla aranirdi ve **sessizce** iskalanip
sentetik cevaba duserdi. Olculdu: korumali okuma 490 bayt, korumasiz 489.
Script artik `$(cat f; printf 'x')` + `${P%x}` kullaniyor.

**Ekleme — `JobSpecificCvIT` ilanini dosyadan okuyor**
(`src/integrationTest/resources/postings/senior-backend-go.txt`). Metin
sabitteyken kaydedilen ile test edilen iki ayri kopyaydi ve ayrisma **sessiz**
bir fixture iskasi olarak gorunurdu. `.gitattributes` LF'e normalize ettigi
icin ozet runner'da da ayni.

**Acik — reddedilen bir cevap da kaydediliyor.** Kayitci `ProviderChain`'in
basari dalinda ve kapilardan haberi yok; makullük kapisinin reddettigi bir
analiz de fixture'a yaziliyor ve sonra **her replay'de ayni reddi** uretiyor.
§ 18.4 cache icin "reddi cache'lemek onu bir hafta dondurur" diyor; ayni akil
yuzde yuz kayit icin de gecerli. Bugun elle silinerek yonetiliyor.

**Ders — kaydedilen fixture hemen commit'lenmeli.** Untracked oldugu surece
tek bir `rm -rf` geri donusu olmayan bir kayip: bu oturumda `job_analysis`
dizininde iki dosya vardi (biri kapinin reddettigi, biri gecerli), dizin
butunuyle silindi ve gecerli olan da gitti. `cat dir/*.json | head` yalniz
ilkini gosterir — **silmeden once dosyalari say.**

### Adim 3.2 telde kapandi (2026-08-28) — ve bir spec hatasi cikti

**Duzeltme — kilavuzun DNS tablosu yanlisti**, § 3.2'ye islendi. Resend MX ve
SPF'i `mail.atomcv`'ye degil **`send.` onekli alt alana** yaziyor, ve bolge
hesabin bolgesi (`ap-northeast-1`, `eu-west-1` degil). Ikisi ayri alan ve bu
dogru: `mail.atomcv...` gonderen alan (From + DKIM), `send.mail.atomcv...`
Return-Path (SPF + bounce MX). **`EMAIL_FROM` gonderen alanda olmak zorunda**
— kodda dogrulamasi yok, yanlis yazilirsa sessizce spam'e duser.

**Uygulamada degisiklik gerekmedi.** Telde dogrulandi: DKIM birebir, SPF gevsek
hizalamayla hizali.

**`_dmarc.mail.atomcv` eklendi** (`p=none; rua=...; fo=1`), apex'teki
`_dmarc.mustafatetik.com` yerine degil, **onunle birlikte**. Apex kaydi
organizasyon alani geri dususuyle alt alani zaten kapsiyordu; alt alan kaydi
`p=reject`'e cikarken portfolyo postasini kapsam disinda birakiyor.

**Karar — Resend bolgesi Tokyo kaliyor.** Sonucu hukuki: e-posta adresi ve
gonderim ustverisi AB disinda isleniyor, ve Gizlilik Politikasi'nin alt isleyen
listesi bunu soylemeli. Frontend aksiyonu **`B-059`**; EK C.1 bunu yayin
oncesi istiyor.

### Kayitci artik reddedilen cevabi geri aliyor

**Ekleme — `AnswerRecorder.discard`.** Zincir kayit ederken kapilardan haberi
yok, ve makulluk kapisinin reddettigi bir analiz fixture'a yaziliyordu: § 18.4
cache icin "reddi cache'lemek onu bir hafta dondurur" diyor, **fixture'a
yazmak sonsuza dondurur** — her klonda ayni reddi ureten, bozuk bir pipeline
gibi okunan bir dosya.

**Kapsam bilincli: yalnizca Faz A'nin kapisi cagiriyor.** Altindaki
dogrulayicilar (Faz D, About, cover letter) reddettiklerinde **orijinali
koruyorlar**, yani reddettikleri bir cevabi replay etmek fixture hic
olmamasiyla ayni sonucu veriyor. Geri alinacak bir sey yok. Faz A'nin reddi
ise isi bitiriyor — fark bu.

`ProviderChainTest`'in sahte kayitcisi `discard` cagrilirsa **atiyor**: zincir
bir cevabi yargilamaz, ve bir gun yargilamaya kalkarsa test soyler. Kapinin
cagrisi sokulerek dusuruldu, geri kondu.

### Karar 8 — `llm_invocations.user_id` dolduruldu

**`StructuredRequest` bir `userId` tasiyor** ve `forUser(...)` ile ekleniyor;
olay onu `LlmInvocationEvent`'e, kaydeden de kolona yaziyor. Alti cagri
yerinin **altisi da** atifli: Faz A, bullet rewrite, about sentezi, cover
letter (uretim + yeniden uretim), cikarim, ceviri.

**`bucketKey` ile birlestirilmedi, ve sebebi somut:** ikisi uretimde ayni deger
ama **cikarimda degil** — `ProfileExtractionJobHandler.bucketKeyFor(userId,
anonSession)` anonim yuklemede oturum kimligini buketliyor. Birlestirmek, "hangi
hesap harcadi" diyen bir kolona oturum kimligi yazardi. Anonim cagri **NULL**
birakiyor; uydurulmus bir sahip bostan kotudur.

**Ders — mock'lanan bir metoda kolaylik overload'i eklemek sessiz bir tuzak.**
Once `analyse`, `structure`, `write`, `retranslate` icin kisa imzalari
birakmistim; testler kisa imzayi stub'liyor, uretim uzun imzayi cagiriyor ve
mock **null donuyordu**. Alti test bunu yakaladi. Cozum: **mock'lanan
metotlarda overload yok** — tek imza, atif zorunlu, umursamayan cagiran `null`
yaziyor. Deger nesnelerinde (`StructuredRequest`, `RewriteContext`) kolaylik
kurucusu kaldi; orada mock yok.

Uctan uca test: `LlmInvocationAttributionIT` olaydan kolona kadar bakiyor —
olayin tasidigi ama `INSERT`'e baglanmadigi durum, aksi halde sessizce urune
ulasacak olan hataydi. Baglama sokulerek dusuruldu, geri kondu.

### Karar 12 — § 44.3'un sikilastirmasi indi, ama **onerdigimden farkli**

**Spec'i okuyunca onerim yanlisti.** § 44.3'un kod parcacigi blanket bir saatlik
tavan degil, **hedefli** bir sikilastirma istiyor:
`rateLimiter.tighten(u.userId(), Duration.ofHours(6))` — ve bolumun kendi notu
"sikilastirilacak bir rate limiter yok" diyerek eksigi zaten kaydetmis. Yazilan
o: anomali dedektoru artik alarmi hakkinda **bir sey yapiyor**, ama frene
dokunmuyor — fren herkesi durdurur ve insanin karari olarak kaliyor.

`TightenedSubjects` isareti **Redis'te**, TTL kaydin kendisi: tabloda dursa
sebebi gecmis birini kilitli tutan bayat satiri temizlemek icin bir supurucu
gerekirdi. **Ulasilamayan Redis = sikilastirma yok**; bir cache arizasi herkesi
agir kullanici ilan edemez.

**Sikistirmanin sayisi spec'te yok**, varsayilan **saatte 2 / 6 saat** kondu ve
yapilandirma — her limit gibi surumsuz degisebilsin diye.

**Kontrol kotanin onunde**: reddedilen istek gunluk hakki harcamamali, § 44.3
freni kotanin onune ayni sebeple koyuyor.

**Sapma — `RateLimiter` `identity`'den `shared`'a tasindi.** `billing`'in ona
uzanmasi `billing -> identity -> billing` dongusu yaratti ve ArchUnit reddetti.
Kural hakliydi: limiter'in oturumdan haberi yok, sozlugu (katman, konu, sinir,
pencere) tamamen genel. `identity.ratelimit`'te kalan sey gercekten girise ait
olan: § 40.5'in uc katmani ve sayilari.

**Ders — `@SpringBootTest` alt sinifta `properties` bildirince tabanınkiler
tamamen duser.** `TighteningIT` ve `RetentionSweeperIT` boylece **is iscisini
geri acti**; zamanlayici paylasilan veritabanindan is talep etti ve
`JobQueueIT` alakasiz bir yerde dustu ("GENERATION bekleniyordu, EMBEDDING
geldi"). Ucu de elle yeniden bildirildi. CLAUDE.md'nin "isci her testte kapali"
kurali bu yuzden var, ve kurali delmenin yolu gorunmez.

### Karar 4 — ikinci CV kapida reddediliyor

**Onerimi spec duzeltti.** `mode=append` yerine **`mode=replace`**: § 08b'nin
409 satiri "yalniz **degistir veya koru** sunar, **birlestir sunmaz**" diyor ve
gerekcesi de yazili — birlestirme atom duzeyinde tekillestirme (Bolum 7) ve
Asama 4 isi; erken sunmak icerigi sessizce cogaltir, P8 onu yasakliyor. Benim
`append` onerim tam olarak o yasakli seydi.

Ret **senkron**, kapida: 202 verip sonra isi dusurmek daha kotu bir cevap ve
dosya hala cagiranin elinde. Bolum 31.6.2'nin **altinci** reti.

**"Profil var mi" degil "icinde bir sey var mi"** — ve bu kural artik **tek
yerde**: `ProfileResolver.hasContent`. Yukseltme servisi kendi kopyasini
tasiyordu, o silindi. Olcu section sayisi; contact sayilmiyor, girisin
kendisinden doluyor.

**Anonim yol etkilenmiyor**: efemeral yazim butun belgeyi birden yaziyor
(§ 31.6.3), ikinci yukleme cogaltmiyor.

**Uc muhafiz yakaladi ve ucu de haklıydı:**
1. `ErrorCatalogueTest` sozlugu on ile kilitliyordu — "bir handoff maddesi
   olmadan eklenen deger kullaniciya ham anahtar olarak duser" diyor. **`B-060`**
   yazildi.
2. `OpenApiSchemaIT` yayinlanan semada enum'u tutuyor.
3. `ProfileImportApiIT` **davranis degisikligini** yakaladi: `DevSeeder`
   tohumlu profil verdigi icin o kullanicinin her yuklemesi tanim geregi
   ikinci yukleme. Testler `?mode=replace` ile aciklandi.

**Ve kendi tuzagima dustum:** `SecondImportIT`'in ilk hali bos profil uretmek
icin **tohumlanmis golden profilin section'larini siliyordu** — paylasilan
veritabaninda, `DevSeederIT`'in baktigi satirlar. Suite yesil gecti cunku sira
tuttu; ilk yeniden siralanmada duserdi. Bos hal artik yordamin kendisi
uzerinden, sinifin kendi kullanicisiyla dogrulanıyor ve paylasilan durum hic
degismiyor.

### Determinizm bozucusu icerikten turuyor artik

**Sorun gercekti ve golden sette gorulebiliyordu.** Tie-break atom id'siydi;
id'ler her ice aktarimda yeniden uretiliyor, yani ayni skor *ve* ayni maliyetteki
iki atom okumalar arasinda yer degistiriyordu — **ayni CV iki kez yuklendiginde
iki farkli sayfa.**

`AtomVariant.contentHash` zaten vardi ve tam olarak bu icerigin ozeti; yeni bir
sey hesaplanmadi. `AtomCandidate.contentKey` onu tasiyor, `tieBreak()` once onu
kullaniyor, id son care olarak kaliyor. **Ozet, metin degil**: bu kayit loglara
ve `toString()`'e girebiliyor ve mutlak kural 4'un deger nesnesi istisnasi yok.

**Mevcut test defektin etrafinda yazilmisti.** `rereadingTheFixture...` yalnizca
"sayfa ayni doluluktа ve ayni sayida atom" diyordu, ve javadoc'u sebebini
acikca kaydediyordu. Artik **ayni sozcuklemeler, ayni sirada** diyor. Eski
tie-break geri konarak test dusuruldu: **gercekten dusuyor.**

### Atomsuz entry indi (§ 20.2)

Dort dokunma noktasinin dordu de yazildi, ve olculen sey degismedi: renderer
atomsuz entry'yi zaten basiyordu, maliyet zaten olculmustu.

- **`AtomCandidate.headerOnly`** — `forEntryHeader(...)` ile uretiliyor,
  `atomId` entry'nin kendi id'si, `renderCostPt` sifir. `EntryPlan` artik bir
  kural daha dogruluyor: **baslik-adayi entry'de yalnizdir.** Yanina bir gercek
  atom girseydi entry baslik-adayiyla acilir, sonraki atom `ITEMIZE_OVERHEAD`
  odemez ve renderer odenmemis bir liste basardi — sayfanin sessizce tastigi
  tek yol buydu.
- **Skor** entry'den geliyor: genel modda guncellik ile onem, § 19.4'un
  ikisi arasindaki oranini koruyarak yeniden normalize edilerek. Bunu yapmasak
  bir diploma satiri 0.65'te tavan yapip kazanmasi gereken her karsilastirmayi
  kaybederdi. Ilana ozel modda entry'nin `importance` degeri — Faz B wording
  skorlar, bu entry'nin wording'i yok.
- **Kisit (4) uygulanmiyor** — minimum madde hakkinda bir cumle. Fixture'daki
  entry'nin `minAtoms`'u kasten varsayilanda (2) birakildi, boylece bu atlama
  gercekten sinaniyor.
- **Sigmayan baslik `rejected`'a girmiyor**, cunku o liste kullaniciya atom
  atom gosteriliyor.
- **`selection_state`'e `headerOnlyEntries` eklendi**, eski satirlarda yok ve
  bos liste okunuyor.

**Yol ustunde bulunan bir determinizm acigi:** `openEntries` bir `HashSet`'ti ve
`upgradeFirstEntryOf` onu gezip **ilk ulastigi** entry'yi ucretlendiriyor. JVM
basina tuzlanan sira, bir kaldirmanin ne kadar iade ettigini degistiriyordu.
`LinkedHashSet` oldu.

**Fixture'da atomsuz entry yoktu**, yani yeni yol hicbir sey tarafindan
sinanmayacakti: `junior_frontend_en`'e maddesiz bir A Levels satiri eklendi.
Varyant eklemedigi icin `*.costs.json` etkilenmedi.

**Yedi muhafizin yedisi de dusurulup dustugu goruldu** — aday uretimi, render
filtresi, `ITEMIZE_OVERHEAD` muafiyeti, minimum atlamasi, ret atlamasi,
`EntryPlan` kurali ve JSONB null varsayilani. Sonuncusundan once: derleyicili
lane'de de dusuruldu, `GeneralCvIT` gercek PDF'te satiri bulamadi.

### ATS geri okumasi indi (§ 23.2)

`AtsCheck` uretilen PDF'i PDFBox ile geri okuyup dort seyi soruyor: her baslik
metin katmaninda mi, her basilan madde orada mi, iletisim satiri sag kaldi mi,
basliklar hala ayni sirada mi.

**Neden onemli:** bu repodaki *baska her sey* CV'yi PDF olmadan once olcuyor —
butce, uygunluk raporu, dogrulayicilar. Guzel dizilen ve metin katmani olmayan
bir sablon suitteki her testi gecer ve bir ATS'ye **bos sayfa** olarak ulasir.

**Raporluyor, reddetmiyor.** Sayfa zaten odenmis ve zaten siniri tutuyor;
buradaki her bulgu **bizim** sablonumuzun ya da fontumuzun kusuru, kullanicinin
CV'sinin degil. Belgeyi elinden almak, kendi hatamiza onun kaybiyla cevap
vermek olurdu. Sayaclar operatorun: `generation.ats.clean` /
`generation.ats.defect`.

**Kendi PDFBox cagrisi**, `PdfTextExtractor` degil: o bir ingestion bileseni ve
sozlesmesi kullaniciya donuk bir ret (`PDF_ENCRYPTED` gibi) — kendi urettigimiz
dosya icin yanlis sozlesme.

**Karsilastirma normalize:** cikarim satirlari yeniden akitiyor, iki satira
bolunmus bir madde cumlenin ortasinda satir sonuyla geri geliyor. Bosluk
sikistiriliyor ve **`Locale.ROOT`** ile kucultuluyor — mutlak kural 7 tam
burada isliyor, Turkce varsayilan "SQL"i "sqı" yapip basilan bir maddeyi
"eksik" diye raporlardi.

**Kendi testim bir kusurumu yakaladi:** ilk `inOrder` ileri-arama yapiyordu,
yani *daha erken* cikan bir baslik "yok" gibi okunuyordu — sirasi bozuk sayfayi
kacirirdi. Her basligin kendi ilk konumu alinip artislari kontrol ediliyor.

**Gercek derleyiciye karsi olan hali `latexTest`'te**, ve orada **pipeline'in
kendi sayacina** bakiliyor: testte render istegini yeniden kurmak, basilanin ne
oldugu hakkinda ikinci bir gorus olurdu; onemli olan pipeline'in kendi gorusu.

### Dilim 6 — deploy altyapisi indi, ve iki sey denetimde gozumden kacmisti

**`Dockerfile` hic yoktu.** Kilavuzun deploy workflow'u `docker build ... .`
diyor ve kokte derlenecek bir sey yoktu — **butun dagitim yolu yazilmisti ve
dagitilacak bir artefakti yoktu.** Cok asamali: JDK'da derleniyor, **JRE'de
calisiyor**, root degil (`atomcv`, uid 1000), kendi HEALTHCHECK'i var.
`.dockerignore` iki seyi disarida tutuyor ve ikisi de kasitli: **`.env`**
(imaj katmanina girse `docker history` geri okur) ve **`build/`** (host'un
jar'i konteyerinkini golgelerdi).

**Ve imaji calistirinca gercek bir hata cikti — kod JDK'da calisiyor, dagitilan
imajda calismiyordu:**

```
No implementation of the random number generator algorithm
"L32X64MixRandom" is available
```

`RandomGenerator.getDefault()` **`jdk.random`** modulunu istiyor; o modul
`java.se`'nin parcasi degil, yani **her JRE imajinda yok**. `JobWorker` insa
edilemiyor, uygulama acilmiyor. **Hicbir sey bunu soyleyemezdi**: testler
JDK'da kosuyor ve hata yapicida cikiyor, yani once bir imajin var olmasi ve
calistirilmasi gerekiyordu. `ThreadLocalRandom` ile degistirildi —
`java.base`'de, kaybolamaz; yaptigi is zaten backoff jitter'i (§ 30.5).

**Prod imaji telde dogrulandi** (gercek Postgres + Redis, `prod` profili):
`/actuator/health` **200 UP** · `/api/v1/profile` **401** (dev kullanicisi yok
— EK C.1'in maddesi artik ArchUnit kuraliyla *ve* canli kanitla tutuluyor) ·
`/v3/api-docs` **404** · CSP, X-Frame-Options, X-Content-Type-Options,
Referrer-Policy hepsi telde · `DevSeeder` calismadi · Docker HEALTHCHECK
`healthy`.

**Inen dosyalar:** `Dockerfile`, `.dockerignore`, `docker-compose.prod.yml`,
`docker/nginx/{nginx.conf,proxy_params.conf}`, `scripts/{deploy.sh,backup.sh,
restore.sh}`, `.github/workflows/deploy.yml`.

**Uc tasarim karari:**

1. **`deploy.sh` yalniz kendi bilesenini geri aliyor.** Oteki bilesen baskasinin
   deploy'u ve dakikalar once inmis olabilir; onu da geri almak bir arizayi
   ikiye cikarirdi. Gerie alinacak onceki etiket yoksa bunu **soyluyor** ve
   loglara yonlendiriyor.
2. **`restore.sh` varsayilan olarak *scratch* veritabanina donuyor**, ve asil
   nokta bu: yalnizca uretimi ezebilen bir restore script'i, kimsenin mecbur
   kalana kadar calistirmadigi script'tir — yani calismadigini ogrendigin gun.
   Bu sıradan bir sali gunu gercek yedeklere karsi kosulabiliyor ve **satir
   sayilarini basiyor**: "hata vermedi" bir restore testi degildir.
   `--into-production` var ve adi yazdirarak onay istiyor.
3. **Trivy imaj taramasi `deploy.yml`'de**, ve imaj **registry'ye itilmeden
   once**: taramayi gecemeyen bir imaj hic var olmamali. `ci.yml`'in taramasi
   repoyu okuyor (Dockerfile'lar); bu, Dockerfile'in *urettigini* okuyor — CVE
   orada yasiyor.

**Ikinci gozden kacan:** `deploy.yml`'de `StrictHostKeyChecking=no` yerine
`SSH_HOST_KEY` secret'i pinleniyor; yoksa `ssh-keyscan`'e dusuyor ama
**workflow uyarisi basiyor** — sessizce guvenmiyor.

### Dilim 7 — zincirin ikinci halkasi ve suppression'in yazicisi

**Gemini adaptoru.** Zincir mekanizmasi kuruluydu, testi vardi, ve **tek
halkasi** vardi: bir OpenRouter kesintisi urunu durduruyordu. Ikinci **saglayici**
(ayni saglayicida ikinci model degil) — OpenRouter zaten bir broker, arkasindaki
her sey brokerin kesintisini paylasir.

**Ve bir tuzak vardi:** Gemini `responseSchema` icin **JSON Schema degil, OpenAPI
3.0 alt kumesi** aliyor, ve alt kume disindaki bir anahtar 400 ile reddediliyor —
yok sayilmiyor. `resources/prompts` altindaki **alti semanin altisi da**
`additionalProperties` tasiyor, yani sema oldugu gibi gonderilseydi **her cagri**
400 alacakti ve zincir bunu "Gemini kapali" diye okuyacakti. `GeminiSchema` ucunu
dusuruyor (`additionalProperties`, `minimum`, `maximum`); dusen kisitlarin hicbiri
yalniz orada zorlanmiyor — cevap zaten bir record'a parse ediliyor ve `confidence`
sinirini `PlausibilityGate` kendisi kontrol ediyor.

Anahtar **header'da**, Google'in orneklerinin `?key=` query string'inde degil:
query string proxy'lerin ve erisim loglarinin yazdigi yerdir.

**Resend webhook'u — `EmailSuppressions` artik yaziliyor.** Kendi javadoc'u
"satirlar webhook'lardan gelecek, bu dilimde degil" diyordu. Sert bounce ve
sikayet artik adresi kapatiyor; **gecici bounce kapatmiyor** — dolu bir posta
kutusu yuzunden birini kendi hesabindan kilitlemek olurdu.

**Imza her seydir.** Uc a nokta:
- Endpoint zorunlu olarak kimliksiz (webhook'un oturumu yok), yani **inanilan
  her sey bir adresi kapatabilir** ve kapatilan adres giris yapamaz. Sahte bir
  `email.bounced` = bizim elimizle teslim edilen bir DoS.
- **Iki kontrol, bir degil:** imza govdenin degismedigini soyler, zaman damgasi
  bunun kaydedilip sonra tekrar oynatilan bir teslimat olmadigini. Ikincisi
  olmadan yakalanmis bir istek sonsuza kadar gecerli kalir.
- **Secret yoksa her teslimat reddediliyor.** Alternatifi — yapilandirilmamisken
  imzasizi gecirmek — secret'i unutan bir dagitimin herkesin postladigini kabul
  etmesi demekti. Reddedilen webhook Resend'in panelinde **gorunur** bir sorun;
  otekisi sessiz.

**CSRF muafiyeti** `/api/v1/webhooks/**` icin, ve dar: CSRF cereze binen bir
istegi savunur, bunun cerezi yok. Yerini imza aliyor — daha guclu, cunku
*gondericiyi* dogruluyor, cerezi okuyabildigini degil.

**Ve testim yalan soyluyordu.** Ilk hali `AbstractIntegrationTest`'in MockMvc'sini
kullaniyordu; o her istege CSRF tokenı veriyor, yani **muafiyet sokulunce test
yine geciyordu**. `CsrfRejectionIT`'in yaptigi gibi kendi MockMvc'sini kuruyor
artik — sokulunce dort test dusuyor. CLAUDE.md bu tuzagi zaten yaziyordu ve ben
yine dustum.

### Dilim 8 — dokumanlar

**Spec'e islenen dort duzeltme.** § 47 (migration acilista kaliyor ve
`--spring.flyway.migrate-only` diye bir property yok; guvenli kilan sey **tek
ornek**, kilit degil — ve `deploy.sh` imaji geri alir, **migration'i geri
almaz**, yani geriye donuk uyumlu sema zorunlu). § 57.4 (R2 listede var, kodda
yok; **`AccountDeletionIT` sema okuyor, veritabaninda olmayan bir nesne
deposunu goremez** — bunu hatirlatacak tek sey o paragraf). § 3.2 (Resend'in
`send.` oneki ve bolge). Ve **§ 51.7**.

**§ 51.7 bir tasima.** Testin kendisi hakkindaki dort kural `CLAUDE.md`'de
yasiyordu ve oraya "hicbir spec dosyasi zorlamiyor" diye yazilmisti. Zorlayan
hala yok, ama yerleri § 51 — test stratejisi orasi. CLAUDE.md 297 satira
cikmisti (sinir 280) ve **gercek icerik kesmeye baslamistim**; dogru hamle
kesmek degil, projenin kendi promosyon kurali oldu. 270'e indi.

**`CLAUDE.md`'de bir madde yanlisti ve duzeltildi:** "Makefile `.env`'i include
ve export ediyor, **compose okur, Spring okumaz**" diyordu. `export` yuzunden
Spring de okuyor — bu oturumun iki yerel tuzagi tam olarak oydu. Madde artik
dogruyu soyluyor ve yeni bir uretim secret'inin `LOCAL_*` ile karsilanmasi
gerektigini de.

**PR check sayisi degisti:** bes degil alti (format eklendi), arti kosula bagli
**LaTeX** ve yalniz `main`'de kosan **Deploy**.

**`to-frontend.md` arsivlenemedi, ve bu dogru.** 463 satir, sinir 100 — ama
**on yedi maddenin hicbiri `ACK` almadi**, yani tasinacak bir sey yok. Acik bir
maddeyi arsivlemek onu kaybetmek olurdu. Yapilan: basa **dizin** kondu (her
madde bir satir) ve dosyanin kendisi bunun bir belge sorunu degil **koordinasyon
sorunu** oldugunu soyluyor. Ayni cumle `CLAUDE.md`'nin kanal kuralina da
islendi, ki bir dahaki sefere "arsivleme gecikmis" diye okunmasin.
